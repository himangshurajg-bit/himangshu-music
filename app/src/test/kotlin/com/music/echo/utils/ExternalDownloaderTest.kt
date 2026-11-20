package echo.music.iad1tya.utils

import echo.music.iad1tya.constants.DefaultExternalDownloaderPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExternalDownloaderTest {
  private val installed =
    listOf(
      ExternalDownloaderApp("com.deniscerri.ytdl", "YTDLnis"),
      ExternalDownloaderApp("org.example.downloader", "Example Downloader"),
    )

  @Test
  fun blankSettingKeepsExistingYtdlnisDefault() {
    assertEquals(
      DefaultExternalDownloaderPackage,
      resolveExternalDownloaderPackage("  ", installed),
    )
  }

  @Test
  fun resolvesPackageNameAndAppLabel() {
    assertEquals(
      "org.example.downloader",
      resolveExternalDownloaderPackage("org.example.downloader", installed),
    )
    assertEquals(
      "com.deniscerri.ytdl",
      resolveExternalDownloaderPackage("  ytdlnis ", installed),
    )
  }

  @Test
  fun buildsExplicitAudioShareIntentForConfiguredDownloader() {
    val intent = createExternalDownloaderIntent("org.example.downloader", "https://youtu.be/song")

    assertEquals(android.content.Intent.ACTION_SEND, intent.action)
    assertEquals("text/plain", intent.type)
    assertEquals("org.example.downloader", intent.`package`)
    assertEquals("https://youtu.be/song", intent.getStringExtra(android.content.Intent.EXTRA_TEXT))
    assertEquals("audio", intent.getStringExtra("TYPE"))
    assertEquals(false, intent.getBooleanExtra("BACKGROUND", true))
  }

  @Test
  fun refusesUnknownOrUninstalledDownloader() {
    assertNull(resolveExternalDownloaderPackage("com.other.downloader", emptyList()))
    assertNull(resolveExternalDownloaderPackage("Unknown Downloader", installed))
    assertNull(resolveExternalDownloaderPackage("", emptyList()))
  }
}
