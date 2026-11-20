package echo.music.iad1tya.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Helpers for a user-named, user-icon home-screen shortcut. */
object CustomHomeShortcut {
  private const val SHORTCUT_ID = "himangshu_music_custom_home_shortcut"
  private const val ICON_SIZE = 512
  private const val ICON_FILENAME = "custom_home_shortcut_icon.png"

  /** Copies a selected image into app-private storage as a bounded, square PNG. */
  fun saveIcon(context: Context, uri: Uri): File {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected image could not be read." }

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > ICON_SIZE * 2 ||
      bounds.outHeight / sampleSize > ICON_SIZE * 2) {
      sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded =
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("The selected image could not be opened.")
    val side = minOf(decoded.width, decoded.height)
    val left = (decoded.width - side) / 2
    val top = (decoded.height - side) / 2
    val square =
      if (left == 0 && top == 0 && side == decoded.width && side == decoded.height) decoded
      else Bitmap.createBitmap(decoded, left, top, side, side)
    val scaled = Bitmap.createScaledBitmap(square, ICON_SIZE, ICON_SIZE, true)

    val destination = File(context.filesDir, ICON_FILENAME)
    try {
      FileOutputStream(destination).use { output ->
        check(scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) {
          "The selected image could not be saved."
        }
      }
    } finally {
      if (scaled !== square) scaled.recycle()
      if (square !== decoded) square.recycle()
      decoded.recycle()
    }
    return destination
  }

  fun loadIcon(path: String): Bitmap? =
    path.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }

  /** Requests Android to pin a shortcut; a supported launcher displays its own confirmation. */
  fun requestPin(context: Context, name: String, icon: Bitmap): Boolean {
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
    if (!manager.isRequestPinShortcutSupported) return false

    val launchIntent =
      context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      } ?: return false
    val label = name.trim().take(40).ifBlank { "RajTune" }
    val shortcut =
      ShortcutInfo.Builder(context, SHORTCUT_ID)
        .setShortLabel(label.take(25))
        .setLongLabel(label)
        .setIcon(Icon.createWithAdaptiveBitmap(icon))
        .setIntent(launchIntent)
        .build()
    return manager.requestPinShortcut(shortcut, null)
  }
}
