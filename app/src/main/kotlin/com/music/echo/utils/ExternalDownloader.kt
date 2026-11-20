package echo.music.iad1tya.utils

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import echo.music.iad1tya.constants.DefaultExternalDownloaderPackage

internal data class ExternalDownloaderApp(val packageName: String, val label: String)

internal fun resolveExternalDownloaderPackage(
  configuredValue: String,
  availableApps: List<ExternalDownloaderApp>,
  defaultPackage: String = DefaultExternalDownloaderPackage,
): String? {
  val value = configuredValue.trim().ifEmpty { defaultPackage }

  availableApps.firstOrNull { it.packageName.equals(value, ignoreCase = true) }?.let {
    return it.packageName
  }
  availableApps.firstOrNull { it.label.equals(value, ignoreCase = true) }?.let {
    return it.packageName
  }

  return null
}


internal fun createExternalDownloaderIntent(packageName: String, songUrl: String): Intent =
  Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, songUrl)
    putExtra("TYPE", "audio")
    putExtra("BACKGROUND", false)
    setPackage(packageName)
  }

internal fun findExternalDownloaderPackage(
  packageManager: PackageManager,
  configuredValue: String,
): String? {
  val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
  val activities =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
      @Suppress("DEPRECATION") packageManager.queryIntentActivities(intent, 0)
    }
  val apps = activities.mapNotNull { toExternalDownloaderApp(it, packageManager) }
  return resolveExternalDownloaderPackage(configuredValue, apps)
}

private fun toExternalDownloaderApp(
  resolveInfo: ResolveInfo,
  packageManager: PackageManager,
): ExternalDownloaderApp? {
  val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return null
  val packageName = appInfo.packageName ?: return null
  val label = runCatching { appInfo.loadLabel(packageManager) }
    .getOrNull()
    ?.toString()
    .orEmpty()
  return ExternalDownloaderApp(packageName, label)
}
