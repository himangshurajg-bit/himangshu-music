package echo.music.usbaudio

import echo.music.usbaudio.model.HiResPolicy
import echo.music.usbaudio.model.HiResTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HiResPolicyTest {

  private val dacRates = listOf(44100, 48000, 88200, 96000, 176400, 192000)

  @Test
  fun classifiesAbove48kOr16bitAsHiRes() {
    assertTrue(HiResPolicy.isHiRes(96000, 16))
    assertTrue(HiResPolicy.isHiRes(44100, 24))
    assertFalse(HiResPolicy.isHiRes(44100, 16))
    assertFalse(HiResPolicy.isHiRes(48000, 16))
    assertEquals(HiResTier.HI_RES, HiResPolicy.tierOf(192000, 24))
    assertEquals(HiResTier.STANDARD, HiResPolicy.tierOf(44100, 16))
  }

  @Test
  fun unknownValuesNeverClaimHiRes() {
    assertFalse(HiResPolicy.isHiRes(null, null))
    assertEquals(HiResTier.STANDARD, HiResPolicy.tierOf(null, null))
  }

  @Test
  fun autoCapMeansDacMax() {
    assertEquals(192000, HiResPolicy.effectiveMaxRate(dacRates, "auto"))
    assertEquals(192000, HiResPolicy.effectiveMaxRate(dacRates, null))
  }

  @Test
  fun userCapLimitsBelowDacMax() {
    assertEquals(96000, HiResPolicy.effectiveMaxRate(dacRates, "96000"))
    // A cap above the DAC's max never exceeds what the DAC reported.
    assertEquals(192000, HiResPolicy.effectiveMaxRate(dacRates, "384000"))
  }

  @Test
  fun unknownDacRatesYieldNoCeiling() {
    assertNull(HiResPolicy.effectiveMaxRate(emptyList(), "auto"))
    assertNull(HiResPolicy.bestNegotiatedRate(emptyList(), 96000, "auto"))
  }

  @Test
  fun bestRatePicksNativeTrackRateUpToCap() {
    assertEquals(44100, HiResPolicy.bestNegotiatedRate(dacRates, 44100, "auto"))
    assertEquals(96000, HiResPolicy.bestNegotiatedRate(dacRates, 96000, "auto"))
    assertEquals(48000, HiResPolicy.bestNegotiatedRate(dacRates, 96000, "48000"))
    // Source above DAC max falls back to DAC max.
    assertEquals(192000, HiResPolicy.bestNegotiatedRate(dacRates, 384000, "auto"))
  }

  @Test
  fun formatLabelOmitsUnknownParts() {
    assertEquals("96 kHz / 24-bit", HiResPolicy.formatLabel(96000, 24))
    assertEquals("44.1 kHz / 16-bit", HiResPolicy.formatLabel(44100, 16))
  }
}
