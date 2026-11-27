package echo.music.iad1tya.stems

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Fetches the four CC BY 4.0 KUIELab MDX-Net B checkpoints on first use, then keeps them offline. */
internal class StemModelRepository(private val context: Context) {
  private val modelDir = File(context.filesDir, "stem_models/kuielab_mdx_b")
  private val modelNames = listOf("bass", "drums", "other", "vocals")
  private val modelSha256 = mapOf(
    "bass" to "b4b7080fe501d0bece62076c5d4eda4d6590c5207ed78ec84a57bac0740a061d",
    "drums" to "a6fecee758059b33ed99f6dabba297439b3e7cacfac4b1097bd324aff8052208",
    "other" to "b0d0b63950ac332333fea2d58f68c92fd3ab0aae071398c2a8beeae1ad15b655",
    "vocals" to "9b7dcb9d878acb0f3e64ff3fd27750faae96577013f6d50f5996875bf4250713",
  )

  fun availableModels(): Map<String, File>? {
    val files = modelNames.associateWith { File(modelDir, "$it.onnx") }
    return files.takeIf { all -> all.all { (name, file) -> file.isFile && sha256(file) == modelSha256[name] } }
  }

  suspend fun ensureModels(progress: (Int) -> Unit): Map<String, File> {
    availableModels()?.let { return it }
    if (!modelDir.exists() && !modelDir.mkdirs()) error("Could not create the model cache")
    val archive = File(context.cacheDir, "kuielab_mdx_b.zip.part")
    val readyArchive = File(context.cacheDir, "kuielab_mdx_b.zip")
    val url = URL("https://zenodo.org/records/6513062/files/onnx_B.zip?download=1")
    val connection = (url.openConnection() as HttpURLConnection).apply {
      connectTimeout = 20_000
      readTimeout = 15_000
      instanceFollowRedirects = true
      setRequestProperty("User-Agent", "RajTune/StemSplitter")
    }
    try {
      connection.connect()
      check(connection.responseCode in 200..299) { "Model download failed (HTTP ${connection.responseCode})" }
      val total = connection.contentLengthLong
      connection.inputStream.use { input ->
        FileOutputStream(archive).use { output ->
          val buffer = ByteArray(128 * 1024)
          var copied = 0L
          while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            copied += count
            if (total > 0L) progress((copied * 100 / total).toInt().coerceIn(0, 99))
          }
          output.fd.sync()
        }
      }
      check(md5(archive) == "39eb4bb43137a6c209caecdbcfd1d8e2") { "Downloaded model archive failed its integrity check" }
      readyArchive.delete()
      check(archive.renameTo(readyArchive)) { "Could not finalize downloaded model archive" }
      val extracted = mutableMapOf<String, File>()
      ZipInputStream(BufferedInputStream(FileInputStream(readyArchive))).use { zip ->
        while (true) {
          currentCoroutineContext().ensureActive()
          val entry = zip.nextEntry ?: break
          val stem = modelStemNameFromZipEntry(entry.name, modelNames)
          if (stem != null) {
            val name = "$stem.onnx"
            val temp = File(modelDir, "$name.part")
            FileOutputStream(temp).use { output ->
              val modelBuffer = ByteArray(64 * 1024)
              while (true) {
                currentCoroutineContext().ensureActive()
                val count = zip.read(modelBuffer)
                if (count < 0) break
                output.write(modelBuffer, 0, count)
              }
            }
            check(sha256(temp) == modelSha256[stem]) { "The $stem model failed its integrity check" }
            val target = File(modelDir, name)
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Could not save the $stem model" }
            extracted[stem] = target
          }
          zip.closeEntry()
        }
      }
      check(extracted.keys.containsAll(modelNames)) { "The model archive is missing a stem model" }
      progress(100)
      return extracted
    } catch (error: Throwable) {
      archive.delete()
      throw error
    } finally {
      connection.disconnect()
      readyArchive.delete()
    }
  }

  private fun digest(file: File, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
  }

  private fun sha256(file: File) = digest(file, "SHA-256")
  private fun md5(file: File) = digest(file, "MD5")
}

/** Return a known model stem key for a ZIP path such as `onnx_B/bass.onnx`. */
internal fun modelStemNameFromZipEntry(entryName: String, modelNames: List<String>): String? {
  val fileName = entryName.substringAfterLast('/')
  if (!fileName.endsWith(".onnx")) return null
  return fileName.removeSuffix(".onnx").takeIf { it in modelNames }
}
