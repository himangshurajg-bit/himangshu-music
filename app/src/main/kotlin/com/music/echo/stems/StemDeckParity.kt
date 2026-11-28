package echo.music.iad1tya.stems

/**
 * StemDeck-parity concepts implemented from scratch for RajTune.
 *
 * StemDeck (github.com/stemdeckapp/stemdeck) is AGPL-3.0-only: its code may
 * not be copied into GPL-3.0 RajTune (incompatible copyleft / network-use
 * obligations). No StemDeck code, assets or model weights are used here.
 * Only the user-facing ideas (six named lanes, Practice vs Advanced modes,
 * key transposition, click/count-in, stem ZIP export, loudness targets) are
 * reimplemented with RajTune's own architecture. Nothing in this file may be
 * read as claiming guitar/piano separation: the shipped model only outputs
 * four stems.
 */

/** The six lanes StemDeck shows. Order matches the StemDeck studio. */
enum class StemDeckStem(val label: String) {
  Vocals("Vocals"),
  Drums("Drums"),
  Bass("Bass"),
  Guitar("Guitar"),
  Piano("Piano"),
  Other("Other"),
  ;

  /** RajTune stem this lane plays through. Guitar/Piano route via Other. */
  fun routeToRajTuneStem(): StemId = when (this) {
    Vocals -> StemId.Vocals
    Drums -> StemId.Drums
    Bass -> StemId.Bass
    Guitar -> StemId.Other
    Piano -> StemId.Other
    Other -> StemId.Other
  }

  /** True only when the offline model outputs this stem by itself. */
  val isSeparatelySeparated: Boolean
    get() = this == Vocals || this == Drums || this == Bass || this == Other

  val routingNote: String
    get() = if (isSeparatelySeparated) {
      "Separated on this device"
    } else {
      "Routed from Other — not separately separated by the current 4-stem model"
    }
}

object StemDeckParity {
  /** Six-lane order shown in the mixer (StemDeck: vocals, drums, bass, guitar, piano, other). */
  val sixStems: List<StemDeckStem> = StemDeckStem.entries

  /** Semitones -> pitch multiplier (e.g. +2 semitones = whole step up). */
  fun semitonesToPitch(semitones: Int): Float {
    val clamped = semitones.coerceIn(-12, 12)
    return Math.pow(2.0, clamped / 12.0).toFloat()
  }

  fun pitchToSemitones(pitch: Float): Int {
    val p = pitch.coerceIn(0.5f, 2f).toDouble()
    val semitones = Math.round(Math.log(p) / Math.log(2.0) * 12.0).toInt()
    return semitones.coerceIn(-12, 12)
  }

  /** Slow-practice speed presets (StemDeck offers 0.50x..0.99x pitch-preserved slow-down). */
  val slowPresets: List<Float> = listOf(0.5f, 0.75f, 0.85f, 0.9f, 0.95f, 1.0f)

  /** ZIP entry names for every stem, mirroring StemDeck's "every part as a .zip". */
  internal fun zipEntries(outputs: List<SeparatedStem>): List<Pair<String, SeparatedStem>> {
    val ordered = outputs.sortedBy { StemId.from(it.name).ordinal }
    return ordered.map { output ->
      val base = StemId.from(output.name).id
      "$base.wav" to output
    }
  }
}

enum class PracticeMode(val label: String) {
  /** Practice: mixer + speed/key/loop only. */
  Practice("Practice"),
  /** Advanced: everything including EQ, presets, loudness and exports. */
  Advanced("Advanced"),
  ;

  /** Whether advanced-only controls (per-stem EQ, saved presets, loudness) are visible. */
  val showsAdvancedControls: Boolean get() = this == Advanced

  companion object {
    fun from(value: String?): PracticeMode =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Practice
  }
}

data class ClickSettings(
  val enabled: Boolean = false,
  /** BPM, 40..240. Default 120. */
  val bpm: Int = 120,
  /** Beats per bar: 2, 3, 4 or 6 (3+2+2 groupings are not modelled). */
  val beatsPerBar: Int = 4,
  /** Count-in bars before the song starts in exports: 0..4. */
  val countInBars: Int = 1,
  /** Click level in the export mix, 0..1. */
  val volume: Float = 0.5f,
) {
  val beatIntervalSec: Double get() = 60.0 / bpm.coerceIn(40, 240)
  val countInBeats: Int get() = beatsPerBar * countInBars.coerceIn(0, 4)
  val countInSec: Double get() = beatIntervalSec * countInBeats

  fun normalized(): ClickSettings = copy(
    bpm = bpm.coerceIn(40, 240),
    beatsPerBar = if (beatsPerBar in listOf(2, 3, 4, 6)) beatsPerBar else 4,
    countInBars = countInBars.coerceIn(0, 4),
    volume = volume.coerceIn(0f, 1f),
  )
}

