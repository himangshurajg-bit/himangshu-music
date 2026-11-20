package echo.music.iad1tya.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIconType(val value: Int) {
  DEFAULT(0),
  LEGACY(1),
  STATIC(2),
  CAT(3),
  CRAZY_BLUE(4),
  POOKIE(5),
  SKY(6),
  ECHO_CAT(7),
  BILLIE_EILISH(13),
  SABRINA_CARPENTER(14),
  SABRINA_CARPENTER_2(15),
  CRAFT(16),
  SADIE(17),
  NEON_GLASS(18),
  MILLIE_BOBBY_BROWN(19),
  CLEF_SILVER_BLUE(24),
  CLEF_VIOLET_BLUE(25),
  CLEF_SILVER(26),
  CLEF_CRIMSON(27),
  CLEF_GOLD(28),
  CLEF_ELECTRIC_BLUE(29),
  CLEF_AMETHYST(30),
  CLEF_MIDNIGHT(31),
  CLEF_OPAL(32),
  CLEF_TURQUOISE(33),
  CLEF_ROYAL_BLUE(34),
  CLEF_ROSE_COPPER(35),
  CLEF_MAGENTA_ORANGE(36),
  CLEF_GOLD_BLUE(37),
  CLEF_MONOCHROME(38),
  CLEF_MONOCHROME_ALT(39),
  USER_CLEF_MONOCHROME(40),
  ITACHI_RED_1(41),
  BEE_YELLOW(42),
  CLEF_RED_CIRCLE(43),
  ITACHI_RED_2(44),
  ANIME_MUSIC_BLUE(45),
  HEADPHONES_NEON(46)
}

