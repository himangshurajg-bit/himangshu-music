package echo.music.iad1tya.stems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StemModelRepositoryTest {
  private val modelNames = listOf("bass", "drums", "other", "vocals")

  @Test
  fun extractsKnownOnnxLeafNamesFromModelArchivePaths() {
    assertEquals("bass", modelStemNameFromZipEntry("onnx_B/bass.onnx", modelNames))
    assertEquals("drums", modelStemNameFromZipEntry("onnx_B/drums.onnx", modelNames))
    assertEquals("other", modelStemNameFromZipEntry("onnx_B/other.onnx", modelNames))
    assertEquals("vocals", modelStemNameFromZipEntry("onnx_B/vocals.onnx", modelNames))
  }

  @Test
  fun ignoresUnknownFilesAndNonOnnxEntries() {
    assertNull(modelStemNameFromZipEntry("onnx_B/config.json", modelNames))
    assertNull(modelStemNameFromZipEntry("onnx_B/piano.onnx", modelNames))
    assertNull(modelStemNameFromZipEntry("onnx_B/bass.onnx/", modelNames))
  }
}
