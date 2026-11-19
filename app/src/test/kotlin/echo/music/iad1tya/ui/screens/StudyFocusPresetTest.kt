package echo.music.iad1tya.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFocusPresetTest {
  @Test
  fun exposesTenDistinctTaskAwareSearchPresets() {
    assertEquals(10, studyFocusPresets.size)
    assertEquals(10, studyFocusPresets.map { it.title }.toSet().size)
    assertTrue(studyFocusPresets.all { it.query.isNotBlank() && it.description.isNotBlank() })
    assertEquals(
      setOf(
        "Deep Focus", "Mathematics Mode", "Physics Mode", "Reading Mode",
        "Memory & Revision", "Coding Mode", "Exam Preparation", "Relaxed Study",
        "Pomodoro Focus", "Night Study",
      ),
      studyFocusPresets.map { it.title }.toSet(),
    )
    assertFalse(studyFocusPresets.any { it.timerType != null && it.timerType !in setOf("focus", "pomodoro") })
  }
}
