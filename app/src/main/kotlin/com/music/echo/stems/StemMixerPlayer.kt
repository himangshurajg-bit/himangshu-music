package echo.music.iad1tya.stems

import android.content.Context
import androidx.media3.common.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import echo.music.dsp.audio.CustomEqualizerAudioProcessor
import echo.music.dsp.core.models.ParametricEQ
import echo.music.dsp.core.models.ParametricEQBand

/**
 * Live professional mixer: one ExoPlayer per stem, each with its own
 * StemGainPanAudioProcessor + CustomEqualizerAudioProcessor.
 *
 * Volume is in the gain/pan processor (no Player.volume because on Android 13+ it
 * routes through a shared session), EQ is the same biquad processor the main
 * app uses, and master EQ is applied per stem (mathematically equivalent for the
 * linear filters used here, and the only way to get a "post-mix" EQ without a
 * summed intermediate bus).
 *
 * All players are created with handleAudioFocus=false so they can play
 * together; the VM requests a single audio focus grant for the whole mixer.
 */
@UnstableApi
internal class StemMixerPlayer(private val context: Context) {

  private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val audioFocusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(
      android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    )
    .setOnAudioFocusChangeListener { /* duck/pause handling is left to the system; stem players self-manage */ }
    .setAcceptsDelayedFocusGain(true)
    .build()

  private val players = LinkedHashMap<StemId, ExoPlayer>()
  private val eqProcessors = HashMap<StemId, CustomEqualizerAudioProcessor>()
  private val gainPanProcessors = HashMap<StemId, StemGainPanAudioProcessor>()

  private var originalPlayer: ExoPlayer? = null

  fun setTracks(outputs: List<SeparatedStem>, sourceUri: Uri?, master: MasterMixSettings) {
    releaseAllPlayers()
    if (outputs.isEmpty()) return

    audioManager.requestAudioFocus(audioFocusRequest)

    val byStem: Map<String, SeparatedStem> = outputs.associateBy { it.name }
    for (id in StemId.entries) {
      val output = byStem[id.id] ?: continue
      val processor = StemGainPanAudioProcessor()
      val eq = CustomEqualizerAudioProcessor()
      gainPanProcessors[id] = processor
      eqProcessors[id] = eq
      val player = buildPlayer(processor, eq)
      player.setMediaItem(MediaItem.fromUri(output.uri))
      player.prepare()
      players[id] = player
    }

    if (sourceUri != null) {
      val original = buildPlayer(StemGainPanAudioProcessor(), CustomEqualizerAudioProcessor())
      original.setMediaItem(MediaItem.fromUri(sourceUri))
      original.prepare()
      originalPlayer = original
    }
  }

  private fun buildPlayer(gainPan: StemGainPanAudioProcessor, eq: CustomEqualizerAudioProcessor): ExoPlayer {
    val renderersFactory = object : DefaultRenderersFactory(context) {
      override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
      ): androidx.media3.exoplayer.audio.AudioSink {
        val sonic = SonicAudioProcessor()
        val processorChain = DefaultAudioSink.DefaultAudioProcessorChain(
          arrayOf(gainPan, eq),
          SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
          sonic,
        )
        return DefaultAudioSink.Builder(context)
          .setEnableFloatOutput(false)
          .setEnableAudioTrackPlaybackParams(true)
          .setAudioProcessorChain(processorChain)
          .build()
      }
    }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

    return ExoPlayer.Builder(context)
      .setMediaSourceFactory(DefaultMediaSourceFactory(context))
      .setRenderersFactory(renderersFactory)
      .setWakeMode(C.WAKE_MODE_LOCAL)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(C.USAGE_MEDIA)
          .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
          .build(),
        false, // manual audio focus: all stem players share the single grant above
      )
      .setHandleAudioBecomingNoisy(true)
      .setDeviceVolumeControlEnabled(false)
      .build()
  }

  fun applySettings(stems: Map<StemId, StemMixSettings>, master: MasterMixSettings) {
    val someSoloed = stems.values.any { it.soloed }
    for ((id, player) in players) {
      val settings = stems[id] ?: continue
      val eq = eqProcessors[id]
      val gp = gainPanProcessors[id]
      val audible = settings.effectiveAudible(someSoloed)
      val masterGain = master.linearGain()
      val gain = if (audible) (settings.linearGain() * masterGain).toDouble() else 0.0
      gp?.gain = gain.coerceIn(0.0, 4.0)
      gp?.pan = settings.pan
      eq?.applyProfile(
        ParametricEQ(
          preamp = 0.0,
          bands = (settings.eqBands + master.eqBands)
            .filter { it.enabled }
            .map { band ->
              ParametricEQBand(
                frequency = band.frequency,
                gain = band.gainDb,
                q = band.q.coerceIn(0.5, 10.0),
                filterType = band.type,
                enabled = band.enabled,
              )
            },
        )
      )
      player.playbackParameters = PlaybackParameters(master.speed, master.pitch)

      // Trim/loop are handled in the VM's periodic position monitor; no audio-level work here.
    }
    originalPlayer?.playbackParameters = PlaybackParameters(master.speed, master.pitch)
  }

  fun play() {
    if (originalPlayer != null && activeIsOriginal) {
      originalPlayer?.playWhenReady = true
      return
    }
    players.values.forEach { it.playWhenReady = true }
    originalPlayer?.playWhenReady = false
  }

  fun pause() {
    players.values.forEach { it.playWhenReady = false }
    originalPlayer?.playWhenReady = false
  }

  fun seekTo(positionMs: Long) {
    players.values.forEach { it.seekTo(positionMs) }
    originalPlayer?.seekTo(positionMs)
  }

  fun setOriginalOnly(originalOnly: Boolean) {
    activeIsOriginal = originalOnly
    if (originalOnly) {
      players.values.forEach { it.playWhenReady = false }
      originalPlayer?.playWhenReady = true
    } else {
      originalPlayer?.playWhenReady = false
      players.values.forEach { it.playWhenReady = true }
    }
  }

  fun currentPositionMs(): Long = when {
    activeIsOriginal -> originalPlayer?.currentPosition ?: 0L
    else -> players.values.firstOrNull()?.currentPosition ?: 0L
  }

  fun durationMs(): Long = when {
    activeIsOriginal -> originalPlayer?.duration ?: 0L
    else -> players.values.firstOrNull()?.duration ?: 0L
  }

  fun isPlaying(): Boolean = when {
    activeIsOriginal -> originalPlayer?.isPlaying == true
    else -> players.values.firstOrNull()?.isPlaying == true
  }

  fun currentPositionSec(): Float = (currentPositionMs() / 1000f)
  fun seekToSec(seconds: Float) = seekTo((seconds.coerceAtLeast(0f) * 1000).toLong())

  @Volatile var activeIsOriginal: Boolean = false

  fun releaseAllPlayers() {
    players.values.forEach { it.release() }
    originalPlayer?.release()
    players.clear()
    eqProcessors.clear()
    gainPanProcessors.clear()
    originalPlayer = null
    audioManager.abandonAudioFocusRequest(audioFocusRequest)
  }

  fun close() = releaseAllPlayers()
}
