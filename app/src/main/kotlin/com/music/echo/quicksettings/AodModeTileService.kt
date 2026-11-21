package echo.music.iad1tya.quicksettings

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import echo.music.iad1tya.MainActivity
import echo.music.iad1tya.R

/** Shortcut to the existing dimmed in-app player; it does not activate Android's system AOD. */
class AodModeTileService : TileService() {
  override fun onStartListening() {
    super.onStartListening()
    qsTile?.apply {
      icon = Icon.createWithResource(this@AodModeTileService, R.drawable.bedtime)
      label = getString(R.string.qs_tile_aod_player)
      state = Tile.STATE_INACTIVE
      updateTile()
    }
  }

  override fun onClick() {
    super.onClick()
    val launchIntent =
      Intent(this, MainActivity::class.java).apply {
        action = MainActivity.ACTION_AOD_MODE
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val pendingIntent =
        PendingIntent.getActivity(
          this,
          1,
          launchIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      startActivityAndCollapse(pendingIntent)
    } else {
      @Suppress("DEPRECATION") startActivityAndCollapse(launchIntent)
    }
  }
}
