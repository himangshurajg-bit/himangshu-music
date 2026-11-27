package echo.music.iad1tya.stems

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Minimal cloud client: explicit endpoint + bearer token; never uploads unless endpoint non-empty. */
internal class StemCloudClient(private val context: Context) {
  suspend fun separateCloud(
    endpoint: String,
    token: String?,
    sourceUri: Uri,
    sourceTitle: String,
    onProgress: (StemJobProgress) -> Unit,
  ): List<SeparatedStem> {
    require(endpoint.isNotBlank()) { "Cloud not configured - Offline mode only" }
    val base = endpoint.trim().trimEnd('/')
    check(base.startsWith("https://") || base.startsWith("http://127.0.0.1") || base.startsWith("http://10.") || base.startsWith("http://192.168.")) {
      "Cloud endpoint must be HTTPS (or local IP for testing)"
    }
    onProgress(StemJobProgress("Uploading audio to cloud", 5))
    val jobId = createJob(base, token, sourceUri)
    onProgress(StemJobProgress("Processing on cloud", 20))
    var state = "queued"
    var percent = 20
    while (state !in listOf("complete", "failed", "cancelled")) {
      currentCoroutineContext().ensureActive()
      val (s, p) = poll(base, token, jobId)
      state = s; percent = p
      onProgress(StemJobProgress("Cloud $state", (20 + 60 * percent / 100).coerceIn(20, 95)))
      kotlinx.coroutines.delay(2000)
    }
    check(state == "complete") { "Cloud processing $state" }
    onProgress(StemJobProgress("Downloading stems", 92))
    val stems = listOf("bass", "drums", "other", "vocals")
    val out = mutableListOf<SeparatedStem>()
    for (stem in stems) {
      val bytes = downloadStem(base, token, jobId, stem)
      val uri = saveStem(sourceTitle, stem, bytes)
      out += SeparatedStem(stem, uri)
    }
    onProgress(StemJobProgress("Cloud stems saved to Music/RajTune Stems", 100))
    return out
  }

  private fun authHeaders(token: String?): Map<String, String> =
    if (token.isNullOrBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${token.trim()}")

  private fun createJob(base: String, token: String?, sourceUri: Uri): String {
    val url = URL("$base/v1/stem-jobs")
    val boundary = "----RajTune${UUID.randomUUID()}"
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    authHeaders(token).forEach { (k, v) -> conn.setRequestProperty(k, v) }
    conn.setRequestProperty("Idempotency-Key", UUID.randomUUID().toString())
    val cr = context.contentResolver.openInputStream(sourceUri) ?: error("Could not open audio")
    cr.use { input ->
      conn.outputStream.use { out ->
        out.write("--$boundary\r\n".toByteArray())
        out.write("Content-Disposition: form-data; name=\"audio\"; filename=\"audio.wav\"\r\n".toByteArray())
        out.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
        val buf = ByteArray(64 * 1024)
        while (true) {
          val n = input.read(buf); if (n < 0) break
          out.write(buf, 0, n)
        }
        out.write("\r\n--$boundary--\r\n".toByteArray())
      }
    }
    val code = conn.responseCode
    val body = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
    conn.disconnect()
    check(code in 200..299) { "Cloud upload failed (HTTP $code): ${body.take(300)}" }
    val id = Regex("\"job_id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: error("Cloud did not return job_id")
    return id
  }

  private fun poll(base: String, token: String?, jobId: String): Pair<String, Int> {
    val conn = URL("$base/v1/stem-jobs/$jobId").openConnection() as HttpURLConnection
    authHeaders(token).forEach { (k, v) -> conn.setRequestProperty(k, v) }
    val code = conn.responseCode
    val body = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
    conn.disconnect()
    check(code in 200..299) { "Cloud status failed (HTTP $code)" }
    val state = Regex("\"state\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: "unknown"
    val pct = Regex("\"progress_percent\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return state to pct
  }

  private fun downloadStem(base: String, token: String?, jobId: String, stem: String): ByteArray {
    val conn = URL("$base/v1/stem-jobs/$jobId/stems/$stem").openConnection() as HttpURLConnection
    authHeaders(token).forEach { (k, v) -> conn.setRequestProperty(k, v) }
    val code = conn.responseCode
    val bytes = (if (code in 200..299) conn.inputStream else conn.errorStream).readBytes()
    conn.disconnect()
    check(code == 200) { "Downloading $stem failed (HTTP $code)" }
    return bytes
  }

  private fun saveStem(sourceTitle: String, stem: String, bytes: ByteArray): Uri {
    val base = sourceTitle.substringBeforeLast('.', sourceTitle).ifBlank { "Audio" }
      .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(90)
    val label = when (stem) { "bass" -> "Bass"; "drums" -> "Drums"; "other" -> "Other"; else -> "Vocals" }
    val values = ContentValues().apply {
      put(MediaStore.Audio.Media.DISPLAY_NAME, "$base - $label stem.wav")
      put(MediaStore.Audio.Media.TITLE, "$base - $label stem")
      put(MediaStore.Audio.Media.ARTIST, "RajTune Stem Cloud")
      put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
      put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/RajTune Stems")
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Audio.Media.IS_PENDING, 1)
    }
    val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
      ?: error("Could not create output file")
    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
      ?: error("Could not write stem file")
    values.clear()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      values.put(MediaStore.Audio.Media.IS_PENDING, 0)
      context.contentResolver.update(uri, values, null, null)
    }
    return uri
  }
}
