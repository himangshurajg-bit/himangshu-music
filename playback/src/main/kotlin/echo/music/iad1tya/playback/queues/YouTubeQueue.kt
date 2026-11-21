package echo.music.iad1tya.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.PodcastPage
import echo.music.iad1tya.extensions.toMediaItem
import echo.music.iad1tya.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class YouTubeQueue(
  private var endpoint: WatchEndpoint,
  override val preloadItem: MediaMetadata? = null,
) : Queue {
  private var continuation: String? = null
  private var retryCount = 0
  private val maxRetries = 3
  val isRadioQueue = endpoint.playlistId?.startsWith("RD") != false
  private val pageFilter = RadioQueuePageFilter()
  /** Started from an episode: YouTube's radio mixes in music, which must not follow it. */
  private var isEpisodeRadio = false
  private val isAddonQueue = endpoint.videoId?.startsWith("addon_") == true
  private var favoriteArtists: List<String> = emptyList()
  private var favoriteTrackKeys: Set<String> = emptySet()
  private var tasteSeedVideoId: String? = null
  private var seedMoodTags: Set<String> = emptySet()
  private val moodSignalCache = mutableMapOf<String, Set<String>>()

  /** Supplies a local listening-history snapshot before the queue's first radio page loads. */
  fun setTasteSignals(artists: List<String>, tracks: List<Pair<String, List<String>>>) {
    favoriteArtists = artists
    favoriteTrackKeys = tracks.mapTo(mutableSetOf()) { (title, trackArtists) ->
      AddonQueueTrackMatcher.trackKey(title, trackArtists)
    }
  }

  override suspend fun getInitialStatus(): Queue.Status {
    return withContext(IO) {
      continuation = null
      pageFilter.reset()
      var lastException: Throwable? = null
      val isAddon = isAddonQueue
      val originalAddonItem = if (isAddon) preloadItem else null

      if (isAddon) {
          try {
              val query = "${preloadItem?.title ?: ""} ${preloadItem?.artists?.firstOrNull()?.name ?: ""}".trim()
              val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
              val ytTrack = AddonQueueTrackMatcher.select(
                  candidates = searchResult?.items?.filterIsInstance<SongItem>().orEmpty(),
                  title = preloadItem?.title.orEmpty(),
                  artist = preloadItem?.artists?.firstOrNull()?.name,
                  durationSeconds = preloadItem?.duration,
              )
              if (ytTrack == null && originalAddonItem != null) {
                  // Keep the actual selected addon track rather than seeding radio with a
                  // same-title/wrong-artist search result when confidence is insufficient.
                  return@withContext Queue.Status(
                      title = originalAddonItem.title,
                      items = listOf(originalAddonItem.toMediaItem()),
                      mediaItemIndex = 0,
                  )
              }
              if (ytTrack != null) {
                  tasteSeedVideoId = ytTrack.id
                  endpoint = WatchEndpoint(videoId = ytTrack.id, playlistId = "RDAMVM${ytTrack.id}")
              }
          } catch (e: Exception) {
              if (e is CancellationException) throw e
              e.printStackTrace()
          }
      }

      for (attempt in 0..maxRetries) {
        try {
          val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
          endpoint = nextResult.endpoint
          continuation = nextResult.continuation
          retryCount = 0
          
          val currentItem = nextResult.items.getOrNull(nextResult.currentIndex ?: 0)
          val currentSongId = currentItem?.id
          isEpisodeRadio = currentItem?.isPodcastEpisode == true
          // Its show is known: the service queues the show's next episode instead of a radio.
          if (isEpisodeRadio && preloadItem?.artists?.any { PodcastPage.isShowBrowseId(it.id) } == true) {
            continuation = null
            return@withContext Queue.Status(
              title = nextResult.title,
              items = listOf(preloadItem.toMediaItem()),
              mediaItemIndex = 0,
            )
          }
          val filtered = if (isRadioQueue) pageFilter.filter(nextResult.items) else nextResult.items
          val songs = if (isEpisodeRadio) filtered.filter { it.isPodcastEpisode } else filtered
          val rankedSongs = if (isRadioQueue) rankRadioSongs(songs, currentSongId) else songs
          val itemsList = rankedSongs.map { it.toMediaItem() }.toMutableList()
          if (isAddon && originalAddonItem != null) {
              if (itemsList.isNotEmpty()) {
                  itemsList[0] = originalAddonItem.toMediaItem()
              } else {
                  itemsList.add(originalAddonItem.toMediaItem())
              }
          }
          
          return@withContext Queue.Status(
            title = nextResult.title,
            items = itemsList,
            mediaItemIndex = if (isAddon) 0 else
              rankedSongs.indexOfFirst { it.id == currentSongId }.coerceAtLeast(0),
          )
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          lastException = e

          if (attempt == 0 && endpoint.videoId != null && endpoint.playlistId == null) {
            endpoint =
              WatchEndpoint(videoId = endpoint.videoId, playlistId = "RDAMVM${endpoint.videoId}")
          }
        }
      }
      
      if (isAddon && originalAddonItem != null) {
          return@withContext Queue.Status(
              title = originalAddonItem.title,
              items = listOf(originalAddonItem.toMediaItem()),
              mediaItemIndex = 0
          )
      }
      
      throw lastException ?: Exception("Failed to get initial status")
    }
  }

  /**
   * Uses YouTube's per-track mood/genre signals only when available. Requests are bounded to
   * the seed plus a few visible queue candidates; missing signals leave YouTube's order intact.
   */
  private suspend fun rankRadioSongs(
    songs: List<SongItem>,
    pinnedSongId: String? = null,
  ): List<SongItem> {
    if (songs.size < 2) return songs
    val seedId = tasteSeedVideoId ?: endpoint.videoId?.takeUnless { it.startsWith("addon_") }
    val requestedIds: List<String> = buildList {
      seedId?.let(::add)
      songs.take(MAX_MOOD_CANDIDATES).mapNotNullTo(this) { it.id.takeIf(String::isNotBlank) }
    }.distinct()
    val missingIds = requestedIds.filterNot(moodSignalCache::containsKey)
    if (missingIds.isNotEmpty()) {
      val fetched =
        withTimeoutOrNull<Map<String, Set<String>>>(MOOD_SIGNAL_TIMEOUT_MS) {
          coroutineScope {
            missingIds
              .map { videoId ->
                async<Pair<String, Set<String>>> {
                  val tags =
                    YouTube.fetchTasteSignals(videoId).getOrNull()?.let { signals ->
                      (signals.moodTags + signals.genres).toSet()
                    }.orEmpty()
                  videoId to tags
                }
              }
              .awaitAll()
              .toMap()
          }
        }
      if (fetched != null) moodSignalCache.putAll(fetched)
    }
    val signalMap = requestedIds.associateWith { moodSignalCache[it].orEmpty() }

    if (seedMoodTags.isEmpty() && seedId != null) {
      seedMoodTags = signalMap[seedId].orEmpty()
    }
    val candidateSignals = songs.take(MAX_MOOD_CANDIDATES)
      .associate { it.id to signalMap[it.id].orEmpty() }

    val pinnedIndex = songs.indexOfFirst { it.id == pinnedSongId }.takeIf { it >= 0 }
    val candidates =
      if (pinnedIndex == null) songs else songs.filterIndexed { index, _ -> index != pinnedIndex }
    val rankedCandidates = AddonQueueTrackMatcher.rankByTaste(
      candidates = candidates,
      favoriteArtists = favoriteArtists,
      favoriteTrackKeys = favoriteTrackKeys,
      currentMoodTags = seedMoodTags,
      candidateMoodTags = candidateSignals,
    )
    if (pinnedIndex == null) return rankedCandidates
    return rankedCandidates.toMutableList().apply {
      add(pinnedIndex.coerceAtMost(size), songs[pinnedIndex])
    }
  }

  override fun hasNextPage(): Boolean = continuation != null

  override suspend fun nextPage(): List<MediaItem> {
    return withContext(IO) {
      val visitedContinuations = mutableSetOf<String>()

      repeat(5) {
        val pageContinuation = continuation ?: return@withContext emptyList()
        if (!visitedContinuations.add(pageContinuation)) {
          continuation = null
          return@withContext emptyList()
        }
        for (attempt in 0..maxRetries) {
          try {
            val nextResult = YouTube.next(endpoint, pageContinuation).getOrThrow()
            endpoint = nextResult.endpoint
            continuation = nextResult.continuation?.takeUnless { it == pageContinuation }
            retryCount = 0
            val filtered = if (isRadioQueue) pageFilter.filter(nextResult.items) else nextResult.items
            val songs = if (isEpisodeRadio) filtered.filter { it.isPodcastEpisode } else filtered
            val rankedSongs = if (isRadioQueue) rankRadioSongs(songs) else songs
            if (rankedSongs.isNotEmpty()) return@withContext rankedSongs.map { it.toMediaItem() }
            break
          } catch (e: Exception) {
            if (e is CancellationException) throw e
            retryCount++
            if (attempt == maxRetries) {
              throw e
            }
          }
        }
      }
      emptyList()
    }
  }
  companion object {
    private const val MAX_MOOD_CANDIDATES = 4
    private const val MOOD_SIGNAL_TIMEOUT_MS = 2_000L

    fun radio(song: MediaMetadata): YouTubeQueue {
      return YouTubeQueue(WatchEndpoint(videoId = song.id), song)
    }
  }
}
