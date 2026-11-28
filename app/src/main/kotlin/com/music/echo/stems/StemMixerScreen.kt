package echo.music.iad1tya.stems

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun StemMixerSection(
  mixer: StemMixerUiState,
  viewModel: StemSplitterViewModel,
) {
  val context = LocalContext.current
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Professional mixer", style = MaterialTheme.typography.titleLarge)
    Text(
      "One editable mix of your separated stems. Every control is real audio processing — volume, pan, 6-band EQ per stem + master EQ, mute, solo, loop/trim, speed/pitch — applied live and in the export.",
      style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(4.dp))

    // Mode + six-lane parity (StemDeck-style, implemented here; no StemDeck code used)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          PracticeMode.entries.forEach { mode ->
            if (mixer.practiceMode == mode) Button(onClick = { viewModel.setPracticeMode(mode) }) { Text(mode.label) }
            else OutlinedButton(onClick = { viewModel.setPracticeMode(mode) }) { Text(mode.label) }
          }
        }
        Text("Six lanes (vocals, drums, bass, guitar, piano, other)", style = MaterialTheme.typography.titleSmall)
        StemDeckParity.sixStems.forEach { stem ->
          Text(
            stem.label + " — " + stem.routingNote,
            style = MaterialTheme.typography.bodySmall,
            color = if (stem.isSeparatelySeparated) Color.Unspecified else Color.Gray,
          )
        }
        if (!mixer.practiceMode.showsAdvancedControls) {
          Text("Practice mode hides EQ, saved presets and loudness. Switch to Advanced to use them.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
      }
    }
    Spacer(Modifier.height(4.dp))

    // MASTER BUS
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Master bus", style = MaterialTheme.typography.titleMedium)

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = viewModel::playPauseMixer,
            enabled = mixer.ready,
          ) { Text(if (mixer.playing) "Pause" else "Play mix") }
          OutlinedButton(
            onClick = { viewModel.setSourceMode(!mixer.master.originalOnly) },
            enabled = mixer.ready,
          ) {
            Text(if (mixer.master.originalOnly) "Back to mix" else "A/B original")
          }
          Text(
            formatTime(mixer.positionSec) + " / " + (if (mixer.durationSec > 0) formatTime(mixer.durationSec) else "--:--"),
            style = MaterialTheme.typography.bodySmall,
          )
        }

        Slider(
          value = mixer.positionSec,
          onValueChange = { viewModel.seekMixer(it) },
          valueRange = 0f..maxOf(1f, mixer.durationSec),
          enabled = mixer.ready,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Speed ${"%.2f".format(mixer.master.speed)}×", modifier = Modifier.fillMaxWidth(0.18f))
          Slider(value = mixer.master.speed, onValueChange = viewModel::setSpeed, valueRange = 0.5f..2f, enabled = mixer.ready, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Pitch ${"%.2f".format(mixer.master.pitch)}×", modifier = Modifier.fillMaxWidth(0.18f))
          Slider(value = mixer.master.pitch, onValueChange = viewModel::setPitch, valueRange = 0.5f..2f, enabled = mixer.ready, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val semis = mixer.transposeSemitones
          Text("Key ${if (semis > 0) "+" else ""}$semis st", modifier = Modifier.fillMaxWidth(0.18f))
          Slider(value = semis.toFloat(), onValueChange = { viewModel.setTransposeSemitones(it.roundToInt()) }, valueRange = -12f..12f, steps = 24, enabled = mixer.ready, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Master ${"%.1f".format(mixer.master.masterGainDb)} dB", modifier = Modifier.fillMaxWidth(0.2f))
          Slider(value = mixer.master.masterGainDb, onValueChange = viewModel::setMasterGain, valueRange = -40f..12f, enabled = mixer.ready, modifier = Modifier.fillMaxWidth())
        }

        // Master EQ
        Text("Master EQ", style = MaterialTheme.typography.titleSmall)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          StemMixPresets.masterPresets().forEach { preset ->
            OutlinedButton(
              onClick = { viewModel.applyMasterPreset(preset) },
              enabled = mixer.ready,
            ) { Text(preset.name) }
          }
        }
        mixer.master.eqBands.forEachIndexed { index, band ->
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatFreq(band.frequency), modifier = Modifier.fillMaxWidth(0.18f))
            Slider(value = band.gainDb.toFloat(), onValueChange = { viewModel.setMasterEqBand(index, it.toDouble()) }, valueRange = -12f..12f, enabled = mixer.ready, modifier = Modifier.fillMaxWidth())
            Text("${band.gainDb} dB", style = MaterialTheme.typography.bodySmall)
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Q ${"%.2f".format(band.q)}", modifier = Modifier.fillMaxWidth(0.18f))
            Slider(value = band.q.toFloat(), onValueChange = { viewModel.setMasterEqBandQ(index, it.toDouble()) }, valueRange = 0.5f..4f, enabled = mixer.ready, modifier = Modifier.fillMaxWidth())
          }
        }

        // Loop / trim
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
          Text("Loop selection")
          Switch(checked = mixer.master.loopEnabled, onCheckedChange = viewModel::setLoopEnabled, enabled = mixer.ready)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Trim start ${"%.1f".format(mixer.master.trimStartSec)}s", modifier = Modifier.fillMaxWidth(0.28f))
          Slider(value = mixer.master.trimStartSec, onValueChange = { viewModel.setMasterTrim(it, mixer.master.trimEndSec) }, valueRange = 0f..maxOf(1f, mixer.durationSec), enabled = mixer.ready, modifier = Modifier.fillMaxWidth())
        }
        val endValue = mixer.master.trimEndSec ?: mixer.durationSec
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Trim end ${"%.1f".format(endValue)}s", modifier = Modifier.fillMaxWidth(0.28f))
          Slider(value = endValue, onValueChange = { viewModel.setMasterTrim(mixer.master.trimStartSec, it) }, valueRange = maxOf(0f, mixer.master.trimStartSec)..maxOf(1f, mixer.durationSec), enabled = mixer.ready, modifier = Modifier.fillMaxWidth())
        }

        // Export
        Text("Export mix", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          StemExportFormat.entries.forEach { format ->
            val selected = mixer.exportFormat == format
            if (selected) Button(onClick = { viewModel.setExportFormat(format) }) { Text(format.name) }
            else OutlinedButton(onClick = { viewModel.setExportFormat(format) }) { Text(format.name) }
          }
        }
        val qualities = if (mixer.exportFormat == StemExportFormat.WAV) listOf(StemExportQuality.WAV_16BIT) else listOf(StemExportQuality.MP3_320, StemExportQuality.MP3_192, StemExportQuality.MP3_128)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          qualities.forEach { q ->
            if (mixer.exportQuality == q) Button(onClick = { viewModel.setExportQuality(q) }, enabled = !mixer.exporting) { Text(q.name) }
            else OutlinedButton(onClick = { viewModel.setExportQuality(q) }, enabled = !mixer.exporting) { Text(q.name) }
          }
        }
        if (mixer.practiceMode.showsAdvancedControls) {
          Text("Loudness target", style = MaterialTheme.typography.titleSmall)
          Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LoudnessTarget.entries.forEach { target ->
              if (mixer.loudness == target) Button(onClick = { viewModel.setLoudness(target) }) { Text(target.label) }
              else OutlinedButton(onClick = { viewModel.setLoudness(target) }) { Text(target.label) }
            }
          }
        }
        Text("Click + count-in (mixed into exports)", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
          Text("Click enabled")
          Switch(checked = mixer.click.enabled, onCheckedChange = viewModel::setClickEnabled, enabled = mixer.ready)
        }
        if (mixer.click.enabled) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BPM ${mixer.click.bpm}", modifier = Modifier.fillMaxWidth(0.22f))
            Slider(value = mixer.click.bpm.toFloat(), onValueChange = { viewModel.setClickBpm(it.roundToInt()) }, valueRange = 40f..240f, enabled = mixer.ready, modifier = Modifier.fillMaxWidth())
          }
          Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(2, 3, 4, 6).forEach { beats ->
              if (mixer.click.beatsPerBar == beats) Button(onClick = { viewModel.setClickBeatsPerBar(beats) }) { Text("$beats/4") }
              else OutlinedButton(onClick = { viewModel.setClickBeatsPerBar(beats) }) { Text("$beats/4") }
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Count-in ${mixer.click.countInBars} bar(s)", modifier = Modifier.fillMaxWidth(0.35f))
            Slider(value = mixer.click.countInBars.toFloat(), onValueChange = { viewModel.setClickCountInBars(it.roundToInt()) }, valueRange = 0f..4f, enabled = mixer.ready, modifier = Modifier.fillMaxWidth())
          }
          Text("Click is rendered into the exported mix only. Live metronome needs a beat grid, which RajTune does not have.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Button(onClick = viewModel::export, enabled = mixer.ready && !mixer.exporting, modifier = Modifier.fillMaxWidth()) {
          Text(if (mixer.exporting) "Exporting…" else "Export mix")
        }
        OutlinedButton(onClick = viewModel::exportZip, enabled = mixer.ready && !mixer.exporting, modifier = Modifier.fillMaxWidth()) {
          Text("Export stems as ZIP")
        }
        if (mixer.exporting) {
          LinearProgressIndicator(progress = { mixer.exportProgress / 100f }, modifier = Modifier.fillMaxWidth())
          Text(mixer.exportLabel + " — ${mixer.exportProgress}%", style = MaterialTheme.typography.bodySmall)
        }
        mixer.exportError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        mixer.lastExportUri?.let { uri ->
          OutlinedButton(
            onClick = {
              val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (mixer.exportFormat == StemExportFormat.MP3) "audio/mpeg" else "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
              runCatching { context.startActivity(Intent.createChooser(intent, "Share RajTune mix")) }
            },
            modifier = Modifier.fillMaxWidth(),
          ) { Text("Share exported mix") }
          Text("Saved in Music/RajTune Stems", style = MaterialTheme.typography.bodySmall)
        }
        if (mixer.lastSavedAt > 0) {
          Text("Mixer auto-saves after every change", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
      }
    }

    // PER-STEM
    mixer.stems.values.forEach { settings ->
      val peaks = mixer.waveforms[settings.stemId]
      StemChannelCard(
        settings = settings,
        peaks = peaks,
        positionSec = mixer.positionSec,
        durationSec = mixer.durationSec,
        enabled = mixer.ready,
        onGain = { viewModel.setStemGain(settings.stemId, it) },
        onPan = { viewModel.setStemPan(settings.stemId, it) },
        onMute = { viewModel.toggleStemMute(settings.stemId) },
        onSolo = { viewModel.toggleStemSolo(settings.stemId) },
        onEqBand = { index, gain -> viewModel.setStemEqBand(settings.stemId, index, gain) },
        onEqBandQ = { index, q -> viewModel.setStemEqBandQ(settings.stemId, index, q) },
        onPreset = { preset -> viewModel.applyStemPreset(settings.stemId, preset) },
        onSavePreset = { name -> viewModel.saveStemEqPreset(name, settings.stemId) },
        onApplySavedPreset = { key -> viewModel.applySavedStemPreset(key) },
        onLoop = { loop -> viewModel.setStemLoop(settings.stemId, loop) },
        savedPresets = mixer.savedPresets,
        practiceMode = mixer.practiceMode,
      )
    }
  }
}

