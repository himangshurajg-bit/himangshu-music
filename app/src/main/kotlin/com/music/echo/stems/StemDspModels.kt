package echo.music.iad1tya.stems

import echo.music.dsp.core.models.FilterType

/**
 * Professional per-stem and master mix settings for the stem mixer.
 *
 * These are consumed by two real processing paths:
 *  - live playback through StemGainPanAudioProcessor + CustomEqualizerAudioProcessor, and
 *  - export through FFmpeg (libavfilter: volume, pan, equalizer, amix).
 *
 * They are deliberately small, serializable and persisted per stem so VM/UI code
 * never has to reason about processors directly.
 */
enum class StemId(val id: String, val label: String) {
  Vocals("vocals", "Vocals"),
  Bass("bass", "Bass"),
  Drums("drums", "Drums"),
  Other("other", "Other/Music"),
  Piano("piano", "Piano"),
  Guitar("guitar", "Guitar"),
  Synthesizer("synthesizer", "Synthesizer"),
  ;

  companion object {
    private val byId = entries.associateBy { it.id }

    fun from(id: String): StemId = byId[id.lowercase()] ?: Vocals
  }
}

data class StemEqBand(
  /** One of the six standard EQ frequencies used by professional graphic/parametic EQs. */
  val frequency: Double,
  val gainDb: Double = 0.0,
  val type: FilterType = FilterType.PK,
  val enabled: Boolean = true,
  /** Q factor (0.5..10.0). Default 1.41 matches the app's existing EQ. */
  val q: Double = 1.41,
)

data class StemMixSettings(
  val stemId: StemId,
  /** Gain in dB: -40..+12 dB (UI fader). */
  val gainDb: Float = 0f,
  /** Pan: -1 (left) .. +1 (right), 0 centre. Constant-power pan. */
  val pan: Float = 0f,
  val muted: Boolean = false,
  val soloed: Boolean = false,
  /** Per-stem equalizer bands (6-band professional graphic EQ with parametric Q/type). */
  val eqBands: List<StemEqBand> = StemMixPresets.flat().eqBands,
  /** Optional trim for the exported mix, per stem; null = full file. */
  val trimStartSec: Float? = null,
  val trimEndSec: Float? = null,
  /** Loop the stem locally for practicing (live preview only). */
  val loopEnabled: Boolean = false,
) {
  /** Linear volume multiplier for a dB gain, clamped to a safe range. */
  fun linearGain(): Float {
    val db = gainDb.coerceIn(-40f, 12f)
    return Math.pow(10.0, db / 20.0).toFloat()
  }

  /** If any stem is soloed, non-soloed stems are audible at 0. */
  fun effectiveAudible(someSoloed: Boolean): Boolean = !muted && (!someSoloed || soloed)

  fun withTrim(startSec: Float?, endSec: Float?): StemMixSettings = copy(trimStartSec = startSec, trimEndSec = endSec)
}

/** Six-band professional presets shared by every stem and the master bus. */
object StemMixPresets {
  private val defaultFreqs = listOf(63.0, 250.0, 1000.0, 4000.0, 8000.0, 12000.0)
  private val shelfFreqs = mapOf(0 to 100.0, 5 to 10000.0)

  fun flat(): StemEqPreset = StemEqPreset("Flat", defaultFreqs.map { StemEqBand(it) })
  fun vocalBoost(): StemEqPreset = StemEqPreset(
    "Vocal Boost",
    listOf(
      StemEqBand(63.0, -2.0),
      StemEqBand(250.0, 1.5),
      StemEqBand(1000.0, 2.0),
      StemEqBand(4000.0, 5.0),
      StemEqBand(8000.0, 4.0),
      StemEqBand(12000.0, 2.0),
    ),
  )
  fun bassBoost(): StemEqPreset = StemEqPreset(
    "Bass Boost",
    listOf(
      StemEqBand(63.0, 8.0, FilterType.LSC),
      StemEqBand(250.0, 5.0),
      StemEqBand(1000.0, -2.0),
      StemEqBand(4000.0, -1.0),
      StemEqBand(8000.0, 0.0),
      StemEqBand(12000.0, 0.0),
    ),
  )
  fun drumPunch(): StemEqPreset = StemEqPreset(
    "Drum Punch",
    listOf(
      StemEqBand(63.0, 4.0, FilterType.LSC),
      StemEqBand(250.0, -1.0),
      StemEqBand(1000.0, 1.5),
      StemEqBand(4000.0, 4.0),
      StemEqBand(8000.0, 3.0, FilterType.HSC),
      StemEqBand(12000.0, 2.0),
    ),
  )
  fun warm(): StemEqPreset = StemEqPreset(
    "Warm",
    listOf(
      StemEqBand(63.0, 2.5, FilterType.LSC),
      StemEqBand(250.0, 2.0),
      StemEqBand(1000.0, 0.0),
      StemEqBand(4000.0, -2.5),
      StemEqBand(8000.0, -3.0, FilterType.HSC),
      StemEqBand(12000.0, -3.5),
    ),
  )
  fun bright(): StemEqPreset = StemEqPreset(
    "Bright",
    listOf(
      StemEqBand(63.0, -2.0),
      StemEqBand(250.0, -1.5),
      StemEqBand(1000.0, 1.0),
      StemEqBand(4000.0, 3.5),
      StemEqBand(8000.0, 5.0, FilterType.HSC),
      StemEqBand(12000.0, 6.0),
    ),
  )
  fun club(): StemEqPreset = StemEqPreset(
    "Club",
    listOf(
      StemEqBand(63.0, 6.0, FilterType.LSC),
      StemEqBand(250.0, 2.5),
      StemEqBand(1000.0, -3.0),
      StemEqBand(4000.0, 2.0),
      StemEqBand(8000.0, 5.0, FilterType.HSC),
      StemEqBand(12000.0, 6.0),
    ),
  )
  fun gentleLowCut(): StemEqPreset = StemEqPreset(
    "Gentle Low Cut",
    listOf(
      StemEqBand(63.0, -4.0, FilterType.LSC),
      StemEqBand(250.0, -1.0),
      StemEqBand(1000.0, 0.0),
      StemEqBand(4000.0, 0.0),
      StemEqBand(8000.0, 0.0),
      StemEqBand(12000.0, 0.0),
    ),
  )

