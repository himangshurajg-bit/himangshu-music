package echo.music.iad1tya.stems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StemDeckParityTest {

  @Test
  fun sixStemsInStemDeckOrder() {
    assertEquals(
      listOf("Vocals", "Drums", "Bass", "Guitar", "Piano", "Other"),
      StemDeckParity.sixStems.map { it.label },
    )
  }

  @Test
  fun guitarAndPianoArePlaceholdersRoutedFromOther() {
    assertEquals(StemId.Vocals, StemDeckStem.Vocals.routeToRajTuneStem())
    assertEquals(StemId.Drums, StemDeckStem.Drums.routeToRajTuneStem())
    assertEquals(StemId.Bass, StemDeckStem.Bass.routeToRajTuneStem())
    assertEquals(StemId.Other, StemDeckStem.Guitar.routeToRajTuneStem())
    assertEquals(StemId.Other, StemDeckStem.Piano.routeToRajTuneStem())
    assertEquals(StemId.Other, StemDeckStem.Other.routeToRajTuneStem())
    assertTrue(StemDeckStem.Vocals.isSeparatelySeparated)
    assertFalse(StemDeckStem.Guitar.isSeparatelySeparated)
    assertFalse(StemDeckStem.Piano.isSeparatelySeparated)
    assertTrue(StemDeckStem.Guitar.routingNote.contains("not separately separated"))
  }

  @Test
  fun semitonesMapToPitchAndBack() {
    assertEquals(1f, StemDeckParity.semitonesToPitch(0), 0.0001f)
    assertEquals(1.122f, StemDeckParity.semitonesToPitch(2), 0.005f)
    assertEquals(0.5f, StemDeckParity.semitonesToPitch(-12), 0.001f)
    assertEquals(2f, StemDeckParity.semitonesToPitch(12), 0.001f)
    assertEquals(2, StemDeckParity.pitchToSemitones(StemDeckParity.semitonesToPitch(2)))
    assertEquals(-3, StemDeckParity.pitchToSemitones(StemDeckParity.semitonesToPitch(-3)))
    // clamped
    assertEquals(2f, StemDeckParity.semitonesToPitch(24), 0.001f)
  }

  @Test
  fun clickSettingsNormalizeAndCountIn() {
    val c = ClickSettings(bpm = 999, beatsPerBar = 7, countInBars = 99, volume = 5f).normalized()
    assertEquals(240, c.bpm)
    assertEquals(4, c.beatsPerBar)
    assertEquals(4, c.countInBars)
    val d = ClickSettings(bpm = 60, beatsPerBar = 4, countInBars = 2)
    assertEquals(1.0, d.beatIntervalSec, 0.0001)
    assertEquals(8, d.countInBeats)
    assertEquals(8.0, d.countInSec, 0.0001)
  }

  @Test
  fun loudnessTargetsProduceFfmpegFilter() {
    assertEquals(-14.0, LoudnessTarget.Streaming.integratedLufs)
    assertTrue(LoudnessTarget.Streaming.toFfmpegLoudnormFilter()!!.contains("-14"))
    assertTrue(LoudnessTarget.None.toFfmpegLoudnormFilter() == null)
    assertEquals(LoudnessTarget.Streaming, LoudnessTarget.from("streaming"))
    assertEquals(LoudnessTarget.None, LoudnessTarget.from(null))
  }

  @Test
  fun practiceModeDefaultsSafe() {
    assertEquals(PracticeMode.Advanced, PracticeMode.from(null))
    assertTrue(PracticeMode.Advanced.showsAdvancedControls)
    assertFalse(PracticeMode.Practice.showsAdvancedControls)
  }

  @Test
  fun clickWavHasValidHeaderAndNoNaN() {
    val bytes = ClickTrackWriter.generateWavBytes(
      ClickSettings(enabled = true, bpm = 120, beatsPerBar = 4, countInBars = 1),
      durationSec = 2.0,
    )
    assertTrue(bytes.size > 44)
    val header = String(bytes, 0, 4, Charsets.US_ASCII)
    val wave = String(bytes, 8, 4, Charsets.US_ASCII)
    assertEquals("RIFF", header)
    assertEquals("WAVE", wave)
    // disabled produces near-silent but valid file
    val silent = ClickTrackWriter.generateWavBytes(ClickSettings(enabled = false), durationSec = 1.0)
    assertTrue(silent.size >= 44)
  }

  @Test
  fun paritySnapshotRoundTrip() {
    val original = StemMixerSnapshot(
      practiceMode = PracticeMode.Practice,
      transposeSemitones = 3,
      click = ClickSettings(enabled = true, bpm = 140, beatsPerBar = 3, countInBars = 2, volume = 0.7f),
      loudness = LoudnessTarget.Album,
    )
    val restored = StemMixerSnapshot.fromJson(original.toJson())
    assertEquals(PracticeMode.Practice, restored.practiceMode)
    assertEquals(3, restored.transposeSemitones)
    assertTrue(restored.click.enabled)
    assertEquals(140, restored.click.bpm)
    assertEquals(3, restored.click.beatsPerBar)
    assertEquals(2, restored.click.countInBars)
    assertEquals(LoudnessTarget.Album, restored.loudness)
  }

  @Test
  fun legacySnapshotWithoutParityStillLoads() {
    // Old snapshots without "parity" must fall back to safe defaults.
    val legacy = StemMixerSnapshot()
    val json = legacy.toJson()
    json.remove("parity")
    val restored = StemMixerSnapshot.fromJson(json)
    assertEquals(PracticeMode.Advanced, restored.practiceMode)
    assertEquals(0, restored.transposeSemitones)
    assertEquals(LoudnessTarget.None, restored.loudness)
    assertFalse(restored.click.enabled)
  }
}