@Composable
private fun StemChannelCard(
  settings: StemMixSettings,
  peaks: StemWaveformPeaks.Peaks?,
  positionSec: Float,
  durationSec: Float,
  enabled: Boolean,
  practiceMode: PracticeMode = PracticeMode.Advanced,
  onGain: (Float) -> Unit,
  onPan: (Float) -> Unit,
  onMute: () -> Unit,
  onSolo: () -> Unit,
  onEqBand: (Int, Double) -> Unit,
  onEqBandQ: (Int, Double) -> Unit,
  onPreset: (StemEqPreset) -> Unit,
  onSavePreset: (String) -> Unit,
  onApplySavedPreset: (String) -> Unit,
  onLoop: (Boolean) -> Unit,
  savedPresets: Map<String, SavedPreset>,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(settings.stemId.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(0.35f))
        OutlinedButton(onClick = onMute, enabled = enabled) { Text(if (settings.muted) "Unmute" else "Mute") }
        OutlinedButton(onClick = onSolo, enabled = enabled) { Text(if (settings.soloed) "Unsolo" else "Solo") }
      }
      if (peaks != null) {
        WaveformCanvas(peaks = peaks, progress = if (durationSec > 0) positionSec / durationSec else 0f)
        Text(
          "Level: ${"%.0f".format((peaks.max.maxOrNull() ?: 0f) * 100)}%",
          style = MaterialTheme.typography.bodySmall,
          color = Color.Gray,
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Volume ${"%.1f".format(settings.gainDb)} dB", modifier = Modifier.fillMaxWidth(0.3f))
        Slider(value = settings.gainDb, onValueChange = onGain, valueRange = -40f..12f, enabled = enabled, modifier = Modifier.fillMaxWidth())
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pan ${formatPan(settings.pan)}", modifier = Modifier.fillMaxWidth(0.3f))
        Slider(value = settings.pan, onValueChange = onPan, valueRange = -1f..1f, enabled = enabled, modifier = Modifier.fillMaxWidth())
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Loop stem")
        Switch(checked = settings.loopEnabled, onCheckedChange = onLoop, enabled = enabled)
      }

      // EQ (Advanced only)
      if (practiceMode.showsAdvancedControls) {
      Text("Equalizer", style = MaterialTheme.typography.titleSmall)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        StemMixPresets.presetsFor(settings.stemId).forEach { preset ->
          OutlinedButton(onClick = { onPreset(preset) }, enabled = enabled) { Text(preset.name) }
        }
      }
      settings.eqBands.forEachIndexed { index, band ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(formatFreq(band.frequency), modifier = Modifier.fillMaxWidth(0.18f))
          Slider(value = band.gainDb.toFloat(), onValueChange = { onEqBand(index, it.toDouble()) }, valueRange = -12f..12f, enabled = enabled, modifier = Modifier.fillMaxWidth())
          Text("${band.gainDb} dB", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Q ${"%.2f".format(band.q)}", modifier = Modifier.fillMaxWidth(0.18f))
          Slider(value = band.q.toFloat(), onValueChange = { onEqBandQ(index, it.toDouble()) }, valueRange = 0.5f..4f, enabled = enabled, modifier = Modifier.fillMaxWidth())
        }
      }
      val savedForThisStem = savedPresets.entries.filter { it.value.stemId == settings.stemId.id }
      if (savedForThisStem.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          savedForThisStem.forEach { (key, _) ->
            OutlinedButton(onClick = { onApplySavedPreset(key) }, enabled = enabled) { Text(key.substringAfter(":")) }
          }
        }
      }
      OutlinedButton(onClick = { onSavePreset(settings.stemId.label.take(2).uppercase() + "-" + (settings.eqBands.hashCode() % 100).toString()) }, enabled = enabled) {
        Text("Save this EQ as preset")
      }
      } else {
        Text("Advanced EQ is hidden in Practice mode.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
      }
    }
  }
}

