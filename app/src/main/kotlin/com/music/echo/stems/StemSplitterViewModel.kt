package echo.music.iad1tya.stems

import android.content.Context
import android.net.Uri
import android.media.AudioManager
import android.media.ToneGenerator
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
  val practiceMode: PracticeMode = PracticeMode.Advanced,
  val transposeSemitones: Int = 0,
  val click: ClickSettings = ClickSettings(),
  val loudness: LoudnessTarget = LoudnessTarget.None,
  val markers: List<StemMarker> = emptyList(),
  val library: List<StemLibraryEntry> = emptyList(),
  val queue: List<StemQueueItem> = emptyList(),
  val metronomeLive: Boolean = false,
  val liveBeat: LiveBeatState? = null,
  val manualKey: String = "",
  val manualTempo: Int? = null,
  val videoQuality: VideoExportQuality = VideoExportQuality.HD720,
  val stemPick: List<String> = listOf("vocals", "drums", "bass", "other"),
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
  private val tapTimes = ArrayDeque<Long>()
  private var lastTickBeat: Long = -1L
  private var toneGenerator: ToneGenerator? = null

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
      practiceMode = snapshot.practiceMode,
      transposeSemitones = snapshot.transposeSemitones,
      click = snapshot.click,
      loudness = snapshot.loudness,
      markers = snapshot.markers,
      library = snapshot.library,
      queue = snapshot.queue,
      metronomeLive = snapshot.metronomeLive,
      manualKey = snapshot.manualKey,
      manualTempo = snapshot.manualTempo,
      videoQuality = snapshot.videoQuality,
      stemPick = snapshot.stemPick,
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
          val liveState = if (m.metronomeLive) LiveBeatClock.stateAt(m.click, posSec, m.master.trimStartSec) else null
          if (shouldUpdate || liveState != m.liveBeat) _mixer.update { it.copy(positionSec = posSec, durationSec = dur, liveBeat = liveState) }
          // Live metronome audible tick (manual BPM only, no beat grid)
          if (m.metronomeLive && m.playing && liveState != null) {
            if (liveState.beatIndex != lastTickBeat) {
              lastTickBeat = liveState.beatIndex
              playTick(liveState.isDownbeat, m.click.volume)
            }
          }
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
    mixerPlayer.applySettings(effectiveStems(m), m.master)
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
      practiceMode = m.practiceMode,
      transposeSemitones = m.transposeSemitones,
      click = m.click,
      loudness = m.loudness,
      markers = m.markers,
      library = m.library,
      queue = m.queue,
      metronomeLive = m.metronomeLive,
      manualKey = m.manualKey,
      manualTempo = m.manualTempo,
      videoQuality = m.videoQuality,
      stemPick = m.stemPick,
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

  /**
   * Effective stem settings after applying the stem pick.
   * Non-picked stems are muted (without mutating stored settings, so
   * re-picking restores them). Used for live playback and mix/loop/video export.
   * ZIP export intentionally keeps all four separated files.
   */
  private fun effectiveStems(m: StemMixerUiState): Map<StemId, StemMixSettings> {
    val pickedIds = QueueLogic.normalizePick(m.stemPick).map { StemId.from(it) }.toSet()
    return m.stems.mapValues { (id, s) -> if (id in pickedIds) s else s.copy(muted = true) }
  }

  private fun applyPlayerSettings() {
    if (!_mixer.value.ready) return
    val m = _mixer.value
    mixerPlayer.applySettings(effectiveStems(m), m.master)
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
          settings = effectiveStems(m),
          master = m.master,
          format = m.exportFormat,
          quality = m.exportQuality,
          loudness = m.loudness,
          click = m.click,
        ) { label, percent ->
          _mixer.update { it.copy(exportLabel = label, exportProgress = percent) }
        }
        addToLibrary(results.firstOrNull()?.uri, "mix")
        _mixer.update { it.copy(lastExportUri = results.firstOrNull()?.uri, exporting = false, exportProgress = 100) }
      } catch (e: CancellationException) {
        _mixer.update { it.copy(exporting = false, exportError = "Export cancelled") }
        throw e
      } catch (e: Throwable) {
        _mixer.update { it.copy(exporting = false, exportError = e.message ?: "Export failed") }
      }
    }
  }

  // ---------- StemDeck-parity (offline-only) ----------

  fun setPracticeMode(mode: PracticeMode) {
    _mixer.update { it.copy(practiceMode = mode) }
    persist()
  }

  /**
   * Key transposition in semitones (-12..+12), mapped onto the existing
   * master pitch multiplier so live playback and export stay in sync.
   */
  fun setTransposeSemitones(semitones: Int) {
    val s = semitones.coerceIn(-12, 12)
    val pitch = StemDeckParity.semitonesToPitch(s)
    _mixer.update { it.copy(transposeSemitones = s, master = it.master.copy(pitch = pitch)) }
    applyPlayerSettings()
    persist()
  }

  fun setClickEnabled(enabled: Boolean) {
    _mixer.update { it.copy(click = it.click.copy(enabled = enabled)) }
    persist()
  }

  fun setClickBpm(bpm: Int) {
    _mixer.update { it.copy(click = it.click.copy(bpm = bpm.coerceIn(40, 240))) }
    persist()
  }

  fun setClickBeatsPerBar(beats: Int) {
    _mixer.update { it.copy(click = it.click.copy(beatsPerBar = beats)) }
    persist()
  }

  fun setClickCountInBars(bars: Int) {
    _mixer.update { it.copy(click = it.click.copy(countInBars = bars.coerceIn(0, 4))) }
    persist()
  }

  fun setClickVolume(volume: Float) {
    _mixer.update { it.copy(click = it.click.copy(volume = volume.coerceIn(0f, 1f))) }
    persist()
  }

  fun setLoudness(target: LoudnessTarget) {
    _mixer.update { it.copy(loudness = target) }
    persist()
  }

  // ---------- Advanced offline parity (metronome, markers, loop, queue, library, metadata, video) ----------

  fun setMetronomeLive(on: Boolean) {
    _mixer.update { it.copy(metronomeLive = on) }
    persist()
  }

  /** Tap tempo: caller should invoke on every tap; computes median BPM and sets click BPM. */
  fun tapTempo() {
    val now = System.currentTimeMillis()
    tapTimes.addLast(now)
    while (tapTimes.size > TapTempoCalculator.MAX_TAPS) tapTimes.removeFirst()
    // Drop taps outside the 3s window for responsiveness.
    while (tapTimes.isNotEmpty() && now - tapTimes.first() > TapTempoCalculator.WINDOW_MS) tapTimes.removeFirst()
    val bpm = TapTempoCalculator.bpmFromTaps(tapTimes.toList(), now)
    if (bpm != null) {
      _mixer.update { it.copy(click = it.click.copy(bpm = bpm), manualTempo = bpm) }
    }
    persist()
  }

  fun clearTapTempo() {
    tapTimes.clear()
    persist()
  }

  private fun playTick(downbeat: Boolean, volume: Float) {
    // ToneGenerator.startTone blocks, so run off the position-monitor thread.
    runCatching {
      Thread {
        runCatching {
          val vol = (volume.coerceIn(0f, 1f) * 100).toInt()
          if (toneGenerator == null) {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, vol)
          }
          val tg = toneGenerator ?: return@runCatching
          // Downbeat is a slightly longer tick so it is distinguishable.
          tg.startTone(ToneGenerator.TONE_CDMA_ANSWER, if (downbeat) 90 else 60)
        }
      }.apply { isDaemon = true; start() }
    }
  }

  fun addMarkerAtCurrent() {
    val pos = _mixer.value.positionSec
    val id = "m" + System.currentTimeMillis()
    val label = "Marker " + (_mixer.value.markers.size + 1) + " %.1fs".format(pos)
    val next = (_mixer.value.markers + StemMarker(id = id, timeSec = pos, label = label)).sortedBy { it.timeSec }
    _mixer.update { it.copy(markers = next) }
    persist()
  }

  fun removeMarker(id: String) {
    _mixer.update { it.copy(markers = it.markers.filter { m -> m.id != id }) }
    persist()
  }

  fun seekToMarker(marker: StemMarker) { seekMixer(marker.timeSec) }

  fun setLoopStartAtPosition() {
    val pos = _mixer.value.positionSec
    val cur = _mixer.value.master
    _mixer.update { it.copy(master = cur.copy(trimStartSec = pos.coerceAtLeast(0f), loopEnabled = true)) }
    persist()
  }

  fun setLoopEndAtPosition() {
    val pos = _mixer.value.positionSec
    val cur = _mixer.value.master
    _mixer.update { it.copy(master = cur.copy(trimEndSec = pos.coerceAtLeast(cur.trimStartSec), loopEnabled = true)) }
    persist()
  }

  fun clearLoop() {
    val cur = _mixer.value.master
    _mixer.update { it.copy(master = cur.copy(trimStartSec = 0f, trimEndSec = null, loopEnabled = false)) }
    persist()
  }

  fun setManualKey(key: String) {
    _mixer.update { it.copy(manualKey = key.take(8)) }
    persist()
  }

  fun setManualTempo(bpm: Int?) {
    _mixer.update { it.copy(manualTempo = bpm?.coerceIn(40, 240)) }
    persist()
  }

  fun setVideoQuality(q: VideoExportQuality) {
    _mixer.update { it.copy(videoQuality = q) }
    persist()
  }

  fun setStemPick(pick: List<String>) {
    _mixer.update { it.copy(stemPick = QueueLogic.normalizePick(pick)) }
    applyPlayerSettings()
    persist()
  }

  fun addToQueue(uriString: String, name: String) {
    val item = StemQueueItem(uriString = uriString, name = name, pickedStemIds = _mixer.value.stemPick)
    _mixer.update { it.copy(queue = it.queue + item) }
    persist()
  }

  fun removeFromQueue(index: Int) {
    _mixer.update { it.copy(queue = it.queue.filterIndexed { i, _ -> i != index }) }
    persist()
  }

  fun clearQueue() {
    _mixer.update { it.copy(queue = emptyList()) }
    persist()
  }

  fun toggleFavoriteLibrary(displayName: String) {
    _mixer.update { it.copy(library = LibraryLogic.toggleFavorite(it.library, displayName)) }
    persist()
  }

  fun trashLibrary(displayName: String) {
    _mixer.update { it.copy(library = LibraryLogic.trashByName(it.library, displayName)) }
    persist()
  }

  private fun addToLibrary(uri: android.net.Uri?, kind: String) {
    val u = uri?.toString() ?: return
    val name = runCatching {
      context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: "RajTune export"
    val entry = StemLibraryEntry(displayName = name, uriString = u, kind = kind)
    _mixer.update { it.copy(library = (it.library + entry).takeLast(100)) }
    persist()
  }

  /** Export only the current loop/trim range (honest trim export; length = end-start). */
  fun exportLoop() {
    val outputs = _state.value.outputs
    val m = _mixer.value
    if (_state.value.cloudMode) {
      _mixer.update { it.copy(exportError = "Loop export is offline-only. Turn off Cloud.") }
      return
    }
    if (outputs.isEmpty() || m.exporting) return
    if (m.master.trimEndSec == null) {
      _mixer.update { it.copy(exportError = "Set an A-B loop first (Set A, then Set B), then export loop.") }
      return
    }
    viewModelScope.launch {
      _mixer.update { it.copy(exporting = true, exportError = null, exportProgress = 0) }
      try {
        val results = exporter.exportMix(outputs, _state.value.sourceName ?: "Mix", effectiveStems(m), m.master, m.exportFormat, m.exportQuality, m.loudness, m.click) { label, percent ->
          _mixer.update { it.copy(exportLabel = label, exportProgress = percent) }
        }
        addToLibrary(results.firstOrNull()?.uri, "mix")
        _mixer.update { it.copy(lastExportUri = results.firstOrNull()?.uri, exporting = false, exportProgress = 100) }
      } catch (e: CancellationException) {
        _mixer.update { it.copy(exporting = false, exportError = "Loop export cancelled") }
        throw e
      } catch (e: Throwable) {
        _mixer.update { it.copy(exporting = false, exportError = e.message ?: "Loop export failed") }
      }
    }
  }

  /** Video export: mixed audio + solid-colour canvas (720p/1080p), offline via FFmpeg. */
  fun exportVideo() {
    val outputs = _state.value.outputs
    val m = _mixer.value
    if (_state.value.cloudMode) {
      _mixer.update { it.copy(exportError = "Video export is offline-only. Turn off Cloud.") }
      return
    }
    if (outputs.isEmpty() || m.exporting) return
    viewModelScope.launch {
      _mixer.update { it.copy(exporting = true, exportError = null, exportProgress = 0) }
      try {
        val results = exporter.exportVideo(outputs, _state.value.sourceName ?: "Mix", effectiveStems(m), m.master, m.videoQuality, m.loudness, m.click) { label, percent ->
          _mixer.update { it.copy(exportLabel = label, exportProgress = percent) }
        }
        addToLibrary(results.firstOrNull()?.uri, "video")
        _mixer.update { it.copy(lastExportUri = results.firstOrNull()?.uri, exporting = false, exportProgress = 100) }
      } catch (e: CancellationException) {
        _mixer.update { it.copy(exporting = false, exportError = "Video export cancelled") }
        throw e
      } catch (e: Throwable) {
        _mixer.update { it.copy(exporting = false, exportError = e.message ?: "Video export failed") }
      }
    }
  }

  fun exportZip() {
    val outputs = _state.value.outputs
    if (_state.value.cloudMode) {
      _mixer.update { it.copy(exportError = "Stem ZIP is offline-only. Turn off Cloud to export on this device.") }
      return
    }
    if (outputs.isEmpty() || _mixer.value.exporting) return
    viewModelScope.launch {
      _mixer.update { it.copy(exporting = true, exportError = null, exportProgress = 0) }
      try {
        val results = exporter.exportZip(
          outputs = outputs,
          sourceTitle = _state.value.sourceName ?: "Mix",
        ) { label, percent ->
          _mixer.update { it.copy(exportLabel = label, exportProgress = percent) }
        }
        addToLibrary(results.firstOrNull()?.uri, "zip")
        _mixer.update { it.copy(lastExportUri = results.firstOrNull()?.uri, exporting = false, exportProgress = 100) }
      } catch (e: CancellationException) {
        _mixer.update { it.copy(exporting = false, exportError = "ZIP export cancelled") }
        throw e
      } catch (e: Throwable) {
        _mixer.update { it.copy(exporting = false, exportError = e.message ?: "ZIP export failed") }
      }
    }
  }

  override fun onCleared() {
    runCatching { toneGenerator?.release() }
    positionMonitor?.cancel()
    mixerPlayer.close()
    super.onCleared()
  }
}