  fun all(): List<StemEqPreset> = listOf(flat(), vocalBoost(), bassBoost(), drumPunch(), warm(), bright(), club(), gentleLowCut())

  fun presetsFor(stemId: StemId): List<StemEqPreset> {
    val recommended = when (stemId) {
      StemId.Vocals -> listOf(vocalBoost(), bright(), warm(), flat())
      StemId.Bass -> listOf(bassBoost(), warm(), flat())
      StemId.Drums -> listOf(drumPunch(), club(), warm(), flat())
      StemId.Guitar, StemId.Piano, StemId.Synthesizer -> listOf(bright(), warm(), flat())
      StemId.Other -> listOf(flat(), warm(), bright())
    }
    return recommended + all().filter { it !in recommended }
  }

  fun masterPresets(): List<StemEqPreset> = all()
}

data class StemEqPreset(
  val name: String,
  val eqBands: List<StemEqBand>,
)

data class MasterMixSettings(
  /** Master EQ: six bands applied per stem (per-stream approximation of a post-mix bus EQ). */
  val eqBands: List<StemEqBand> = StemMixPresets.flat().eqBands,
  /** Master fader, dB. */
  val masterGainDb: Float = 0f,
  /** Master playback speed (0.5..2.0). */
  val speed: Float = 1f,
  /** Master pitch multiplier (0.5..2.0); independent of speed. */
  val pitch: Float = 1f,
  /** Full-song play/pause, represented here for persistence/comparisons. */
  val playing: Boolean = false,
  /** True while playing the original source (A/B compare). */
  val originalOnly: Boolean = false,
  /** Timeline trim for live playback + export, in seconds. */
  val trimStartSec: Float = 0f,
  val trimEndSec: Float? = null,
  /** Loop the trimmed timeline. */
  val loopEnabled: Boolean = false,
  /** Called every time a setting changes; the VM persists in this callback. */
  val onChange: ((MasterMixSettings) -> Unit)? = null,
) {
  fun linearGain(): Float {
    val db = masterGainDb.coerceIn(-40f, 12f)
    return Math.pow(10.0, db / 20.0).toFloat()
  }
}

enum class StemExportFormat { WAV, MP3 }

enum class StemExportQuality(val ffmpegValue: String) {
  WAV_16BIT("pcm_s16le"),
  MP3_320("320k"),
  MP3_192("192k"),
  MP3_128("128k"),
}

internal val StemId.allStems: List<StemId> get() = StemId.entries

internal fun StemId.monogram(): String = label.take(1).uppercase()

/** FFmpeg equalizer bandwidth (octaves) for a gain/Q peaking band. */
internal fun qToBandwidth(q: Double): Double {
  val safe = q.coerceIn(0.5, 10.0)
  val bw = 2.0 * kotlin.math.asinh(1.0 / (2.0 * safe)) / kotlin.math.ln(2.0)
  return bw.coerceIn(0.2, 6.0)
}

internal fun panToConstantPowerGains(pan: Float): Pair<Float, Float> {
  val clamped = pan.coerceIn(-1f, 1f)
  val angle = (clamped + 1f) * Math.PI / 4.0
  return Math.cos(angle).toFloat() to Math.sin(angle).toFloat()
}
