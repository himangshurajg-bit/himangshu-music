package echo.music.iad1tya.playback.queues

import com.music.innertube.models.SongItem

/** Selects a YouTube seed only when it plausibly represents the requested addon track. */
internal object AddonQueueTrackMatcher {
  private val decorations = setOf("official", "audio", "video", "lyrics", "lyric", "visualizer", "hd")
  private val versionQualifiers = setOf(
    "remix", "live", "acoustic", "instrumental", "karaoke", "cover", "sped", "slowed", "reverb",
  )

  fun select(candidates: List<SongItem>, title: String, artist: String?, durationSeconds: Int?): SongItem? {
    val requestedTitle = titleTokens(title)
    if (requestedTitle.isEmpty()) return null
    val requestedArtist = tokens(artist.orEmpty())
    val requestedVersions = requestedTitle.filter { it in versionQualifiers }.toSet()
    val requestedBase = requestedTitle.filterNot { it in versionQualifiers }

    return candidates.mapNotNull { candidate ->
      if (candidate.id.isBlank()) return@mapNotNull null
      // Search results often append featured performers to the title even when the
      // structured artist list contains the same names. Keep title comparison about
      // the song itself; artist credits are checked independently below.
      val candidateTitle = titleTokens(candidate.title)
      val candidateVersions = candidateTitle.filter { it in versionQualifiers }.toSet()
      val candidateBase = candidateTitle.filterNot { it in versionQualifiers }
      if (requestedBase.isEmpty() || candidateBase.isEmpty()) return@mapNotNull null
      if (!candidateVersions.containsAll(requestedVersions)) return@mapNotNull null

      val overlap = requestedBase.count { it in candidateBase }
      val recall = overlap.toDouble() / requestedBase.size
      val precision = overlap.toDouble() / candidateBase.size
      if (recall < MIN_TITLE_RECALL || precision < MIN_TITLE_PRECISION) return@mapNotNull null

      // A matching singer may be a secondary credited artist on YouTube results.
      val candidateArtist = candidate.artists.flatMap { tokens(it.name) }.distinct()
      val artistOverlap = requestedArtist.count { it in candidateArtist }
      if (requestedArtist.isNotEmpty() && artistOverlap == 0) return@mapNotNull null
      val artistRecall = if (requestedArtist.isEmpty()) 0.0 else artistOverlap.toDouble() / requestedArtist.size

      val candidateDuration = candidate.duration
      val durationDiff = if (durationSeconds != null && durationSeconds > 0 && candidateDuration != null && candidateDuration > 0) {
        kotlin.math.abs(durationSeconds - candidateDuration)
      } else null
      if (durationDiff != null && durationDiff > MAX_DURATION_DIFFERENCE_SECONDS) return@mapNotNull null

      val score =
        (recall * 60 + precision * 20 + artistRecall * 20 - candidateVersions.size * 8 - (durationDiff ?: 0) / 10.0)
      candidate to score
    }.maxByOrNull { it.second }?.first
  }

  /**
   * Reorders a radio page only when a candidate matches the person's actual listening history.
   * Unmatched songs keep YouTube's order; we do not invent a genre or mood from a title.
   */
  fun rankByTaste(
    candidates: List<SongItem>,
    favoriteArtists: List<String>,
    favoriteTrackKeys: Set<String>,
    currentMoodTags: Set<String> = emptySet(),
    candidateMoodTags: Map<String, Set<String>> = emptyMap(),
  ): List<SongItem> {
    if (
      candidates.size < 2 ||
        (favoriteArtists.isEmpty() && favoriteTrackKeys.isEmpty() && currentMoodTags.isEmpty())
    ) return candidates

    val artistRanks = favoriteArtists
      .map(::normalize)
      .filter(String::isNotBlank)
      .distinct()
      .withIndex()
      .associate { it.value to it.index }

    return candidates.withIndex()
      .sortedWith(
        compareByDescending<IndexedValue<SongItem>> { indexed ->
          val item = indexed.value
          val artistNames = item.artists.map { normalize(it.name) }
          val artistScore = artistNames.mapNotNull(artistRanks::get).minOrNull()?.let { 10.0 / (it + 1) } ?: 0.0
          val trackKey = trackKey(item.title, item.artists.map { it.name })
          val trackScore = if (trackKey in favoriteTrackKeys) 20.0 else 0.0
          val itemMoodTags = candidateMoodTags[item.id].orEmpty().map(::normalize).toSet()
          val preferredMoods = currentMoodTags.map(::normalize).toSet()
          val moodScore = itemMoodTags.intersect(preferredMoods).size * MOOD_TAG_SCORE
          artistScore + trackScore + moodScore
        }
          .thenBy { it.index },
      )
      .map { it.value }
  }

  fun trackKey(title: String, artists: List<String>): String =
    "${normalize(title)}|${artists.joinToString(" ") { normalize(it) }}"

  private fun normalize(value: String): String =
    value.lowercase()
      .replace(Regex("[\\p{Punct}]"), " ")
      .split(Regex("\\s+"))
      .filter { it.isNotBlank() && it !in decorations }
      .joinToString(" ")
      .trim()

  private fun tokens(value: String): List<String> = normalize(value).split(" ").filter(String::isNotBlank)

  private fun titleTokens(value: String): List<String> {
    val featureCreditStart =
      Regex("\\b(feat(?:uring)?|ft)\\b", RegexOption.IGNORE_CASE).find(value)?.range?.first
        ?: value.length
    return tokens(value.substring(0, featureCreditStart))
  }

  private const val MIN_TITLE_RECALL = 0.8
  private const val MIN_TITLE_PRECISION = 0.6
  private const val MAX_DURATION_DIFFERENCE_SECONDS = 45
  private const val MOOD_TAG_SCORE = 6.0
}
