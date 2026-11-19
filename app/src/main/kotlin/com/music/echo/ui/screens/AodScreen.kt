package echo.music.iad1tya.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.extensions.togglePlayPause
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun AodScreen(navController: NavController) {
  val context = LocalContext.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val metadata by playerConnection.mediaMetadata.collectAsState()
  val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  val clockFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

  LaunchedEffect(Unit) {
    while (true) {
      now = System.currentTimeMillis()
      delay(1_000)
    }
  }

  DisposableEffect(Unit) {
    val activity = context as? Activity
    val window = activity?.window
    val originalBrightness = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    val hadKeepScreenOn = window?.let {
      it.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    } ?: false
    val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
    val systemBars = WindowInsetsCompat.Type.systemBars()
    val wereSystemBarsVisible = window?.decorView?.rootWindowInsets?.let {
      WindowInsetsCompat.toWindowInsetsCompat(it).isVisible(systemBars)
    } ?: true
    val originalBarsBehavior = controller?.systemBarsBehavior
    if (window != null) {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      window.attributes = window.attributes.apply { screenBrightness = 0.06f }
      controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller?.hide(WindowInsetsCompat.Type.systemBars())
    }
    onDispose {
      if (window != null) {
        if (hadKeepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
        controller?.systemBarsBehavior = originalBarsBehavior ?: WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        if (wereSystemBarsVisible) controller?.show(systemBars) else controller?.hide(systemBars)
      }
    }
  }

  BackHandler { navController.popBackStack() }

  Column(
    modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 28.dp, vertical = 22.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Row(Modifier.align(Alignment.Start)) {
      IconButton(onClick = { navController.popBackStack() }) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Exit always-on player", tint = Color(0xFFB4B4B4))
      }
    }
    Text(
      text = clockFormat.format(Date(now)),
      style = MaterialTheme.typography.displayLarge,
      color = Color(0xFFB8B8B8),
      fontWeight = FontWeight.Light,
    )
    Spacer(Modifier.height(28.dp))
    Text(
      text = metadata?.title ?: "Nothing playing",
      style = MaterialTheme.typography.titleLarge,
      color = Color(0xFFD0D0D0),
      fontWeight = FontWeight.Medium,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = metadata?.artists?.joinToString { it.name }.orEmpty(),
      style = MaterialTheme.typography.bodyLarge,
      color = Color(0xFF777777),
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
      IconButton(onClick = { playerConnection.player.seekToPreviousMediaItem() }, modifier = Modifier.size(56.dp)) {
        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color(0xFFB8B8B8), modifier = Modifier.size(32.dp))
      }
      IconButton(onClick = { playerConnection.togglePlayPause() }, modifier = Modifier.size(64.dp)) {
        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = Color(0xFFD8D8D8), modifier = Modifier.size(40.dp))
      }
      IconButton(onClick = { playerConnection.player.seekToNext() }, modifier = Modifier.size(56.dp)) {
        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color(0xFFB8B8B8), modifier = Modifier.size(32.dp))
      }
    }
    Spacer(Modifier.height(28.dp))
    Text(
      "Keeps this screen awake and dims it while open. Battery use may increase; this is an in-app display, not Android’s system AOD.",
      style = MaterialTheme.typography.bodySmall,
      color = Color(0xFF555555),
      textAlign = TextAlign.Center,
    )
  }
}
