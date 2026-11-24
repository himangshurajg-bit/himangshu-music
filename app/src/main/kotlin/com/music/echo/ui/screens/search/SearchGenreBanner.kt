package echo.music.iad1tya.ui.screens.search

import echo.music.iad1tya.R

/** Selects original, locally bundled artwork for well-known mood/genre names. */
internal fun searchGenreBannerForTitle(title: String): Int {
  val normalized = title.lowercase().replace('-', ' ').trim()
  return when {
    normalized.containsAny("commute", "driving", "road trip", "travel") ->
      R.drawable.mood_genre_banner_commute
    normalized.containsAny("feel good", "happy", "uplifting") ->
      R.drawable.mood_genre_banner_feel_good
    normalized.containsAny("country", "americana") ->
      R.drawable.mood_genre_banner_country
    normalized.contains("desi") && normalized.containsAny("hip hop", "rap") ->
      R.drawable.mood_genre_banner_desi_hip_hop
    normalized.containsAny("chill", "calm", "relax", "sleep", "focus") ->
      R.drawable.mood_genre_banner_chill
    normalized.containsAny("pop", "energize", "party", "dance", "2010s") ->
      R.drawable.mood_genre_banner_pop
    else -> R.drawable.mood_genre_banner_general
  }
}

private fun String.containsAny(vararg terms: String): Boolean = terms.any(::contains)
