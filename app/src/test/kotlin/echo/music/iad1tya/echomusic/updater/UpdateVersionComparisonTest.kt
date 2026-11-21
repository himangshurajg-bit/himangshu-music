package echo.music.iad1tya.echomusic.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionComparisonTest {
  @Test
  fun newerStableVersionsAreDetected() {
    assertTrue(isNewerVersion("v1.4.16", "1.4.15"))
    assertTrue(isNewerVersion("1.5.0", "1.4.99"))
    assertTrue(isNewerVersion("1.4.16", "b1.4.16"))
  }

  @Test
  fun sameOrOlderVersionsAreNotUpdates() {
    assertFalse(isNewerVersion("1.4.16", "v1.4.16"))
    assertFalse(isNewerVersion("1.4.15", "1.4.16"))
    assertFalse(isNewerVersion("b1.4.16", "1.4.16"))
  }

  @Test
  fun missingTrailingComponentsAreZero() {
    assertFalse(isNewerVersion("1.4", "1.4.0"))
    assertTrue(isNewerVersion("1.4.1", "1.4"))
  }
}
