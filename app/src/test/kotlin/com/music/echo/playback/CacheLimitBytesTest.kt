package com.music.echo.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheLimitBytesTest {
  @Test fun zeroDisablesCache() {
    assertEquals(0L, cacheLimitBytes(0))
  }

  @Test fun megabyteLimitConvertsToBytes() {
    assertEquals(1024L * 1024L * 1024L, cacheLimitBytes(1024))
  }

  @Test fun negativeLimitMeansUnlimited() {
    assertEquals(Long.MAX_VALUE, cacheLimitBytes(-1))
  }
}