/** Loudness targets for mix-down (FFmpeg loudnorm). PP = 0 keeps current behaviour. */
enum class LoudnessTarget(val label: String, val integratedLufs: Double?, val truePeakDbTp: Double?) {
  None("None", null, null),
  Streaming("Streaming −14 LUFS", -14.0, -1.0),
  Album("Album −16 LUFS", -16.0, -1.0),
  Broadcast("Broadcast −23 LUFS", -23.0, -1.0),
  ;

  fun toFfmpegLoudnormFilter(): String? {
    val i = integratedLufs ?: return null
    val tp = truePeakDbTp ?: -1.0
    return "loudnorm=I=$i:TP=$tp:LRA=11"
  }

  companion object {
    fun from(value: String?): LoudnessTarget =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: None
  }
}

/**
 * Generates a click/count-in track as 16-bit PCM WAV bytes (44100 Hz stereo).
 * Downbeat click is a 1568 Hz (G6) short pulse, other beats 1046 Hz (C6).
 * Used only in mix-down exports; live click playback needs a beat grid,
 * which RajTune does not have, so it is deliberately not faked live.
 */
object ClickTrackWriter {
  const val SAMPLE_RATE = 44100

  fun generateWavBytes(settings: ClickSettings, durationSec: Double): ByteArray {
    val s = settings.normalized()
    val totalBeats = if (!s.enabled) 0 else {
      val songBeats = (durationSec / s.beatIntervalSec).toInt() + 1
      s.countInBeats + songBeats
    }
    val totalBeatsSafe = totalBeats.coerceAtLeast(0)
    val totalSamples = ((totalBeatsSafe * s.beatIntervalSec + 0.1) * SAMPLE_RATE).toInt().coerceIn(0, SAMPLE_RATE * 3600)
    val left = FloatArray(totalSamples)
    val right = FloatArray(totalSamples)
    if (s.enabled) {
      for (beat in 0 until totalBeatsSafe) {
        val downbeat = beat % s.beatsPerBar == 0
        val freq = if (downbeat) 1568.0 else 1046.0
        val amp = (if (downbeat) 0.9 else 0.6) * s.volume
        val start = (beat * s.beatIntervalSec * SAMPLE_RATE).toInt()
        val pulseLen = (0.05 * SAMPLE_RATE).toInt()
        for (n in 0 until pulseLen) {
          val idx = start + n
          if (idx >= totalSamples) break
          // Exponential decay envelope.
          val env = Math.exp(-6.0 * n / pulseLen).toFloat()
          val v = (Math.sin(2.0 * Math.PI * freq * n / SAMPLE_RATE) * amp * env).toFloat()
          left[idx] = v
          right[idx] = v
        }
      }
    }
    val dataBytes = totalSamples * 4 // 16-bit stereo = 4 bytes per sample frame
    val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray())
    header.putInt(36 + dataBytes)
    header.put("WAVE".toByteArray())
    header.put("fmt ".toByteArray())
    header.putInt(16)
    header.putShort(1) // PCM
    header.putShort(2) // stereo
    header.putInt(SAMPLE_RATE)
    header.putInt(SAMPLE_RATE * 4)
    header.putShort(4) // block align
    header.putShort(16) // bits
    header.put("data".toByteArray())
    header.putInt(dataBytes)
    val out = java.io.ByteArrayOutputStream()
    out.write(header.array())
    val buf = java.nio.ByteBuffer.allocate(8192).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until totalSamples) {
      fun clamp16(v: Float): Short {
        val c = v.coerceIn(-1f, 1f)
        return (c * 32767f).toInt().toShort()
      }
      buf.putShort(clamp16(left[i]))
      buf.putShort(clamp16(right[i]))
      if (!buf.hasRemaining()) {
        buf.flip()
        out.write(buf.array(), 0, buf.remaining())
        buf.clear()
      }
    }
    if (buf.position() > 0) {
      buf.flip()
      out.write(buf.array(), 0, buf.remaining())
    }
    return out.toByteArray()
  }
}
