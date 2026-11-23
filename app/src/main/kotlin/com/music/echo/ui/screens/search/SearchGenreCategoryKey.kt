package echo.music.iad1tya.ui.screens.search

import com.music.innertube.pages.MoodAndGenres

/** A saveable, collision-free LazyGrid key for one displayed mood/genre destination. */
internal fun MoodAndGenres.Item.searchGenreCategoryKey(): String =
  buildString {
    appendKeyPart(endpoint.browseId)
    appendKeyPart(endpoint.params)
    appendKeyPart(title)
  }

/** Repeated API entries for the same card and destination should only be displayed once. */
internal fun uniqueSearchGenreCategories(
  categories: List<MoodAndGenres.Item>,
): List<MoodAndGenres.Item> = categories.distinctBy { it.searchGenreCategoryKey() }

private fun StringBuilder.appendKeyPart(value: String?) {
  if (value == null) {
    append("-1:")
  } else {
    append(value.length).append(':').append(value)
  }
}
