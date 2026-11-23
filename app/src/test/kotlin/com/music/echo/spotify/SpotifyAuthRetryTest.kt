package echo.music.iad1tya.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyAuthRetryTest {
  @Test
  fun transientFailuresUseBoundedBackoff() {
    assertEquals(250L, spotifyAuthRetryDelayMillis(null, null, 0))
    assertEquals(500L, spotifyAuthRetryDelayMillis(503, null, 1))
    assertEquals(1_000L, spotifyAuthRetryDelayMillis(429, null, 2))
  }

  @Test
  fun retryAfterIsHonoredAndCapped() {
    assertEquals(3_000L, spotifyAuthRetryDelayMillis(429, 3L, 0))
    assertEquals(5_000L, spotifyAuthRetryDelayMillis(503, Long.MAX_VALUE, 0))
  }

  @Test
  fun permanentFailuresAndExhaustedAttemptsDoNotRetry() {
    assertNull(spotifyAuthRetryDelayMillis(401, null, 0))
    assertNull(spotifyAuthRetryDelayMillis(429, null, 3))
  }
}
