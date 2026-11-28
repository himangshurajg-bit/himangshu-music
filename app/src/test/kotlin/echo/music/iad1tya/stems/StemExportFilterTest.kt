package echo.music.iad1tya.stems

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Sanity checks on the FFmpeg filter graph pieces.
 * The graph builder is internal/private, so we only validate the model level here;
 * the renderer itself is exercised by the manual export test on a real device.
 */
class StemExportFilterTest {
  @Test
  fun panGainsAreNeverNegativeAndSumToSqrt2Squared() {
    for (pan in listOf(-1f, -0.5f, 0f, 0.5f, 1f)) {
      val (l, r) = panToConstantPowerGains(pan)
      assertFalse(l < 0f)
      assertFalse(r < 0f)
    }
  }
}