@Composable
private fun WaveformCanvas(peaks: StemWaveformPeaks.Peaks, progress: Float) {
  Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
    val count = peaks.max.size
    if (count == 0) return@Canvas
    val slot = size.width / count
    val midY = size.height / 2f
    val safeProgress = progress.coerceIn(0f, 1f)
    val playheadX = safeProgress * size.width
    for (i in 0 until count) {
      val amp = maxOf(kotlin.math.abs(peaks.min[i]), kotlin.math.abs(peaks.max[i]))
      val h = (amp * size.height).coerceAtLeast(1f)
      val x = i * slot
      val color = if (x <= playheadX) Color(0xFF334769) else Color(0xFF8FAFD4)
      drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(x, midY - h / 2), size = androidx.compose.ui.geometry.Size(slot * 0.8f, h))
    }
    drawLine(color = Color.Red, start = androidx.compose.ui.geometry.Offset(playheadX, 0f), end = androidx.compose.ui.geometry.Offset(playheadX, size.height), strokeWidth = 2f)
  }
}

private fun formatFreq(hz: Double): String = if (hz >= 1000) "${"%.1f".format(hz / 1000)} k" else "${hz.toInt()}"

private fun formatPan(pan: Float): String = when (pan) {
  in -1f..-0.9f -> "L100"
  in -0.9f..-0.5f -> "L${"%.0f".format(-pan * 100)}"
  in -0.05f..0.05f -> "C"
  in 0.05f..0.5f -> "R${"%.0f".format(pan * 100)}"
  else -> "R100"
}

private fun formatTime(sec: Float): String {
  val total = sec.toInt().coerceAtLeast(0)
  val m = total / 60
  val s = total % 60
  return "${m}:${"%02d".format(s)}"
}
