package echo.music.iad1tya.echomusic.updater.downloadmanager

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import echo.music.iad1tya.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateDownloadWorker(private val context: Context, workerParams: WorkerParameters) :
  CoroutineWorker(context, workerParams) {

  override suspend fun doWork(): Result =
    withContext(Dispatchers.IO) {
      val apkUrl = inputData.getString("apk_url") ?: return@withContext Result.failure()
      val version = inputData.getString("version") ?: "unknown"
      val fileSize = inputData.getString("file_size") ?: ""

      try {
        DownloadNotificationManager.initialize(context)
        val startingNotification =
          DownloadNotificationManager.getDownloadStartingNotification(version, fileSize)
        val foregroundInfo =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
              DownloadNotificationManager.NOTIFICATION_ID,
              startingNotification,
              ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
          } else {
            ForegroundInfo(DownloadNotificationManager.NOTIFICATION_ID, startingNotification)
          }
        setForeground(foregroundInfo)
      } catch (e: Exception) {}

      var partialDownload: File? = null
      try {
        val url = URL(apkUrl)
        if (url.protocol != "https" || url.host !in setOf("github.com", "nightly.link")) {
          return@withContext Result.failure()
        }
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
          DownloadNotificationManager.showDownloadFailed(
            version,
            context.getString(R.string.server_error, connection.responseCode)
          )
          if (connection.responseCode >= 500) {
            return@withContext Result.retry()
          }
          return@withContext Result.failure()
        }

        val fileLength = connection.contentLengthLong
        val inputStream = connection.inputStream

        val externalDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
          ?: throw IOException("App storage is unavailable")
        val downloadDir = File(externalDownloadsDir, "echo_updates")
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
          connection.disconnect()
          return@withContext Result.failure()
        }

        val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val isZip = apkUrl.contains("nightly.link", ignoreCase = true) || apkUrl.endsWith(".zip", ignoreCase = true)
        val downloadFile = if (isZip) File(downloadDir, "update-$safeVersion.zip")
          else File(downloadDir, "update-$safeVersion.apk")
        partialDownload = downloadFile
        val buffer = ByteArray(8192)
        var totalBytesRead = 0L
        var lastProgress = -1
        var lastNotificationTime = 0L
        connection.inputStream.use { inputStream ->
          FileOutputStream(downloadFile, false).use { outputStream ->
            while (true) {
              val bytesRead = inputStream.read(buffer)
              if (bytesRead == -1) break
              if (isStopped) {
                downloadFile.delete()
                partialDownload = null
                return@withContext Result.retry()
              }

              outputStream.write(buffer, 0, bytesRead)
              totalBytesRead += bytesRead
              if (fileLength > 0) {
                val progress = (totalBytesRead.toFloat() / fileLength.toFloat() * 100).toInt()
                val currentTime = System.currentTimeMillis()
                if (progress > lastProgress && currentTime - lastNotificationTime >= 1000) {
                  lastProgress = progress
                  lastNotificationTime = currentTime
                  val progressNotification =
                    DownloadNotificationManager.getDownloadProgressNotification(progress, version)
                  val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                  notificationManager.notify(DownloadNotificationManager.NOTIFICATION_ID, progressNotification)
                  setProgress(workDataOf("progress" to progress.toFloat() / 100f))
                }
              }
            }
            outputStream.flush()
          }
        }
        connection.disconnect()
        if (downloadFile.length() == 0L || (fileLength > 0 && downloadFile.length() != fileLength)) {
          downloadFile.delete()
          throw IOException("Downloaded update is incomplete")
        }

        val finalFile =
          if (isZip) {
            val targetApkFile = File(downloadDir, "update-$safeVersion.apk")
            var extracted = false
            try {
              ZipInputStream(downloadFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                  if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                    FileOutputStream(targetApkFile).use { fos -> zis.copyTo(fos) }
                    extracted = true
                    break
                  }
                  entry = zis.nextEntry
                }
              }
            } catch (e: Exception) {
              if (downloadFile.exists()) downloadFile.delete()
              DownloadNotificationManager.showDownloadFailed(
                version,
                e.message ?: "Failed to extract zip file"
              )
              return@withContext Result.failure()
            } finally {
              if (downloadFile.exists()) {
                downloadFile.delete()
              }
            }
            if (!extracted) {
              DownloadNotificationManager.showDownloadFailed(version, "Could not find APK in zip")
              return@withContext Result.failure()
            }
            targetApkFile
          } else {
            downloadFile
          }

        val isValidApk = runCatching {
          ZipFile(finalFile).use { zip -> zip.getEntry("AndroidManifest.xml") != null }
        }.getOrDefault(false)
        if (!isValidApk) {
          finalFile.delete()
          DownloadNotificationManager.showDownloadFailed(version, "The downloaded file is not a valid APK")
          return@withContext Result.failure()
        }

        if (version.startsWith("nightly-r")) {
          val runNumberString = version.removePrefix("nightly-r")
          val runNumber = runNumberString.toIntOrNull()
          if (runNumber != null) {
            val sharedPreferences =
              context.getSharedPreferences("update_settings", Context.MODE_PRIVATE)
            sharedPreferences.edit().putInt("last_installed_nightly_run", runNumber).apply()
          }
        }

        partialDownload = null
        DownloadNotificationManager.showDownloadComplete(version, finalFile.absolutePath)

        Result.success(workDataOf("file_path" to finalFile.absolutePath, "version" to version))
      } catch (e: IOException) {
        partialDownload?.delete()
        DownloadNotificationManager.showDownloadFailed(
          version,
          e.message ?: context.getString(R.string.download_failed)
        )
        return@withContext Result.retry()
      } catch (e: Exception) {
        partialDownload?.delete()
        DownloadNotificationManager.showDownloadFailed(
          version,
          e.message ?: context.getString(R.string.download_failed)
        )
        Result.failure()
      }
    }
}
