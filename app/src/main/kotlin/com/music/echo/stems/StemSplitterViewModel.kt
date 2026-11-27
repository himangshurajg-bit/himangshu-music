package echo.music.iad1tya.stems

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal data class StemSplitterUiState(
  val sourceUri: Uri? = null,
  val sourceName: String? = null,
  val label: String = "Choose an audio file to begin",
  val percent: Int = 0,
  val running: Boolean = false,
  val outputs: List<SeparatedStem> = emptyList(),
  val error: String? = null,
  val cloudMode: Boolean = false,
  val cloudEndpoint: String = "",
  val cloudToken: String = "",
  val cloudStatus: String = "",
)

data class StemMixerUiState(
  val stems: Map<StemId, StemMixSettings> = StemId.entries.associateWith { StemMixSettings(it) },
  val master: MasterMixSettings = MasterMixSettings(),
  val waveforms: Map<StemId, StemWaveformPeaks.Peaks> = emptyMap(),
  val ready: Boolean = false,
  val playing: Boolean = false,
  val positionSec: Float = 0f,
  val durationSec: Float = 0f,
  val exporting: Boolean = false,
  val exportProgress: Int = 0,
  val exportLabel: String = "",
  val lastExportUri: Uri? = null,
  val exportFormat: StemExportFormat = StemExportFormat.WAV,
  val exportQuality: StemExportQuality = StemExportQuality.WAV_16BIT,
  val exportError: String? = null,
  val savedPresets: Map<String, SavedPreset> = emptyMap(),
  val lastSavedAt: Long = 0L,
)

