package echo.music.iad1tya.ui.screens.settings

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import echo.music.dsp.audio.EightDMotionMode
import echo.music.iad1tya.LocalPlayerAwareWindowInsets
import echo.music.iad1tya.R
import echo.music.iad1tya.constants.EightDAudioBassKey
import echo.music.iad1tya.constants.EightDAudioDelayKey
import echo.music.iad1tya.constants.EightDAudioDepthKey
import echo.music.iad1tya.constants.EightDAudioEnabledKey
import echo.music.iad1tya.constants.EightDAudioIntensityKey
import echo.music.iad1tya.constants.EightDAudioModeKey
import echo.music.iad1tya.constants.EightDAudioReverbKey
import echo.music.iad1tya.constants.EightDAudioReverseKey
import echo.music.iad1tya.constants.EightDAudioBeatReactiveKey
import echo.music.iad1tya.constants.EightDAudioFrequencyPositioningKey
import echo.music.iad1tya.constants.EightDAudioFrequencySpreadKey
import echo.music.iad1tya.constants.EightDAudioSpeedKey
import echo.music.iad1tya.constants.EightDAudioWidthKey
import echo.music.iad1tya.ui.component.Material3SettingsGroup
import echo.music.iad1tya.ui.component.Material3SettingsItem
import echo.music.iad1tya.utils.rememberPreference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun EightDAudioSettingsScreen(navController: NavController) {
  val (enabled, setEnabled) = rememberPreference(EightDAudioEnabledKey, false)
  val (modeName, setModeName) = rememberPreference(EightDAudioModeKey, EightDMotionMode.ORBIT.name)
  val mode = EightDMotionMode.values().firstOrNull { it.name == modeName } ?: EightDMotionMode.ORBIT
  val (speed, setSpeed) = rememberPreference(EightDAudioSpeedKey, 1f)
  val (depth, setDepth) = rememberPreference(EightDAudioDepthKey, 0.7f)
  val (width, setWidth) = rememberPreference(EightDAudioWidthKey, 0.5f)
  val (bass, setBass) = rememberPreference(EightDAudioBassKey, 0f)
  val (reverb, setReverb) = rememberPreference(EightDAudioReverbKey, 0f)
  val (delay, setDelay) = rememberPreference(EightDAudioDelayKey, 0f)
  val (intensity, setIntensity) = rememberPreference(EightDAudioIntensityKey, 0.8f)
  val (reverse, setReverse) = rememberPreference(EightDAudioReverseKey, false)
  val (beatReactive, setBeatReactive) = rememberPreference(EightDAudioBeatReactiveKey, true)
  val (frequencyPositioning, setFrequencyPositioning) = rememberPreference(EightDAudioFrequencyPositioningKey, true)
  val (frequencySpread, setFrequencySpread) = rememberPreference(EightDAudioFrequencySpreadKey, 0.7f)
  var showModeDialog by remember { mutableStateOf(false) }
  val scrollState = rememberScrollState()
  val outputDevices = rememberOutputDevices()

  if (showModeDialog) {
    AlertDialog(
      onDismissRequest = { showModeDialog = false },
      title = { Text("Motion mode") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          EightDMotionMode.values().forEach { candidate ->
            Row(
              modifier = Modifier.fillMaxWidth().clickable {
                setModeName(candidate.name)
                showModeDialog = false
              }.padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected = mode == candidate, onClick = {
                setModeName(candidate.name)
                showModeDialog = false
              })
              Column(Modifier.padding(start = 8.dp)) {
                Text(candidate.label, style = MaterialTheme.typography.bodyLarge)
                Text(candidate.description, style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showModeDialog = false }) { Text("Done") }
      },
    )
  }

  Column(
    Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
      .verticalScroll(scrollState)
      .padding(horizontal = 16.dp),
  ) {
    Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
    Spacer(Modifier.height(16.dp))
    Text("8D stereo audio", style = MaterialTheme.typography.headlineSmall)
    Text(
      "Beat-reactive stereo motion with optional low/mid/high frequency placement. Frequency bands are not isolated instruments or vocals; this is not HRTF/binaural positioning.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
    )

    OrbitPreview(mode = mode, speed = speed, enabled = enabled)
    Spacer(Modifier.height(12.dp))

    Material3SettingsGroup(
      title = "Playback",
      scrollState = scrollState,
      items = listOf(
        Material3SettingsItem(
          icon = painterResource(R.drawable.graphic_eq),
          title = { Text("Enable 8D processing") },
          description = { Text("Switch off for an immediate, sample-preserving stereo bypass") },
          trailingContent = { Switch(checked = enabled, onCheckedChange = setEnabled) },
          onClick = { setEnabled(!enabled) },
        ),
        Material3SettingsItem(
          icon = painterResource(R.drawable.graphic_eq),
          title = { Text("Motion mode") },
          description = { Text("${mode.label} · ${mode.description}") },
          onClick = { showModeDialog = true },
        ),
        Material3SettingsItem(
          icon = painterResource(R.drawable.graphic_eq),
          title = { Text("Beat-reactive motion") },
          description = { Text("Detect rhythmic onsets and pulse the stereo movement") },
          trailingContent = { Switch(checked = beatReactive, onCheckedChange = setBeatReactive) },
          onClick = { setBeatReactive(!beatReactive) },
        ),
        Material3SettingsItem(
          icon = painterResource(R.drawable.graphic_eq),
          title = { Text("Frequency-band positioning") },
          description = { Text("Place bass, midrange and treble at different stereo positions; this does not separate vocals") },
          trailingContent = { Switch(checked = frequencyPositioning, onCheckedChange = setFrequencyPositioning) },
          onClick = { setFrequencyPositioning(!frequencyPositioning) },
        ),
        Material3SettingsItem(
          icon = painterResource(R.drawable.graphic_eq),
          title = { Text("Reverse direction") },
          description = { Text("Reverse the sweep for orbit modes") },
          trailingContent = { Switch(checked = reverse, onCheckedChange = setReverse) },
          onClick = { setReverse(!reverse) },
        ),
      ),
    )

    Spacer(Modifier.height(12.dp))
    Text("Sound controls", style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
    AudioSlider("Rotation speed", speed, 0.5f..3f, "${"%.1f".format(speed)}×", enabled,
      setSpeed, steps = 24)
    AudioSlider("Spatial depth", depth, 0f..1f, "${(depth * 100).roundToInt()}%", enabled, setDepth)
    AudioSlider("Stereo width", width, 0f..1f, "${(width * 100).roundToInt()}%", enabled, setWidth)
    AudioSlider("Effect intensity", intensity, 0f..1f, "${(intensity * 100).roundToInt()}%", enabled, setIntensity)
    AudioSlider("Frequency spread", frequencySpread, 0f..1f, "${(frequencySpread * 100).roundToInt()}%",
      enabled && frequencyPositioning, setFrequencySpread)
    AudioSlider("Bass enhancement", bass, 0f..1f, "${(bass * 100).roundToInt()}%", enabled, setBass)
    AudioSlider("Short ambience", reverb, 0f..1f, "${(reverb * 100).roundToInt()}%", enabled, setReverb)
    AudioSlider("Echo delay", delay, 0f..1f, "${(delay * 100).roundToInt()}%", enabled, setDelay)

    Spacer(Modifier.height(12.dp))
    Material3SettingsGroup(
      title = "Output",
      scrollState = scrollState,
      items = listOf(
        Material3SettingsItem(
          icon = painterResource(R.drawable.graphic_eq),
          title = { Text("Connected audio output") },
          description = {
            Text(if (outputDevices.isEmpty()) {
              "No wired, Bluetooth or USB headset detected. Stereo playback remains available; headphones are recommended."
            } else {
              "Detected: ${outputDevices.joinToString()} · device detection does not verify its stereo response."
            })
          },
        ),
      ),
    )
    Text(
      "The effect runs in the playback service and follows background playback. Multichannel input is passed to Media3's fallback path; mono stays mono. Bit-perfect/offload routes may bypass app DSP. No HRTF renderer or hardware headphone test is included.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
    )
    Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)))
  }
}

