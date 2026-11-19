package echo.music.iad1tya.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackdropPolicyTest {
  @Test
  fun explicitSettingsOptOutPreventsBackdropEvenWhenGlassIsEnabled() {
    assertFalse(
      shouldUseBackdropGlass(enableBackdropGlass = false, globalEnabled = true, sdkInt = 35)
    )
  }

  @Test
  fun enabledBackdropRequiresGlobalSettingAndSupportedAndroidVersion() {
    assertTrue(
      shouldUseBackdropGlass(enableBackdropGlass = true, globalEnabled = true, sdkInt = 35)
    )
    assertFalse(
      shouldUseBackdropGlass(enableBackdropGlass = true, globalEnabled = false, sdkInt = 35)
    )
    assertFalse(
      shouldUseBackdropGlass(enableBackdropGlass = true, globalEnabled = true, sdkInt = 30)
    )
  }
}