@HiltViewModel
internal class StemSplitterViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val engine: StemSeparationEngine,
  private val store: StemProjectStore,
  private val exporter: StemExportRenderer,
) : ViewModel() {
  private val _state = MutableStateFlow(StemSplitterUiState())
  val state = _state.asStateFlow()

  private val _mixer = MutableStateFlow(StemMixerUiState())
  val mixerState = _mixer.asStateFlow()

  private var job: Job? = null
  private val mixerPlayer = StemMixerPlayer(context)
  private var positionMonitor: Job? = null

  init {
    viewModelScope.launch { loadMixerSnapshot() }
    startPositionMonitor()
  }

  private suspend fun loadMixerSnapshot() {
    val snapshot = store.load()
    _mixer.value = _mixer.value.copy(
      stems = snapshot.stems,
      master = snapshot.master,
      savedPresets = snapshot.savedPresets,
      lastSavedAt = snapshot.lastSavedAtEpochMs,
    )
    if (_state.value.outputs.isNotEmpty()) rebuildTracks()
  }

  private fun startPositionMonitor() {
    if (positionMonitor != null) return
    positionMonitor = viewModelScope.launch {
      while (true) {
        val m = _mixer.value
        if (m.ready) {
          val posSec = mixerPlayer.currentPositionSec()
          val dur = mixerPlayer.durationMs().let { if (it > 0) it / 1000f else m.durationSec }
          val shouldUpdate = posSec != m.positionSec || dur != m.durationSec
          if (shouldUpdate) _mixer.update { it.copy(positionSec = posSec, durationSec = dur) }
          // Loop / trim handling
          val master = m.master
          val loopEnd = master.trimEndSec ?: dur.takeIf { it > 0f }
          if (master.loopEnabled && loopEnd != null && posSec >= loopEnd && loopEnd > master.trimStartSec) {
            mixerPlayer.seekToSec(master.trimStartSec)
          } else if (!master.loopEnabled && loopEnd != null && posSec >= loopEnd && _mixer.value.playing) {
            pauseMixer()
          }
        }
        delay(200)
      }
    }
  }

  private fun rebuildTracks() {
    val outputs = _state.value.outputs
    val m = _mixer.value
    val sourceUri = _state.value.sourceUri
    if (outputs.isEmpty()) return
    if (_state.value.cloudMode) {
      mixerPlayer.releaseAllPlayers()
      _mixer.update { it.copy(ready = false, playing = false, waveforms = emptyMap()) }
      return
    }
    mixerPlayer.setTracks(outputs, sourceUri, m.master)
    mixerPlayer.applySettings(m.stems, m.master)
    mixerPlayer.setOriginalOnly(m.master.originalOnly)
    mixerPlayer.pause()
    // Load waveforms in IO
    viewModelScope.launch(Dispatchers.IO) {
      val map = HashMap<StemId, StemWaveformPeaks.Peaks>()
      for (output in outputs) {
        val peaks = StemWaveformPeaks.loadFromUri(context, output.uri) ?: continue
        map[StemId.from(output.name)] = peaks
      }
      _mixer.update { it.copy(waveforms = map, ready = true, durationSec = getDurationFromWaveform(map) ?: it.durationSec) }
    }
    _mixer.update { it.copy(ready = true) }
  }

  private fun getDurationFromWaveform(map: Map<StemId, StemWaveformPeaks.Peaks>): Float? = null

  private fun persist() {
    val m = _mixer.value
    val snapshot = StemMixerSnapshot(
      stems = m.stems,
      master = m.master,
      savedPresets = m.savedPresets,
      lastSavedAtEpochMs = System.currentTimeMillis(),
    )
    viewModelScope.launch { store.save(snapshot) }
    _mixer.update { it.copy(lastSavedAt = snapshot.lastSavedAtEpochMs) }
  }

  fun select(uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    val name = runCatching {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: "Selected audio"
    _state.value = StemSplitterUiState(sourceUri = uri, sourceName = name, label = name, cloudMode = _state.value.cloudMode, cloudEndpoint = _state.value.cloudEndpoint, cloudToken = _state.value.cloudToken)
  }

  fun setCloudMode(on: Boolean) {
    _state.update { it.copy(cloudMode = on) }
    if (on) {
      mixerPlayer.releaseAllPlayers()
      _mixer.update { it.copy(ready = false, playing = false) }
    }
  }
  fun setCloudEndpoint(v: String) { _state.update { it.copy(cloudEndpoint = v) } }
  fun setCloudToken(v: String) { _state.update { it.copy(cloudToken = v) } }

  fun start() {
    val current = _state.value
    val uri = current.sourceUri ?: return
    if (current.running) return
    if (!current.cloudMode) {
      job = viewModelScope.launch {
        _state.value = current.copy(running = true, percent = 0, outputs = emptyList(), error = null, label = "Starting separation")
        try {
          val outputs = engine.separate(uri, current.sourceName ?: "Audio") { update ->
            _state.value = _state.value.copy(label = update.label, percent = update.percent)
          }
          _state.value = _state.value.copy(running = false, percent = 100, label = "Four stems saved to Music/RajTune Stems", outputs = outputs)
          rebuildTracks()
        } catch (cancelled: CancellationException) {
          _state.value = _state.value.copy(running = false, label = "Separation cancelled", percent = 0)
          throw cancelled
        } catch (error: Throwable) {
          _state.value = _state.value.copy(running = false, label = "Separation failed", error = error.message ?: "Unexpected audio processing error")
        }
      }
    } else {
      startCloud()
    }
  }

  fun startCloud() {
    val current = _state.value
    val uri = current.sourceUri ?: return
    if (current.running) return
    if (current.cloudEndpoint.isBlank()) {
      _state.value = current.copy(error = "Cloud not configured - Offline mode only")
      return
    }
    job = viewModelScope.launch {
      _state.value = current.copy(running = true, percent = 0, outputs = emptyList(), error = null, cloudStatus = "Uploading audio to cloud")
      try {
        val outputs = StemCloudClient(context).separateCloud(current.cloudEndpoint, current.cloudToken, uri, current.sourceName ?: "Audio") { update ->
          _state.value = _state.value.copy(label = update.label, percent = update.percent, cloudStatus = update.label)
        }
        _state.value = _state.value.copy(running = false, percent = 100, label = "Cloud stems saved to Music/RajTune Stems", outputs = outputs, cloudStatus = "Complete")
        rebuildTracks()
      } catch (cancelled: CancellationException) {
        _state.value = _state.value.copy(running = false, label = "Cloud separation cancelled", percent = 0, cloudStatus = "Cancelled")
        throw cancelled
      } catch (error: Throwable) {
        _state.value = _state.value.copy(running = false, label = "Cloud separation failed", error = error.message ?: "Cloud processing error", cloudStatus = "Failed")
      }
    }
  }

  fun cancel() {
    job?.cancel()
    job = null
  }

  // ---------- Mixer operations ----------

  fun setSourceMode(original: Boolean) {
    _mixer.update { it.copy(master = it.master.copy(originalOnly = original)) }
    mixerPlayer.setOriginalOnly(original)
    persist()
  }

  fun playPauseMixer() {
    val m = _mixer.value
    if (!m.ready) return
    val nowPlaying = !m.playing
    if (nowPlaying) mixerPlayer.play() else mixerPlayer.pause()
    _mixer.update { it.copy(playing = nowPlaying) }
  }

  fun pauseMixer() {
    if (!_mixer.value.ready) return
    mixerPlayer.pause()
    _mixer.update { it.copy(playing = false) }
  }

  fun seekMixer(seconds: Float) {
    if (!_mixer.value.ready) return
    mixerPlayer.seekToSec(seconds)
    _mixer.update { it.copy(positionSec = seconds) }
  }

  fun setStemGain(stemId: StemId, gainDb: Float) {
    _mixer.update { s -> s.copy(stems = s.stems.toMutableMap().apply { this[stemId] = (this[stemId] ?: StemMixSettings(stemId)).copy(gainDb = gainDb) }) }
    applyPlayerSettings()
    persist()
  }

  fun setStemPan(stemId: StemId, pan: Float) {
    _mixer.update { s -> s.copy(stems = s.stems.toMutableMap().apply { this[stemId] = (this[stemId] ?: StemMixSettings(stemId)).copy(pan = pan) }) }
    applyPlayerSettings()
    persist()
  }

  fun toggleStemMute(stemId: StemId) {
    _mixer.update { s -> s.copy(stems = s.stems.toMutableMap().apply { val cur = this[stemId] ?: StemMixSettings(stemId); this[stemId] = cur.copy(muted = !cur.muted) }) }
    applyPlayerSettings()
    persist()
  }

  fun toggleStemSolo(stemId: StemId) {
    _mixer.update { s -> s.copy(stems = s.stems.toMutableMap().apply { val cur = this[stemId] ?: StemMixSettings(stemId); this[stemId] = cur.copy(soloed = !cur.soloed) }) }
    applyPlayerSettings()
    persist()
  }

  fun setStemEqBand(stemId: StemId, bandIndex: Int, gainDb: Double) {
    _mixer.update { s ->
      val cur = s.stems[stemId] ?: StemMixSettings(stemId)
      val bands = cur.eqBands.toMutableList()
      if (bandIndex in bands.indices) {
        bands[bandIndex] = bands[bandIndex].copy(gainDb = gainDb)
      }
      s.copy(stems = s.stems.toMutableMap().apply { this[stemId] = cur.copy(eqBands = bands) })
    }
    applyPlayerSettings()
    persist()
  }

  fun applyStemPreset(stemId: StemId, preset: StemEqPreset) {
    _mixer.update { s -> s.copy(stems = s.stems.toMutableMap().apply { this[stemId] = (this[stemId] ?: StemMixSettings(stemId)).copy(eqBands = preset.eqBands) }) }
    applyPlayerSettings()
    persist()
  }

  fun saveStemEqPreset(name: String, stemId: StemId) {
    viewModelScope.launch {
      val key = "${stemId.id}:$name"
      val cur = _mixer.value.stems[stemId] ?: StemMixSettings(stemId)
      store.saveEqPreset(key, stemId.id, cur.eqBands)
      val fresh = store.load()
      _mixer.update { it.copy(savedPresets = fresh.savedPresets) }
    }
  }

  fun applySavedStemPreset(key: String) {
    val preset = _mixer.value.savedPresets[key] ?: return
    val stemId = StemId.from(preset.stemId)
    _mixer.update { s -> s.copy(stems = s.stems.toMutableMap().apply { this[stemId] = (this[stemId] ?: StemMixSettings(stemId)).copy(eqBands = preset.bands) }) }
    applyPlayerSettings()
    persist()
  }

  fun setStemTrim(stemId: StemId, startSec: Float?, endSec: Float?) {
    _mixer.update { s -> s.copy(stems = s.stems.toMutableMap().apply { this[stemId] = (this[stemId] ?: StemMixSettings(stemId)).withTrim(startSec, endSec) }) }
    persist()
  }

  fun setStemLoop(stemId: StemId, loop: Boolean) {
    _mixer.update { s -> s.copy(stems = s.stems.toMutableMap().apply { this[stemId] = (this[stemId] ?: StemMixSettings(stemId)).copy(loopEnabled = loop) }) }
    persist()
  }

  fun setStemEqBandQ(stemId: StemId, bandIndex: Int, q: Double) {
    _mixer.update { s ->
      val cur = s.stems[stemId] ?: StemMixSettings(stemId)
      val bands = cur.eqBands.toMutableList()
      if (bandIndex in bands.indices) {
        bands[bandIndex] = bands[bandIndex].copy(q = q.coerceIn(0.5, 10.0))
      }
      s.copy(stems = s.stems.toMutableMap().apply { this[stemId] = cur.copy(eqBands = bands) })
    }
    applyPlayerSettings()
    persist()
  }

  fun setMasterEqBandQ(index: Int, q: Double) {
    _mixer.update { s ->
      val bands = s.master.eqBands.toMutableList()
      if (index in bands.indices) bands[index] = bands[index].copy(q = q.coerceIn(0.5, 10.0))
      s.copy(master = s.master.copy(eqBands = bands))
    }
    applyPlayerSettings()
    persist()
  }

  fun setMasterEqBand(index: Int, gainDb: Double) {
    _mixer.update { s ->
      val bands = s.master.eqBands.toMutableList()
      if (index in bands.indices) bands[index] = bands[index].copy(gainDb = gainDb)
      s.copy(master = s.master.copy(eqBands = bands))
    }
    applyPlayerSettings()
    persist()
  }

  fun applyMasterPreset(preset: StemEqPreset) {
    _mixer.update { it.copy(master = it.master.copy(eqBands = preset.eqBands)) }
    applyPlayerSettings()
    persist()
  }

  fun setMasterGain(gainDb: Float) {
    _mixer.update { it.copy(master = it.master.copy(masterGainDb = gainDb)) }
    applyPlayerSettings()
    persist()
  }

  fun setSpeed(speed: Float) {
    _mixer.update { it.copy(master = it.master.copy(speed = speed.coerceIn(0.5f, 2f))) }
    applyPlayerSettings()
    persist()
  }

  fun setPitch(pitch: Float) {
    _mixer.update { it.copy(master = it.master.copy(pitch = pitch.coerceIn(0.5f, 2f))) }
    applyPlayerSettings()
    persist()
  }

  fun setMasterTrim(startSec: Float, endSec: Float?) {
    _mixer.update { it.copy(master = it.master.copy(trimStartSec = startSec.coerceIn(0f, 3600f), trimEndSec = endSec)) }
    persist()
  }

  fun setLoopEnabled(loop: Boolean) {
    _mixer.update { it.copy(master = it.master.copy(loopEnabled = loop)) }
    persist()
  }

  fun setExportFormat(format: StemExportFormat) {
    _mixer.update { it.copy(exportFormat = format, exportQuality = if (format == StemExportFormat.WAV) StemExportQuality.WAV_16BIT else StemExportQuality.MP3_192) }
  }

  fun setExportQuality(q: StemExportQuality) {
    _mixer.update { it.copy(exportQuality = q) }
  }

  private fun applyPlayerSettings() {
    if (!_mixer.value.ready) return
    val m = _mixer.value
    mixerPlayer.applySettings(m.stems, m.master)
  }

  fun export() {
    val outputs = _state.value.outputs
    val m = _mixer.value
    if (_state.value.cloudMode) {
      _mixer.update { it.copy(exportError = "Mixer/EQ export is offline-only. Turn off Cloud to mix on this device.") }
      return
    }
    if (outputs.isEmpty() || m.exporting) return
    viewModelScope.launch {
      _mixer.update { it.copy(exporting = true, exportError = null, exportProgress = 0) }
      try {
        val results = exporter.exportMix(
          outputs = outputs,
          sourceTitle = _state.value.sourceName ?: "Mix",
          settings = m.stems,
          master = m.master,
          format = m.exportFormat,
          quality = m.exportQuality,
        ) { label, percent ->
          _mixer.update { it.copy(exportLabel = label, exportProgress = percent) }
        }
        _mixer.update { it.copy(lastExportUri = results.firstOrNull()?.uri, exporting = false, exportProgress = 100) }
      } catch (e: CancellationException) {
        _mixer.update { it.copy(exporting = false, exportError = "Export cancelled") }
        throw e
      } catch (e: Throwable) {
        _mixer.update { it.copy(exporting = false, exportError = e.message ?: "Export failed") }
      }
    }
  }

  override fun onCleared() {
    positionMonitor?.cancel()
    mixerPlayer.close()
    super.onCleared()
  }
}
