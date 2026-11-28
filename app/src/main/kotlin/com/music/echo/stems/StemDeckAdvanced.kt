package echo.music.iad1tya.stems

/**
 * Advanced offline studio parity, implemented from scratch (no StemDeck code).
 * StemDeck is AGPL-3.0-only and cannot be copied into GPL-3.0 RajTune.
 * Every type here is RajTune's own logic for offline-only use.
 */

/** Tap-tempo: 2..8 taps, 250..1500 ms intervals, median, clamped 40..240 BPM. */
object TapTempoCalculator {
  const val MIN_BPM = 40
  const val MAX_BPM = 240
  const val MAX_TAPS = 8
  const val WINDOW_MS = 3000L
  const val MIN_INTERVAL_MS = 250L
  const val MAX_INTERVAL_MS = 1500L

  fun bpmFromTaps(tapsMs: List<Long>, nowMs: Long = tapsMs.lastOrNull() ?: 0L): Int? {
    if (tapsMs.size < 2) return null
    val sorted = tapsMs.sorted()
    val windowed = sorted.filter { nowMs - it <= WINDOW_MS }.takeLast(MAX_TAPS)
    if (windowed.size < 2) return null
    val intervals = windowed.zipWithNext { a, b -> b - a }.filter { it in MIN_INTERVAL_MS..MAX_INTERVAL_MS }
    if (intervals.isEmpty()) return null
    val median = intervals.sorted()[intervals.size / 2].toDouble()
    if (median <= 0) return null
    return (60000.0 / median).toInt().coerceIn(MIN_BPM, MAX_BPM)
  }
}

/** Live beat state at a playback position (manual BPM, no beat grid). */
data class LiveBeatState(
  val beatIndex: Long,
  val beatInBar: Int,
  val isDownbeat: Boolean,
  val positionInBeat: Float,
  val secondsToNextBeat: Double,
)

object LiveBeatClock {
  fun stateAt(
    settings: ClickSettings,
    positionSec: Float,
    loopStartSec: Float = 0f,
  ): LiveBeatState {
    val s = settings.normalized()
    val interval = s.beatIntervalSec
    val offset = (positionSec - loopStartSec).coerceAtLeast(0f)
    val beatFloat = offset / interval
    val beatIndex = beatFloat.toLong()
    val frac = (beatFloat - beatIndex.toDouble()).toFloat().coerceIn(0f, 1f)
    val beatInBar = ((beatIndex % s.beatsPerBar).toInt() + 1).coerceIn(1, s.beatsPerBar)
    return LiveBeatState(
      beatIndex = beatIndex,
      beatInBar = beatInBar,
      isDownbeat = beatInBar == 1,
      positionInBeat = frac,
      secondsToNextBeat = (1.0 - frac) * interval,
    )
  }
}

/** A-B loop range in seconds. */
data class LoopRange(val startSec: Float = 0f, val endSec: Float? = null) {
  val durationSec: Float? get() = endSec?.let { (it - startSec).coerceAtLeast(0f) }
  val isValid: Boolean get() = endSec != null && endSec!! > startSec
  fun contains(positionSec: Float): Boolean =
    positionSec >= startSec && (endSec == null || positionSec < endSec!!)
}

object LoopRangeLogic {
  fun normalized(startSec: Float, endSec: Float?, durationSec: Float): LoopRange {
    val dur = durationSec.coerceAtLeast(0f)
    val s = startSec.coerceIn(0f, dur)
    val e = endSec?.coerceIn(s, dur)
    return LoopRange(s, e)
  }
}

/** User marker on the timeline. */
data class StemMarker(val id: String = "", val timeSec: Float = 0f, val label: String = "")

/** Playlist queue item (offline local audio). */
data class StemQueueItem(
  val uriString: String = "",
  val name: String = "",
  val pickedStemIds: List<String> = listOf("vocals", "drums", "bass", "other"),
) {
  val pickedStems: List<StemId> get() = pickedStemIds.map { StemId.from(it) }
}

object QueueLogic {
  val defaultPick: List<String> = listOf("vocals", "drums", "bass", "other")
  fun normalizePick(pick: List<String>): List<String> {
    val valid = setOf("vocals", "drums", "bass", "other")
    val filtered = pick.map { it.lowercase() }.filter { it in valid }.distinct()
    return if (filtered.isEmpty()) defaultPick else filtered
  }
  fun label(pick: List<String>): String = normalizePick(pick).joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
}

/** Minimal offline library entry for saved stems/mixes. */
data class StemLibraryEntry(
  val displayName: String = "",
  val uriString: String = "",
  val kind: String = "mix", // mix | zip | video | stem
  val folder: String = "Music/RajTune Stems",
  val tags: List<String> = emptyList(),
  val favorite: Boolean = false,
  val trashed: Boolean = false,
)

object LibraryLogic {
  fun visible(entries: List<StemLibraryEntry>, favoritesOnly: Boolean = false): List<StemLibraryEntry> {
    val nonTrashed = entries.filter { !it.trashed }
    return if (favoritesOnly) nonTrashed.filter { it.favorite } else nonTrashed
  }
  fun trashByName(entries: List<StemLibraryEntry>, displayName: String): List<StemLibraryEntry> =
    entries.map { if (it.displayName == displayName) it.copy(trashed = true) else it }
  fun toggleFavorite(entries: List<StemLibraryEntry>, displayName: String): List<StemLibraryEntry> =
    entries.map { if (it.displayName == displayName) it.copy(favorite = !it.favorite) else it }
}

/** Offline song metadata (manual + computed). */
data class SongMetadata(
  val key: String = "",
  val tempoBpm: Int? = null,
  val loudnessLabel: String = "None",
  val durationSec: Float = 0f,
) {
  fun readout(): String {
    val parts = mutableListOf<String>()
    parts.add("Duration %.1fs".format(durationSec))
    if (key.isNotBlank()) parts.add("Key $key")
    if (tempoBpm != null) parts.add("Tempo $tempoBpm BPM")
    parts.add("Loudness $loudnessLabel")
    return parts.joinToString(" · ")
  }
}

object MetadataLogic {
  val commonKeys: List<String> = listOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
    "Am", "Bbm", "Bm", "Cm", "C#m", "Dm", "D#m", "Em", "Fm", "F#m", "Gm", "G#m",
  )
  fun formatBpm(bpm: Int?): String = bpm?.toString() ?: "—"
}

/** Video export resolution. */
enum class VideoExportQuality(val width: Int, val height: Int, val label: String) {
  HD720(1280, 720, "720p"),
  HD1080(1920, 1080, "1080p"),
  ;

  companion object {
    fun from(value: String?): VideoExportQuality =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HD720
  }
}
