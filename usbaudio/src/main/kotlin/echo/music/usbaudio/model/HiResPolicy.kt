package echo.music.usbaudio.model

/** Quality tier for a decoded format. Hi-Res follows the JAS rule: above 48 kHz or above 16-bit. */
enum class HiResTier(val label: String) {
  STANDARD("Standard"),
  HI_RES("Hi-Res"),
}

/**
 * Pure Hi-Res policy used by both the USB-DAC routing decision and the player badge.
 * All functions are total and testable without Android: unknown values never claim Hi-Res.
 */
object HiResPolicy {

  /** User cap stored as "auto" | "48000" | "96000" | "192000". */
  fun parseUserCap(userCap: String?): Int? =
    when (userCap?.trim()?.lowercase()) {
      null, "", "auto", "none" -> null
      else -> userCap.trim().toIntOrNull()?.takeIf { it >= 44100 }
    }

  /** True only when the format is genuinely above CD quality. Mirrors the Bluetooth telemetry rule. */
  fun isHiRes(sampleRateHz: Int?, bitDepth: Int?): Boolean =
    (sampleRateHz != null && sampleRateHz > 48000) || (bitDepth != null && bitDepth > 16)

  fun tierOf(sampleRateHz: Int?, bitDepth: Int?): HiResTier =
    if (isHiRes(sampleRateHz, bitDepth)) HiResTier.HI_RES else HiResTier.STANDARD

  /**
   * Effective maximum rate: the DAC's highest rate, further limited by the user cap.
   * Returns null when the DAC rates are unknown (never invent a ceiling the DAC may not support).
   */
  fun effectiveMaxRate(dacRates: Collection<Int>, userCap: String?): Int? {
    val dacMax = dacRates.maxOrNull() ?: return null
    val cap = parseUserCap(userCap) ?: return dacMax
    return if (cap < dacMax) cap else dacMax
  }

  /**
   * Best negotiated rate for a source track: the highest DAC rate that does not exceed
   * min(source rate, effective max). Null when no DAC rate qualifies.
   */
  fun bestNegotiatedRate(dacRates: Collection<Int>, sourceRateHz: Int, userCap: String?): Int? {
    if (dacRates.isEmpty() || sourceRateHz <= 0) return null
    val max = effectiveMaxRate(dacRates, userCap) ?: return null
    val limit = if (sourceRateHz < max) sourceRateHz else max
    return dacRates.filter { it <= limit }.maxOrNull()
  }

  /** Short honest label for badges, e.g. "96 kHz / 24-bit". Unknown parts are omitted. */
  fun formatLabel(sampleRateHz: Int?, bitDepth: Int?): String {
    val parts = buildList {
      if (sampleRateHz != null && sampleRateHz > 0) {
        add(
          if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000} kHz"
          else "%.1f kHz".format(java.util.Locale.US, sampleRateHz / 1000.0),
        )
      }
      if (bitDepth != null && bitDepth > 0) add("$bitDepth-bit")
    }
    return parts.joinToString(" / ")
  }
}