object IconUtils {
  fun setIcon(context: Context, iconType: AppIconType) {
    val pm = context.packageManager

    val dynamic = ComponentName(context, "echo.music.iad1tya.MainActivityAlias")
    val legacy = ComponentName(context, "echo.music.iad1tya.MainActivityLegacy")
    val static = ComponentName(context, "echo.music.iad1tya.MainActivityStatic")
    val cat = ComponentName(context, "echo.music.iad1tya.MainActivityCat")
    val crazyBlue = ComponentName(context, "echo.music.iad1tya.MainActivityCrazyBlue")
    val pookie = ComponentName(context, "echo.music.iad1tya.MainActivityPookie")
    val sky = ComponentName(context, "echo.music.iad1tya.MainActivitySky")
    val echoCat = ComponentName(context, "echo.music.iad1tya.MainActivityEchoCat")
    val billieEilish = ComponentName(context, "echo.music.iad1tya.MainActivityBillieEilish")
    val sabrina = ComponentName(context, "echo.music.iad1tya.MainActivitySabrina")
    val sabrina2 = ComponentName(context, "echo.music.iad1tya.MainActivitySabrina2")
    val craft = ComponentName(context, "echo.music.iad1tya.MainActivityCraft")
    val sadie = ComponentName(context, "echo.music.iad1tya.MainActivitySadie")
    val neonGlass = ComponentName(context, "echo.music.iad1tya.MainActivityNeonGlass")
    val millie = ComponentName(context, "echo.music.iad1tya.MainActivityMillie")
    val clefSilverBlue = ComponentName(context, "echo.music.iad1tya.MainActivityClefSilverBlue")
    val clefVioletBlue = ComponentName(context, "echo.music.iad1tya.MainActivityClefVioletBlue")
    val clefSilver = ComponentName(context, "echo.music.iad1tya.MainActivityClefSilver")
    val clefCrimson = ComponentName(context, "echo.music.iad1tya.MainActivityClefCrimson")
    val clefGold = ComponentName(context, "echo.music.iad1tya.MainActivityClefGold")
    val clefElectricBlue = ComponentName(context, "echo.music.iad1tya.MainActivityClefElectricBlue")
    val clefAmethyst = ComponentName(context, "echo.music.iad1tya.MainActivityClefAmethyst")
    val clefMidnight = ComponentName(context, "echo.music.iad1tya.MainActivityClefMidnight")
    val clefOpal = ComponentName(context, "echo.music.iad1tya.MainActivityClefOpal")
    val clefTurquoise = ComponentName(context, "echo.music.iad1tya.MainActivityClefTurquoise")
    val clefRoyalBlue = ComponentName(context, "echo.music.iad1tya.MainActivityClefRoyalBlue")
    val clefRoseCopper = ComponentName(context, "echo.music.iad1tya.MainActivityClefRoseCopper")
    val clefMagentaOrange = ComponentName(context, "echo.music.iad1tya.MainActivityClefMagentaOrange")
    val clefGoldBlue = ComponentName(context, "echo.music.iad1tya.MainActivityClefGoldBlue")
    val clefMonochrome = ComponentName(context, "echo.music.iad1tya.MainActivityClefMonochrome")
    val clefMonochromeAlt = ComponentName(context, "echo.music.iad1tya.MainActivityClefMonochromeAlt")
    val userClefMonochrome = ComponentName(context, "echo.music.iad1tya.MainActivityUserClefMonochrome")
    val itachiRed1 = ComponentName(context, "echo.music.iad1tya.MainActivityItachiRed1")
    val beeYellow = ComponentName(context, "echo.music.iad1tya.MainActivityBeeYellow")
    val clefRedCircle = ComponentName(context, "echo.music.iad1tya.MainActivityClefRedCircle")
    val itachiRed2 = ComponentName(context, "echo.music.iad1tya.MainActivityItachiRed2")
    val animeMusicBlue = ComponentName(context, "echo.music.iad1tya.MainActivityAnimeMusicBlue")
    val headphonesNeon = ComponentName(context, "echo.music.iad1tya.MainActivityHeadphonesNeon")

    pm.setComponentEnabledSetting(
      dynamic,
      if (iconType == AppIconType.DEFAULT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      legacy,
      if (iconType == AppIconType.LEGACY) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      static,
      if (iconType == AppIconType.STATIC) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      cat,
      if (iconType == AppIconType.CAT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      crazyBlue,
      if (iconType == AppIconType.CRAZY_BLUE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      pookie,
      if (iconType == AppIconType.POOKIE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      sky,
      if (iconType == AppIconType.SKY) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      echoCat,
      if (iconType == AppIconType.ECHO_CAT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      billieEilish,
      if (iconType == AppIconType.BILLIE_EILISH) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      sabrina,
      if (iconType == AppIconType.SABRINA_CARPENTER) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      sabrina2,
      if (iconType == AppIconType.SABRINA_CARPENTER_2)
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      craft,
      if (iconType == AppIconType.CRAFT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      sadie,
      if (iconType == AppIconType.SADIE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      neonGlass,
      if (iconType == AppIconType.NEON_GLASS) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      millie,
      if (iconType == AppIconType.MILLIE_BOBBY_BROWN) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefSilverBlue,
      if (iconType == AppIconType.CLEF_SILVER_BLUE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefVioletBlue,
      if (iconType == AppIconType.CLEF_VIOLET_BLUE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefSilver,
      if (iconType == AppIconType.CLEF_SILVER) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefCrimson,
      if (iconType == AppIconType.CLEF_CRIMSON) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefGold,
      if (iconType == AppIconType.CLEF_GOLD) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefElectricBlue,
      if (iconType == AppIconType.CLEF_ELECTRIC_BLUE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefAmethyst,
      if (iconType == AppIconType.CLEF_AMETHYST) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefMidnight,
      if (iconType == AppIconType.CLEF_MIDNIGHT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefOpal,
      if (iconType == AppIconType.CLEF_OPAL) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefTurquoise,
      if (iconType == AppIconType.CLEF_TURQUOISE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefRoyalBlue,
      if (iconType == AppIconType.CLEF_ROYAL_BLUE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefRoseCopper,
      if (iconType == AppIconType.CLEF_ROSE_COPPER) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefMagentaOrange,
      if (iconType == AppIconType.CLEF_MAGENTA_ORANGE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefGoldBlue,
      if (iconType == AppIconType.CLEF_GOLD_BLUE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefMonochrome,
      if (iconType == AppIconType.CLEF_MONOCHROME) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefMonochromeAlt,
      if (iconType == AppIconType.CLEF_MONOCHROME_ALT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      userClefMonochrome,
      if (iconType == AppIconType.USER_CLEF_MONOCHROME) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      itachiRed1,
      if (iconType == AppIconType.ITACHI_RED_1) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      beeYellow,
      if (iconType == AppIconType.BEE_YELLOW) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      clefRedCircle,
      if (iconType == AppIconType.CLEF_RED_CIRCLE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      itachiRed2,
      if (iconType == AppIconType.ITACHI_RED_2) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      animeMusicBlue,
      if (iconType == AppIconType.ANIME_MUSIC_BLUE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      headphonesNeon,
      if (iconType == AppIconType.HEADPHONES_NEON) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
  }
}
