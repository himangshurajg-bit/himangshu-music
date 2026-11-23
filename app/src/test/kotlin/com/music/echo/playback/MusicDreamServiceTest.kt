package echo.music.iad1tya.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicDreamServiceTest {
  @Test
  fun formatsNegativeAndMinutePositions() {
    assertEquals("0:00", formatDreamTime(-1L))
    assertEquals("1:05", formatDreamTime(65_900L))
  }

  @Test
  fun formatsHoursWithoutLosingTheMinuteField() {
    assertEquals("1:02:03", formatDreamTime(3_723_000L))
  }

  @Test
  fun selectsCurrentAndNextSyncedLyricLine() {
    val lyrics = "[00:00.00]First line\n[00:03.250]Second line\n[00:08.000]Third line"
    assertEquals("Second line\nThird line", dreamLyricsAt(lyrics, 4_000L))
  }

  @Test
  fun returnsFirstLinesBeforeLyricsStartAndHandlesPlainText() {
    assertEquals("First\nSecond", dreamLyricsAt("[00:01.000]First\n[00:02.000]Second", 0L))
    assertEquals("Plain lyric\nnext", dreamLyricsAt("Plain lyric\nnext", 1_000L))
    assertEquals("Lyrics unavailable", dreamLyricsAt(null, 0L))
  }
}
