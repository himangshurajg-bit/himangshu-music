package echo.music.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class EightDAudioProcessorTest {
  @Test
  fun disabledModeCopiesPcm16Exactly() {
    val processor = EightDAudioProcessor()
    processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
    val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
      .putShort(12_345).putShort((-23_456).toShort()).putShort(1).putShort((-1).toShort())
    input.flip()
    val expected = ByteArray(input.remaining())
    input.duplicate().get(expected)

    processor.queueInput(input)
    val outputBuffer = processor.getOutput()
    val output = ByteArray(outputBuffer.remaining())
    outputBuffer.get(output)

    assertTrue(expected.contentEquals(output))
  }

  @Test
  fun fastOrbitChangesStereoSignalAndKeepsFrameCount() {
    val processor = EightDAudioProcessor()
    processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
    processor.parameters = EightDAudioParameters(
      enabled = true,
      mode = EightDMotionMode.FAST_SPIN,
      rotationSpeed = 3f,
      spatialDepth = 1f,
      stereoWidth = 0.5f,
      intensity = 1f,
    )
    val frameCount = 5_000
    val input = ByteBuffer.allocateDirect(frameCount * 4).order(ByteOrder.nativeOrder())
    repeat(frameCount) { input.putShort(8_000).putShort(8_000) }
    input.flip()

    processor.queueInput(input)
    val output = processor.getOutput()
    assertEquals(frameCount * 4, output.remaining())
    var differs = false
    repeat(frameCount) {
      val left = output.getShort()
      val right = output.getShort()
      if (kotlin.math.abs(left - right) > 50) differs = true
    }
    assertTrue("Enabled orbit should move centered content between channels", differs)
  }

  @Test
  fun stereoWidthControlChangesTheProcessedSignal() {
    val inputSamples = shortArrayOf(10_000, 0)
    val narrow = processOneFrame(inputSamples, EightDAudioParameters(
      enabled = true, mode = EightDMotionMode.STATIC_SPATIAL, spatialDepth = 0f,
      stereoWidth = 0f, intensity = 1f,
    ))
    val wide = processOneFrame(inputSamples, EightDAudioParameters(
      enabled = true, mode = EightDMotionMode.STATIC_SPATIAL, spatialDepth = 0f,
      stereoWidth = 1f, intensity = 1f,
    ))
    assertNotEquals(narrow[0], wide[0])
    assertNotEquals(narrow[1], wide[1])
  }

  @Test
  fun floatPcmIsSupportedAndLimitedToRange() {
    val processor = EightDAudioProcessor()
    processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
    processor.parameters = EightDAudioParameters(
      enabled = true, mode = EightDMotionMode.STATIC_SPATIAL, spatialDepth = 0f,
      stereoWidth = 1f, bassEnhancement = 1f, intensity = 1f,
    )
    val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
      .putFloat(1f).putFloat(-1f)
    input.flip()
    processor.queueInput(input)
    val output = processor.getOutput()
    assertEquals(8, output.remaining())
    repeat(2) { assertTrue(kotlin.math.abs(output.float) <= 1f) }
  }

  @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
  fun rejectsMultichannelInsteadOfMisprocessingIt() {
    EightDAudioProcessor().configure(AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT))
  }

  private fun processOneFrame(samples: ShortArray, parameters: EightDAudioParameters): ShortArray {
    val processor = EightDAudioProcessor()
    processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
    processor.parameters = parameters
    val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
      .putShort(samples[0]).putShort(samples[1])
    input.flip()
    processor.queueInput(input)
    val output = processor.getOutput()
    return shortArrayOf(output.getShort(), output.getShort())
  }
}