@Composable
private fun AudioSlider(
  title: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  valueLabel: String,
  enabled: Boolean,
  onValueChange: (Float) -> Unit,
  steps: Int = 99,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
  ) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(valueLabel, style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary)
      }
      Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onValueChange,
        valueRange = range,
        steps = steps,
        enabled = enabled,
      )
    }
  }
}

@Composable
private fun OrbitPreview(mode: EightDMotionMode, speed: Float, enabled: Boolean) {
  val duration = (10_000f / (speed * when (mode) {
    EightDMotionMode.SLOW_MOTION -> 0.35f
    EightDMotionMode.FAST_SPIN -> 3f
    else -> 1f
  })).roundToInt().coerceIn(1_000, 30_000)
  val transition = rememberInfiniteTransition(label = "stereo motion preview")
  val angle by transition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart),
    label = "preview angle",
  )
  val color = MaterialTheme.colorScheme.primary
  val trackColor = MaterialTheme.colorScheme.outlineVariant
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(24.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
      Canvas(Modifier.size(104.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.38f
        drawCircle(trackColor, radius, center, style = Stroke(width = 2.dp.toPx()))
        val radians = angle * PI.toFloat() / 180f
        val x = center.x + cos(radians) * radius
        val y = center.y + sin(radians) * radius
        drawCircle(color.copy(alpha = if (enabled) 1f else 0.45f), 7.dp.toPx(), Offset(x, y))
        drawCircle(trackColor, 3.dp.toPx(), center)
      }
      Column(Modifier.padding(start = 14.dp)) {
        Text("Motion preview", style = MaterialTheme.typography.titleSmall)
        Text(
          if (enabled) "Visual preview only; the selected motion also changes the processed stereo signal."
          else "Preview is illustrative. Audio DSP is bypassed while 8D is off.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun rememberOutputDevices(): List<String> {
  val context = LocalContext.current
  val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
  var names by remember { mutableStateOf(emptyList<String>()) }
  DisposableEffect(audioManager) {
    fun refresh() {
      val knownTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
      )
      names = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type in knownTypes }
        .map { device ->
          val name = device.productName?.toString()?.takeIf(String::isNotBlank)
          name ?: when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth headset"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            else -> "Wired headset"
          }
        }
        .distinct()
    }
    val callback = object : AudioDeviceCallback() {
      override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
      override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }
    audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    refresh()
    onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
  }
  return names
}
