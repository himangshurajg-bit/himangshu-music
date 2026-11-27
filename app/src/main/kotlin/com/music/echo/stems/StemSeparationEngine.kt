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
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

internal data class StemJobProgress(val label: String, val percent: Int)
internal data class SeparatedStem(val name: String, val uri: Uri)

@Singleton
internal class StemSeparationEngine @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  suspend fun separate(
    sourceUri: Uri,
    sourceTitle: String,
    onProgress: (StemJobProgress) -> Unit,
  ): List<SeparatedStem> = withContext(Dispatchers.IO) {
    val input = File(context.cacheDir, "stem-source-${UUID.randomUUID()}")
    val pcm = File(context.cacheDir, "stem-pcm-${UUID.randomUUID()}.f32le")
    val outputs = linkedMapOf<String, StemOutputWriter>()
    try {
      onProgress(StemJobProgress("Reading audio", 2))
      context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
        FileOutputStream(input).use { output ->
          val buffer = ByteArray(64 * 1024)
          while (true) {
            currentCoroutineContext().ensureActive()
            val count = inputStream.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
          }
        }
      } ?: error("RajTune could not open that audio file")
      check(input.length() > 0) { "The selected audio file is empty" }
      onProgress(StemJobProgress("Decoding to 44.1 kHz stereo", 8))
      decodeToPcm(input, pcm)
      val sampleCount = pcm.length() / 8L
      check(sampleCount > 0) { "No audio samples could be decoded" }
      currentCoroutineContext().ensureActive()

      onProgress(StemJobProgress("Preparing four offline models", 12))
      val models = StemModelRepository(context).ensureModels { progress ->
        onProgress(StemJobProgress("Downloading models (${progress}%)", 12 + progress * 8 / 100))
      }
      val separator = MdxNetSeparator()
      val stemOrder = listOf("bass", "drums", "other", "vocals")
      separator.separate(
        pcmFile = pcm,
        sampleCount = sampleCount,
        modelFiles = models,
        writer = { stem, _, left, right, count ->
          val output = outputs.getOrPut(stem) {
            StemOutputWriter(context, sourceTitle, stem, sampleCount)
          }
          output.write(left, right, count)
        },
        progress = { stem, fraction ->
          val index = stemOrder.indexOf(stem).coerceAtLeast(0)
          val percent = 20 + ((index + fraction) / stemOrder.size * 80).toInt()
          onProgress(StemJobProgress("Separating ${stem.replaceFirstChar { it.uppercase() }}", percent.coerceIn(20, 99)))
          if (fraction >= 1f) outputs[stem]?.finish()
        },
      )
      check(outputs.keys.containsAll(stemOrder)) { "Separation did not produce all four stems" }
      outputs.map { (name, writer) -> SeparatedStem(name, writer.uri) }
    } catch (error: Throwable) {
      outputs.values.forEach { it.abort() }
      throw error
    } finally {
      input.delete()
      pcm.delete()
    }
  }

  private suspend fun decodeToPcm(source: File, output: File) {
    val args = arrayOf(
      "-y", "-v", "error", "-i", source.absolutePath,
      "-map", "0:a:0", "-vn", "-ar", "44100", "-ac", "2",
      "-f", "f32le", "-acodec", "pcm_f32le", output.absolutePath,
    )
    suspendCancellableCoroutine<Unit> { continuation ->
      val session = FFmpegKit.executeWithArgumentsAsync(args) { completed ->
        if (!continuation.isActive) return@executeWithArgumentsAsync
        if (ReturnCode.isSuccess(completed.returnCode) && output.isFile && output.length() > 0L) {
          continuation.resume(Unit)
        } else {
          continuation.resumeWithException(
            IllegalArgumentException("Could not decode this file as audio: ${completed.output.take(500)}"),
          )
        }
      }
      continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }
  }
}

private class StemOutputWriter(
  private val context: Context,
  sourceTitle: String,
  private val stem: String,
  sampleCount: Long,
) {
  internal val uri: Uri
  private var stream: OutputStream
  private var finished = false
  private var writtenFrames = 0L
  private val expectedFrames = sampleCount
  private val totalPcmBytes = sampleCount * 4L // 16-bit stereo

  init {
    val base = sourceTitle.substringBeforeLast('.', sourceTitle).ifBlank { "Audio" }
      .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(90)
    val stemLabel = when (stem) {
      "bass" -> "Bass"
      "drums" -> "Drums"
      "other" -> "Other"
      else -> "Vocals"
    }
    val name = "$base - $stemLabel stem.wav"
    val values = ContentValues().apply {
      put(MediaStore.Audio.Media.DISPLAY_NAME, name)
      put(MediaStore.Audio.Media.TITLE, "$base - $stemLabel stem")
      put(MediaStore.Audio.Media.ARTIST, "RajTune Stem Splitter")
      put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
      put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/RajTune Stems")
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Audio.Media.IS_PENDING, 1)
    }
    uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
      ?: error("Could not create the $stem output file")
    try {
      stream = BufferedOutputStream(context.contentResolver.openOutputStream(uri, "w")
        ?: error("Could not write the $stem output file"))
      writeWavHeader(stream, totalPcmBytes)
    } catch (error: Throwable) {
      context.contentResolver.delete(uri, null, null)
      throw error
    }
  }

  fun write(left: FloatArray, right: FloatArray, count: Int) {
    check(!finished) { "The $stem output is already complete" }
    val bytes = ByteArray(count * 4)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until count) {
      buffer.putShort(toPcm16(left[i]))
      buffer.putShort(toPcm16(right[i]))
    }
    check(writtenFrames + count <= expectedFrames) { "The $stem output exceeded its expected duration" }
    stream.write(bytes)
    writtenFrames += count
  }

  fun finish() {
    if (finished) return
    check(writtenFrames == expectedFrames) { "The $stem output ended before all audio was written" }
    stream.flush()
    stream.close()
    val values = ContentValues().apply {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Audio.Media.IS_PENDING, 0)
    }
    check(context.contentResolver.update(uri, values, null, null) > 0) { "Could not publish the $stem output" }
    finished = true
  }

  fun abort() {
    runCatching { if (!finished) stream.close() }
    runCatching { context.contentResolver.delete(uri, null, null) }
  }

  private fun toPcm16(sample: Float): Short {
    val scaled = (sample.coerceIn(-1f, 1f) * if (sample < 0f) 32768f else 32767f).toInt()
    return scaled.toShort()
  }

  private fun writeWavHeader(output: OutputStream, dataLength: Long) {
    require(dataLength <= Int.MAX_VALUE - 36L) { "Audio is too long for a standard WAV file" }
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt((36L + dataLength).toInt())
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16)
    header.putShort(1)
    header.putShort(2)
    header.putInt(44100)
    header.putInt(44100 * 2 * 2)
    header.putShort(4)
    header.putShort(16)
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(dataLength.toInt())
    output.write(header.array())
  }
}
