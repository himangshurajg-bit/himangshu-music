package echo.music.iad1tya.stems

import echo.music.dsp.core.models.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StemMixerTest {

  @Test
  fun linearGainConvertsDbCorrectly() {
    assertEquals(1f, StemMixSettings(StemId.Vocals).linearGain(), 0.0001f)
    assertEquals(2f, StemMixSettings(StemId.Vocals, gainDb = 6.0206f).linearGain(), 0.01f)
    assertEquals(0f, StemMixSettings(StemId.Vocals, gainDb = -40f).linearGain(), 1f)
  }

  @Test
  fun panConstantPowerIsCenteredAtOneHalfSqrt2() {
    val (l, r) = panToConstantPowerGains(0f)
    assertEquals(0.7071f, l, 0.001f)
    assertEquals(0.7071f, r, 0.001f)
    val (ll, rr) = panToConstantPowerGains(-1f)
    assertEquals(1f, ll, 0.001f)
    assertEquals(0f, rr, 0.001f)
  }

  @Test
  fun soloMuteAudibilityLogic() {
    val vocals = StemMixSettings(StemId.Vocals, muted = false, soloed = true)
    val bassMute = StemMixSettings(StemId.Bass, soloed = false)
    assertTrue(vocals.effectiveAudible(someSoloed = true))
    assertFalse(bassMute.effectiveAudible(someSoloed = true))
    assertTrue(bassMute.effectiveAudible(someSoloed = false))
    assertFalse(StemMixSettings(StemId.Drums, muted = true).effectiveAudible(someSoloed = false))
  }

  @Test
  fun presetsExistForAllStemsAndMaster() {
    assertEquals(8, StemMixPresets.all().size)
    for (id in StemId.entries) {
      assertTrue("presets for $id", StemMixPresets.presetsFor(id).isNotEmpty())
      assertTrue(StemMixPresets.presetsFor(id).any { it.name == "Flat" })
    }
    assertEquals(StemMixPresets.masterPresets().size, StemMixPresets.all().size)
  }

  @Test
  fun flatPresetHasZeroGainsAndSixBands() {
    val flat = StemMixPresets.flat()
    assertEquals(6, flat.eqBands.size)
    assertTrue(flat.eqBands.all { it.gainDb == 0.0 })
    assertEquals(listOf(63.0, 250.0, 1000.0, 4000.0, 8000.0, 12000.0), flat.eqBands.map { it.frequency })
  }

  @Test
  fun snapshotRoundTripPreservesSettings() {
    val original = StemMixerSnapshot(
      stems = StemId.entries.associateWith { id ->
        StemMixSettings(id, gainDb = 3f, pan = 0.5f, muted = true, soloed = false)
      },
      master = MasterMixSettings(masterGainDb = 2f, speed = 1.25f, pitch = 0.9f, loopEnabled = true),
    )
    val restored = StemMixerSnapshot.fromJson(original.toJson())
    assertEquals(original.master.masterGainDb, restored.master.masterGainDb)
    assertEquals(original.master.speed, restored.master.speed)
    assertEquals(original.master.pitch, restored.master.pitch)
    assertEquals(original.master.loopEnabled, restored.master.loopEnabled)
    assertEquals(original.stems[StemId.Vocals]?.gainDb, restored.stems[StemId.Vocals]?.gainDb)
    assertEquals(original.stems[StemId.Vocals]?.pan, restored.stems[StemId.Vocals]?.pan)
    assertEquals(original.stems[StemId.Vocals]?.muted, restored.stems[StemId.Vocals]?.muted)
    assertEquals(6, restored.stems[StemId.Vocals]?.eqBands?.size)
  }

  @Test
  fun presetsContainShelves() {
    assertTrue(StemMixPresets.bassBoost().eqBands.any { it.type == FilterType.LSC })
    assertTrue(StemMixPresets.drumPunch().eqBands.any { it.type == FilterType.HSC })
  }
}
