package echo.music.iad1tya.stems

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Offline mix-down renderer: decodes each stem WAV and feeds it through
 * FFmpeg's real audio filters (volume, pan, equalizer for per-stem + master,
 * amix) before writing the final WAV/MP3 into Music/RajTune Stems.
 *
 * This guarantees the exported file hears exactly what the live mixer does:
 * per-stem gain/pan/EQ, master EQ (via per-stream filter equivalence), trim,
 * and additive mixing.
 */
@Singleton
internal class StemExportRenderer @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  data class ExportResult(val uri: Uri, val reason: String = "")
  data class AppliedTrim(val trimStartSec: Float, val trimEndSec: Float?)

  suspend fun exportMix(
    outputs: List<SeparatedStem>,
    sourceTitle: String,
    settings: Map<StemId, StemMixSettings>,
    master: MasterMixSettings,
    format: StemExportFormat,
    quality: StemExportQuality,
    onProgress: (String, Int) -> Unit,
  ): List<ExportResult> = withContext(Dispatchers.IO) {
    require(outputs.isNotEmpty()) { "No stems to export" }
    onProgress("Preparing export", 2)

    val someSoloed = settings.values.any { it.soloed }

    val tempFiles = mutableListOf<File>()
    var wavUri: Uri? = null
    try {
      val inputs = outputs.map { output ->
        val temp = File(context.cacheDir, "stem-export-${UUID.randomUUID()}.wav").also(tempFiles::add)
        copyUriToFile(output.uri, temp)
        temp
      }
      if (inputs.isEmpty()) error("No stems found for export")

      val exportTmp = File(context.cacheDir, "mix-${UUID.randomUUID()}.wav").also(tempFiles::add)
      onProgress("Rendering mix", 20)

      val filterGraph = buildFilterGraph(
        stems = outputs.map { it.name },
        settings = settings,
        master = master,
        someSoloed = someSoloed,
      )
      val args = mutableListOf<String>()
      for (input in inputs) {
        args += listOf("-i", input.absolutePath)
      }
      args += listOf("-filter_complex", filterGraph)
      args += listOf("-map", "[mixed]")
      if (format == StemExportFormat.WAV) {
        args += listOf("-c:a", "pcm_s16le", "-ar", "44100", "-ac", "2")
      } else {
        val bitrate = when (quality) {
          StemExportQuality.WAV_16BIT -> "192k"
          StemExportQuality.MP3_320 -> "320k"
          StemExportQuality.MP3_192 -> "192k"
          StemExportQuality.MP3_128 -> "128k"
        }
        args += listOf("-c:a", "libmp3lame", "-b:a", bitrate, "-ar", "44100", "-ac", "2")
      }
      // Speed/pitch are embedded in the filter graph via atempo/asetrate.
      args += listOf("-y", exportTmp.absolutePath)

      runFfmpeg(args, "Mixdown failed", "ffmpeg export")
      onProgress("Saving export", 80)

      val useMp3 = format == StemExportFormat.MP3
      val finalName = exportDisplayName(sourceTitle, useMp3)
      wavUri = saveToMediaStore(finalName, exportTmp, if (useMp3) "audio/mpeg" else "audio/wav")
      onProgress("Export saved", 100)
      listOf(ExportResult(wavUri, "export"))
    } finally {
      tempFiles.forEach(File::delete)
    }
  }

  private fun buildFilterGraph(
    stems: List<String>,
    settings: Map<StemId, StemMixSettings>,
    master: MasterMixSettings,
    someSoloed: Boolean,
  ): String {
    val filters = stems.mapIndexed { index, idString ->
      val id = StemId.from(idString)
      val settingsForStem = settings[id] ?: StemMixSettings(id)
      buildStemFilterChain(index, settingsForStem, master, someSoloed)
    }
    // Amix all stems. FFmpeg 4.4+ requires normalize=0 for a true sum.
    val amix = "[${filters.joinToString("][")}]amix=inputs=${filters.size}:normalize=0"
    val masterEq = buildMasterEqFilter(master)
    val atempo = if (master.speed != 1f) ",atempo=${master.speed}" else ""
    val asetrate = if (master.pitch != 1f) ",asetrate=${master.pitch * master.speed}" else ""
    val trim = ",atrim=start=${master.trimStartSec}:end=${(master.trimEndSec ?: 2147483.647f)}"
    val resample = ",aresample=44100:async=1"
    return "$amix,$masterEq$atempo$asetrate$trim$resample[mixed]"
  }

  private fun buildStemFilterChain(
    index: Int,
    settings: StemMixSettings,
    master: MasterMixSettings,
    someSoloed: Boolean,
  ): String {
    val audible = settings.effectiveAudible(someSoloed)
    val gain = if (audible) settings.linearGain() * master.linearGain() else 0f
    val (leftGain, rightGain) = panToConstantPowerGains(settings.pan)
    val volume = gain.toDouble()
    val filters = mutableListOf<String>()

    // Per-stem trimming in ffmpeg is atrim: applied locally if trim defined for the stem.
    val trim = if (settings.trimStartSec != null || settings.trimEndSec != null) {
      val s = settings.trimStartSec ?: 0f
      val e = settings.trimEndSec ?: 2147483.647f
      "atrim=start=$s:end=$e,asetpts=PTS-STARTPTS,"
    } else ""

    val eqFilters = settings.eqBands
      .filter { it.enabled && it.gainDb != 0.0 }
      .joinToString(",") { band ->
        val widthType = when (band.type) {
          echo.music.dsp.core.models.FilterType.LSC -> "w"
          echo.music.dsp.core.models.FilterType.HSC -> "w"
          else -> "o"
        }
        val width = when (band.type) {
          echo.music.dsp.core.models.FilterType.LSC -> "100"
          echo.music.dsp.core.models.FilterType.HSC -> "10000"
          else -> "%.4f".format(qToBandwidth(band.q))
        }
        "equalizer=f=${band.frequency}:width_type=$widthType:width=$width:g=${band.gainDb}"
      }

    filters.add("volume=${"%.6f".format(volume)}")
    // Constant-power pan: left/right channels multiplied by the computed gains.
    filters.add("pan=stereo|c0=${"%.6f".format(leftGain)}c0|c1=${"%.6f".format(rightGain)}c1")
    if (eqFilters.isNotEmpty()) filters.add(eqFilters)
    return "[$index:a]$trim${filters.joinToString(",")}[s$index]"
  }

  private fun buildMasterEqFilter(master: MasterMixSettings): String {
    val eqFilters = master.eqBands
      .filter { it.enabled && it.gainDb != 0.0 }
      .joinToString(",") { band ->
        val width = "%.4f".format(qToBandwidth(band.q))
        "equalizer=f=${band.frequency}:width_type=o:width=$width:g=${band.gainDb}"
      }
    return if (eqFilters.isEmpty()) "anull" else eqFilters
  }

  private fun copyUriToFile(uri: Uri, dest: File) {
    context.contentResolver.openInputStream(uri)?.use { input ->
      FileOutputStream(dest).use { output ->
        val buf = ByteArray(64 * 1024)
        while (true) {
          val n = input.read(buf)
          if (n < 0) break
          output.write(buf, 0, n)
        }
      }
    } ?: error("Could not read stem file")
    require(dest.length() > 0L) { "Stem file is empty" }
  }

  private suspend fun runFfmpeg(args: List<String>, errorPrefix: String, label: String) {
    suspendCancellableCoroutine<Unit> { continuation ->
      val session = FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { completed ->
        if (!continuation.isActive) return@executeWithArgumentsAsync
        if (ReturnCode.isSuccess(completed.returnCode)) {
          continuation.resume(Unit)
        } else {
          continuation.resumeWithException(IllegalStateException("$errorPrefix: ${completed.output.take(800)}"))
        }
      }
      continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }
  }

  private fun exportDisplayName(sourceTitle: String, mp3: Boolean): String {
    val base = sourceTitle.substringBeforeLast('.', sourceTitle).ifBlank { "Mix" }
      .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(90)
    return "$base - RajTune mix.${if (mp3) "mp3" else "wav"}"
  }

  private fun saveToMediaStore(displayName: String, source: File, mime: String): Uri {
    val values = ContentValues().apply {
      put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
      put(MediaStore.Audio.Media.TITLE, displayName.substringBeforeLast('.'))
      put(MediaStore.Audio.Media.ARTIST, "RajTune Stem Mixer")
      put(MediaStore.Audio.Media.MIME_TYPE, mime)
      put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/RajTune Stems")
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Audio.Media.IS_PENDING, 1)
    }
    val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
      ?: error("Could not create mix file")
    try {
      context.contentResolver.openOutputStream(uri, "w")?.use { output ->
        source.inputStream().use { input ->
          val buf = ByteArray(64 * 1024)
          while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
          }
        }
      } ?: error("Could not write mix file")
      val clearPending = ContentValues().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Audio.Media.IS_PENDING, 0)
      }
      check(context.contentResolver.update(uri, clearPending, null, null) > 0) { "Could not publish mix file" }
      return uri
    } catch (e: Throwable) {
      context.contentResolver.delete(uri, null, null)
      throw e
    }
  }
}
