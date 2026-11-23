package echo.music.iad1tya.ui.screens.search

import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.pages.MoodAndGenres
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchGenreCategoryKeyTest {
  @Test
  fun duplicateCategoryAndDestinationAreDisplayedOnce() {
    val commute = category("Commute", "FEmusic_moods_and_genres_category:Commute")

    assertEquals(
      listOf(commute),
      uniqueSearchGenreCategories(listOf(commute, commute.copy(stripeColor = 42L))),
    )
  }

  @Test
  fun distinctParamsKeepSameNamedBrowseCategoriesSeparate() {
    val first = category("Commute", "FEmusic_moods_and_genres_category:Commute", "first")
    val second = category("Commute", "FEmusic_moods_and_genres_category:Commute", "second")

    assertEquals(listOf(first, second), uniqueSearchGenreCategories(listOf(first, second)))
    assertEquals(2, listOf(first, second).map { it.searchGenreCategoryKey() }.distinct().size)
  }

  @Test
  fun keyPartsCannotCollideWhenTheirTextContainsSeparators() {
    val first = category("a:b", "c", "d")
    val second = category("a", "b:c", "d")

    assertEquals(2, listOf(first, second).map { it.searchGenreCategoryKey() }.distinct().size)
  }

  private fun category(title: String, browseId: String, params: String? = null) =
    MoodAndGenres.Item(
      title = title,
      stripeColor = 0L,
      endpoint = BrowseEndpoint(browseId = browseId, params = params),
    )
}
