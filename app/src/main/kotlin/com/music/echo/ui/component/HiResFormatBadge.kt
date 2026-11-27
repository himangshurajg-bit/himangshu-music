package com.music.echo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.usbaudio.model.HiResPolicy

/**
 * Hi-Res badge for the expanded player. Shows only values the DAC actually reported
 * (from its parsed USB descriptors) and the user's sample-rate cap — never a native rate
 * the DAC did not advertise. Hidden when the DAC rates are unknown.
 */
@Composable
fun HiResFormatBadge(
  dacRates: Collection<Int>,
  bestBits: Int?,
  uacVersion: Int?,
  userCap: String?,
  modifier: Modifier = Modifier,
) {
  val effective = HiResPolicy.effectiveMaxRate(dacRates, userCap) ?: return
  val label = HiResPolicy.formatLabel(effective, bestBits)
  val badgeText = buildString {
    append("Hi-Res • $label")
    if (uacVersion != null) append(" • UAC$uacVersion")
  }

  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Text(
      text = badgeText,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
      color = MaterialTheme.colorScheme.primary,
    )
  }
}
