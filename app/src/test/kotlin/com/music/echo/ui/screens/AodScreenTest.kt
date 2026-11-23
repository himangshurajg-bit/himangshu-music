package echo.music.iad1tya.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class AodScreenTest {
  @Test
  fun progressIsBoundedAndHandlesUnknownDuration() {
    assertEquals(0f, aodProgress(5_000L, 0L))
    assertEquals(0f, aodProgress(-1L, 10_000L))
    assertEquals(0.5f, aodProgress(5_000L, 10_000L))
    assertEquals(1f, aodProgress(20_000L, 10_000L))
  }

  @Test
  fun timeFormattingClampsNegativeAndUsesMinutesAndSeconds() {
    assertEquals("0:00", formatAodTime(-5L))
    assertEquals("1:09", formatAodTime(69_000L))
    assertEquals("62:03", formatAodTime(3_723_000L))
  }
}
