package echo.music.iad1tya.ui.screens.search

import echo.music.iad1tya.R
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchGenreBannerTest {
  @Test
  fun knownMoodAndGenreTitlesUseTheirMatchingOriginalBanner() {
    assertEquals(R.drawable.mood_genre_banner_commute, searchGenreBannerForTitle("Commute"))
    assertEquals(R.drawable.mood_genre_banner_feel_good, searchGenreBannerForTitle("Feel good"))
    assertEquals(R.drawable.mood_genre_banner_country, searchGenreBannerForTitle("Country & Americana"))
    assertEquals(R.drawable.mood_genre_banner_desi_hip_hop, searchGenreBannerForTitle("Desi hip-hop"))
    assertEquals(R.drawable.mood_genre_banner_chill, searchGenreBannerForTitle("Chill"))
    assertEquals(R.drawable.mood_genre_banner_pop, searchGenreBannerForTitle("2010s"))
  }

  @Test
  fun unrecognizedTitlesStillGetTheGeneralMusicBanner() {
    assertEquals(R.drawable.mood_genre_banner_general, searchGenreBannerForTitle("Indie"))
  }
}
