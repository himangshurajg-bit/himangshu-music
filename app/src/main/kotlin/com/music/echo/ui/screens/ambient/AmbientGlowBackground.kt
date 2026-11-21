package echo.music.iad1tya.ui.screens.ambient

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import echo.music.iad1tya.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AmbientGlowBackground(
  mediaMetadata: MediaMetadata?,
  modifier: Modifier = Modifier,
  intensity: Float = 1f,
  animate: Boolean = true,
) {
  val glowIntensity = intensity.coerceIn(0f, 1f)
  val context = LocalContext.current
  val animationsDisabled =
    Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.ANIMATOR_DURATION_SCALE,
      1f,
    ) == 0f
  val shouldAnimate = animate && !animationsDisabled
  var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }

  LaunchedEffect(mediaMetadata?.id) {
    val currentMetadata = mediaMetadata ?: return@LaunchedEffect
    val url = currentMetadata.thumbnailUrl ?: return@LaunchedEffect

    withContext(Dispatchers.IO) {
      val request =
        ImageRequest.Builder(context).data(url).size(100, 100).allowHardware(false).build()

      val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
      if (result != null) {
        val bitmap = result.image?.toBitmap()
        if (bitmap != null) {
          val fallbackColor = Color.DarkGray.toArgb()
          val palette =
            Palette.from(bitmap).maximumColorCount(8).resizeBitmapArea(100 * 100).generate()

          val extractedColors =
            listOfNotNull(
                palette.getVibrantColor(fallbackColor).let { Color(it) },
                palette.getLightVibrantColor(fallbackColor).let { Color(it) },
                palette.getDarkVibrantColor(fallbackColor).let { Color(it) },
                palette.getMutedColor(fallbackColor).let { Color(it) },
                palette.getLightMutedColor(fallbackColor).let { Color(it) },
                palette.getDarkMutedColor(fallbackColor).let { Color(it) }
              )
              .distinct()

          withContext(Dispatchers.Main) { gradientColors = extractedColors }
        }
      }
    }
  }

  AnimatedContent(
    targetState = gradientColors,
    transitionSpec = {
      if (shouldAnimate) fadeIn(tween(1200)) togetherWith fadeOut(tween(1200))
      else EnterTransition.None togetherWith ExitTransition.None
    },
    label = "GlowAnimatedContent",
    modifier = modifier
  ) { colors ->
    if (colors.isNotEmpty()) {
      val progress =
        if (shouldAnimate) {
          val infiniteTransition = rememberInfiniteTransition(label = "GlowAnimation")
          val animatedProgress by
            infiniteTransition.animateFloat(
              initialValue = 0f,
              targetValue = 1f,
              animationSpec =
                infiniteRepeatable(
                  animation = tween(24000, easing = LinearEasing),
                  repeatMode = RepeatMode.Restart
                ),
              label = "glowProgress"
            )
          animatedProgress
        } else {
          0f
        }

      fun rotatedColorAt(index: Int): Color {
        val size = colors.size
        val idx = index.toFloat() + progress * size
        val a = kotlin.math.floor(idx).toInt() % size
        val b = (a + 1) % size
        val frac = idx - kotlin.math.floor(idx)
        return androidx.compose.ui.graphics.lerp(
          colors.getOrElse(a) { Color.DarkGray },
          colors.getOrElse(b) { Color.DarkGray },
          frac
        )
      }

      fun oscillate(min: Float, max: Float, phase: Float, speed: Float = 1f): Float {
        val v = kotlin.math.sin(2f * kotlin.math.PI.toFloat() * (progress * speed + phase))
        return min + (max - min) * ((v + 1f) * 0.5f)
      }

      val color1 = rotatedColorAt(0)
      val color2 = rotatedColorAt(1)
      val color3 = rotatedColorAt(2)
      val color4 = rotatedColorAt(3)
      val color5 = rotatedColorAt(4)
      val color6 = rotatedColorAt(5)

      val o1x = oscillate(0.0f, 1.0f, 0.00f, 1.0f)
      val o1y = oscillate(0.0f, 0.5f, 0.07f, 1.0f)
      val r1 = oscillate(0.8f, 1.6f, 0.12f, 1.0f)

      val o2x = oscillate(1.0f, 0.0f, 0.2f, 1.0f)
      val o2y = oscillate(0.5f, 1.0f, 0.25f, 1.0f)
      val r2 = oscillate(0.7f, 1.5f, 0.18f, 1.0f)

      val o3x = oscillate(0.2f, 0.8f, 0.33f, 1.0f)
      val o3y = oscillate(0.8f, 0.2f, 0.36f, 1.0f)
      val r3 = oscillate(0.6f, 1.4f, 0.29f, 1.0f)

      val o4x = oscillate(0.3f, 0.7f, 0.44f, 1.0f)
      val o4y = oscillate(0.2f, 0.8f, 0.41f, 1.0f)
      val r4 = oscillate(0.9f, 1.7f, 0.47f, 1.0f)

      val o5x = oscillate(0.4f, 0.6f, 0.55f, 1.0f)
      val o5y = oscillate(0.0f, 1.0f, 0.51f, 1.0f)
      val r5 = oscillate(0.7f, 1.5f, 0.58f, 1.0f)

      val o6x = oscillate(0.0f, 1.0f, 0.66f, 1.0f)
      val o6y = oscillate(0.5f, 0.7f, 0.62f, 1.0f)
      val r6 = oscillate(0.8f, 1.8f, 0.69f, 1.0f)

      Box(
        modifier =
          Modifier.fillMaxSize().drawWithCache {
            val width = size.width
            val height = size.height
            val baseColor = Color(0xFF050505)

            val brush1 =
              Brush.radialGradient(
                colors =
                  listOf(
                    color1.copy(alpha = 0.85f * glowIntensity),
                    color1.copy(alpha = 0.5f * glowIntensity),
                    Color.Transparent,
                  ),
                center = Offset(width * o1x, height * o1y),
                radius = width * r1
              )
            val brush2 =
              Brush.radialGradient(
                colors =
                  listOf(
                    color2.copy(alpha = 0.8f * glowIntensity),
                    color2.copy(alpha = 0.45f * glowIntensity),
                    Color.Transparent,
                  ),
                center = Offset(width * o2x, height * o2y),
                radius = width * r2
              )
            val brush3 =
              Brush.radialGradient(
                colors =
                  listOf(
                    color3.copy(alpha = 0.75f * glowIntensity),
                    color3.copy(alpha = 0.4f * glowIntensity),
                    Color.Transparent,
                  ),
                center = Offset(width * o3x, height * o3y),
                radius = width * r3
              )
            val brush4 =
              Brush.radialGradient(
                colors =
                  listOf(
                    color4.copy(alpha = 0.7f * glowIntensity),
                    color4.copy(alpha = 0.35f * glowIntensity),
                    Color.Transparent,
                  ),
                center = Offset(width * o4x, height * o4y),
                radius = width * r4
              )
            val brush5 =
              Brush.radialGradient(
                colors =
                  listOf(
                    color5.copy(alpha = 0.65f * glowIntensity),
                    color5.copy(alpha = 0.3f * glowIntensity),
                    Color.Transparent,
                  ),
                center = Offset(width * o5x, height * o5y),
                radius = width * r5
              )
            val brush6 =
              Brush.radialGradient(
                colors =
                  listOf(
                    color6.copy(alpha = 0.6f * glowIntensity),
                    color6.copy(alpha = 0.25f * glowIntensity),
                    Color.Transparent,
                  ),
                center = Offset(width * o6x, height * o6y),
                radius = width * r6
              )

            onDrawBehind {
              if (glowIntensity >= 0.99f) drawRect(color = baseColor)
              drawRect(brush = brush1)
              drawRect(brush = brush2)
              drawRect(brush = brush3)
              drawRect(brush = brush4)
              drawRect(brush = brush5)
              drawRect(brush = brush6)
            }
          }
      )
    } else {
      // Fallback backgound
      Box(modifier = Modifier.fillMaxSize())
    }
  }
}
