package echo.music.iad1tya.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.extensions.togglePlayPause
import echo.music.iad1tya.ui.player.InlineLyricsView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

internal fun aodProgress(positionMs: Long, durationMs: Long): Float =
  if (durationMs <= 0L) 0f else (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)

internal fun formatAodTime(positionMs: Long): String {
  val totalSeconds = (positionMs.coerceAtLeast(0L) / 1_000L)
  return "%d:%02d".format(Locale.ROOT, totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
fun AodScreen(navController: NavController) {
  val context = LocalContext.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val metadata by playerConnection.mediaMetadata.collectAsState()
  val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
  val duration by playerConnection.duration.collectAsState()
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var position by remember {
    mutableLongStateOf(playerConnection.player.currentPosition.coerceAtLeast(0L))
  }
  val clockFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

  LaunchedEffect(Unit) {
    while (true) {
      now = System.currentTimeMillis()
      position = playerConnection.player.currentPosition.coerceAtLeast(0L)
      delay(1_000)
    }
  }

  DisposableEffect(Unit) {
    val activity = context as? Activity
    val window = activity?.window
    val originalBrightness =
      window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    val hadKeepScreenOn =
      window?.let {
        it.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
      } ?: false
    val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
    val systemBars = WindowInsetsCompat.Type.systemBars()
    val wereSystemBarsVisible =
      window?.decorView?.rootWindowInsets?.let {
        WindowInsetsCompat.toWindowInsetsCompat(it).isVisible(systemBars)
      } ?: true
    val originalBarsBehavior = controller?.systemBarsBehavior
    if (window != null) {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      window.attributes = window.attributes.apply { screenBrightness = 0.06f }
      controller?.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller?.hide(WindowInsetsCompat.Type.systemBars())
    }
    onDispose {
      if (window != null) {
        if (hadKeepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
        controller?.systemBarsBehavior =
          originalBarsBehavior ?: WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        if (wereSystemBarsVisible) controller?.show(systemBars) else controller?.hide(systemBars)
      }
    }
  }

  BackHandler { navController.popBackStack() }
  val progress = aodProgress(position, duration)

  Column(
    modifier =
      Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 28.dp, vertical = 18.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = { navController.popBackStack() }) {
        Icon(
          Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = "Exit always-on player",
          tint = Color(0xFF858585),
        )
      }
      Spacer(Modifier.weight(1f))
      Text(
        text = clockFormat.format(Date(now)),
        style = MaterialTheme.typography.headlineMedium,
        color = Color(0xFF9A9A9A),
        fontWeight = FontWeight.Light,
      )
    }
    Spacer(Modifier.height(12.dp))
    AsyncImage(
      model = metadata?.thumbnailUrl,
      contentDescription = "Album artwork",
      contentScale = ContentScale.Crop,
      modifier =
        Modifier.fillMaxWidth(0.56f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).alpha(0.78f),
    )
    Spacer(Modifier.height(16.dp))
    Text(
      text = metadata?.title ?: "Nothing playing",
      style = MaterialTheme.typography.titleLarge,
      color = Color(0xFFCBCBCB),
      fontWeight = FontWeight.Medium,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = metadata?.artists?.joinToString { it.name }.orEmpty(),
      style = MaterialTheme.typography.bodyMedium,
      color = Color(0xFF777777),
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(14.dp))
    LinearProgressIndicator(
      progress = progress,
      modifier = Modifier.fillMaxWidth().height(2.dp),
      color = Color(0xFF9A9A9A),
      trackColor = Color(0xFF282828),
    )
    Row(
      Modifier.fillMaxWidth().padding(top = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        formatAodTime(position),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF666666),
      )
      Text(
        formatAodTime(duration),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF666666),
      )
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      IconButton(
        onClick = { playerConnection.player.seekToPreviousMediaItem() },
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          Icons.Rounded.SkipPrevious,
          contentDescription = "Previous",
          tint = Color(0xFF888888),
          modifier = Modifier.size(28.dp),
        )
      }
      IconButton(
        onClick = { playerConnection.togglePlayPause() },
        modifier = Modifier.size(56.dp),
      ) {
        Icon(
          if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = if (isPlaying) "Pause" else "Play",
          tint = Color(0xFFC8C8C8),
          modifier = Modifier.size(36.dp),
        )
      }
      IconButton(
        onClick = { playerConnection.player.seekToNext() },
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          Icons.Rounded.SkipNext,
          contentDescription = "Next",
          tint = Color(0xFF888888),
          modifier = Modifier.size(28.dp),
        )
      }
    }
    if (metadata != null) {
      Box(Modifier.fillMaxWidth().weight(0.7f).padding(top = 4.dp)) {
        InlineLyricsView(
          mediaMetadata = metadata,
          showLyrics = true,
          modifier = Modifier.fillMaxSize(),
          positionProvider = { playerConnection.player.currentPosition },
        )
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      "Keeps this screen awake and dims it while open. Battery use may increase; this is an in-app display, not Android’s system AOD.",
      style = MaterialTheme.typography.labelSmall,
      color = Color(0xFF505050),
      textAlign = TextAlign.Center,
    )
  }
}
