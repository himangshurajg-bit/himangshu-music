package echo.music.iad1tya.stems

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class StemGainPanAudioProcessorTest {

  private val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)

  @Test
  fun unityGainCenterPanScalesByConstantPower() {
    val processor = StemGainPanAudioProcessor()
    processor.configure(format)
    processor.gain = 1.0
    processor.pan = 0f
    val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
    input.putShort(1234.toShort())
    input.putShort((-5678).toShort())
    input.flip()
    processor.queueInput(input)
    val out = processor.getOutput()
    val g = 0.70710678
    assertEquals((1234 * g).toInt().coerceIn(-32768, 32767).toShort(), out.getShort())
    assertEquals((-5678 * g).toInt().coerceIn(-32768, 32767).toShort(), out.getShort())
  }

  @Test
  fun zeroGainSilences() {
    val processor = StemGainPanAudioProcessor()
    processor.configure(format)
    processor.gain = 0.0
    val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
    input.putShort(16384.toShort())
    input.putShort(16384.toShort())
    input.flip()
    processor.queueInput(input)
    val out = processor.getOutput()
    assertEquals(0.toShort(), out.getShort())
    assertEquals(0.toShort(), out.getShort())
  }

  @Test
  fun hardLeftPanSilencesRight() {
    val processor = StemGainPanAudioProcessor()
    processor.configure(format)
    processor.gain = 1.0
    processor.pan = -1f
    val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
    input.putShort(16384.toShort())
    input.putShort(16384.toShort())
    input.flip()
    processor.queueInput(input)
    val out = processor.getOutput()
    assertEquals(16384.toShort(), out.getShort())
    assertEquals(0.toShort(), out.getShort())
  }

  @Test
  fun doubleGainDoublesSample() {
    val processor = StemGainPanAudioProcessor()
    processor.configure(format)
    processor.gain = 2.0
    processor.pan = 0f
    val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
    input.putShort(8000.toShort())
    input.putShort(8000.toShort())
    input.flip()
    processor.queueInput(input)
    val out = processor.getOutput()
    val g = 2.0 * 0.7071
    assertEquals((8000 * g).toInt().coerceIn(-32768, 32767).toShort(), out.getShort())
    assertEquals((8000 * g).toInt().coerceIn(-32768, 32767).toShort(), out.getShort())
  }
}
