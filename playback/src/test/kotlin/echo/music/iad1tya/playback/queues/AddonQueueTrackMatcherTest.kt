package echo.music.iad1tya.playback.queues

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddonQueueTrackMatcherTest {
  private fun song(id: String, title: String, artist: String, duration: Int) =
    SongItem(id, title, listOf(Artist(artist, null)), duration = duration, thumbnail = "")

  @Test
  fun choosesTitleArtistAndDurationMatchInsteadOfFirstSearchResult() {
    val wrongFirst = song("wrong", "Morom Lagi Gol Tuk", "Another Singer", 320)
    val right = song("right", "Morom Lagi Gol Tuk - Official Audio", "Dipanwita Deka", 319)

    assertEquals(
      "right",
      AddonQueueTrackMatcher.select(listOf(wrongFirst, right), "Morom Lagi Gol Tuk", "Dipanwita Deka", 320)?.id,
    )
  }

  @Test
  fun matchesFeaturedArtistEvenWhenYoutubeListsAnotherArtistFirst() {
    val collaboration = SongItem(
      "collab",
      "Dola (feat. Nilakshi Neog)",
      listOf(Artist("Dikshu", null), Artist("Nilakshi Neog", null)),
      duration = 265,
      thumbnail = "",
    )

    assertEquals(
      "collab",
      AddonQueueTrackMatcher.select(listOf(collaboration), "Dola", "Nilakshi Neog", 265)?.id,
    )
  }

  @Test
  fun rejectsSameTitleByDifferentArtist() {
    assertNull(
      AddonQueueTrackMatcher.select(
        listOf(song("wrong", "Dola", "Different Artist", 265)),
        "Dola",
        "Nilakshi Neog",
        265,
      ),
    )
  }

  @Test
  fun rejectsUnrelatedTitleEvenWhenArtistMatches() {
    assertNull(
      AddonQueueTrackMatcher.select(
        listOf(song("wrong", "Dola Remix", "Nilakshi Neog", 265)),
        "Morom Lagi Gol Tuk",
        "Nilakshi Neog",
        265,
      ),
    )
  }

  @Test
  fun doesNotReplaceRequestedRemixWithOriginal() {
    assertNull(
      AddonQueueTrackMatcher.select(
        listOf(song("plain", "Dola", "Nilakshi Neog", 265)),
        "Dola Remix",
        "Nilakshi Neog",
        265,
      ),
    )
  }

  @Test
  fun rejectsWrongLengthRecordingWhenMetadataIsAvailable() {
    assertNull(
      AddonQueueTrackMatcher.select(
        listOf(song("wrong", "Dola", "Nilakshi Neog", 180)),
        "Dola",
        "Nilakshi Neog",
        265,
      ),
    )
  }
  @Test
  fun ranksSongsByArtistsFromListeningHistory() {
    val unfamiliar = song("unfamiliar", "New Song", "New Artist", 210)
    val favorite = song("favorite", "Another Song", "Dipanwita Deka", 220)

    assertEquals(
      listOf("favorite", "unfamiliar"),
      AddonQueueTrackMatcher.rankByTaste(
        listOf(unfamiliar, favorite),
        favoriteArtists = listOf("Dipanwita Deka"),
        favoriteTrackKeys = emptySet(),
      ).map { it.id },
    )
  }

  @Test
  fun ranksAnExactFavoriteTrackAheadOfArtistOnlyMatch() {
    val artistMatch = song("artist", "Different Track", "Dipanwita Deka", 220)
    val trackMatch = song("track", "Morom Lagi Gol Tuk", "Other Artist", 320)

    assertEquals(
      "track",
      AddonQueueTrackMatcher.rankByTaste(
        listOf(artistMatch, trackMatch),
        favoriteArtists = listOf("Dipanwita Deka"),
        favoriteTrackKeys = setOf(AddonQueueTrackMatcher.trackKey("Morom Lagi Gol Tuk", listOf("Other Artist"))),
      ).first().id,
    )
  }

  @Test
  fun keepsYouTubeOrderWhenNoTasteSignalMatches() {
    val first = song("first", "First Song", "One", 180)
    val second = song("second", "Second Song", "Two", 190)

    assertEquals(
      listOf("first", "second"),
      AddonQueueTrackMatcher.rankByTaste(
        listOf(first, second),
        favoriteArtists = listOf("No Match"),
        favoriteTrackKeys = emptySet(),
      ).map { it.id },
    )
  }

  @Test
  fun prioritizesCandidatesMatchingTheCurrentTrackMood() {
    val unfamiliarMood = song("unfamiliar", "New Song", "New Artist", 210)
    val sameMood = song("same-mood", "Calm Song", "Another Artist", 220)

    assertEquals(
      listOf("same-mood", "unfamiliar"),
      AddonQueueTrackMatcher.rankByTaste(
        listOf(unfamiliarMood, sameMood),
        favoriteArtists = emptyList(),
        favoriteTrackKeys = emptySet(),
        currentMoodTags = setOf("calm", "ambient"),
        candidateMoodTags = mapOf("same-mood" to setOf("calm", "ambient")),
      ).map { it.id },
    )
  }

  @Test
  fun leavesYouTubeOrderWhenMoodMetadataIsMissing() {
    val first = song("first", "First Song", "One", 180)
    val second = song("second", "Second Song", "Two", 190)

    assertEquals(
      listOf("first", "second"),
      AddonQueueTrackMatcher.rankByTaste(
        listOf(first, second),
        favoriteArtists = emptyList(),
        favoriteTrackKeys = emptySet(),
        currentMoodTags = setOf("calm"),
        candidateMoodTags = emptyMap(),
      ).map { it.id },
    )
  }

}
