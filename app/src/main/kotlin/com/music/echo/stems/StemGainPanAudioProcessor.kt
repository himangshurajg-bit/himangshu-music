package echo.music.iad1tya.stems

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Constant-power gain + pan audio processor for stem playback.
 *
 * Handles 16-bit PCM stereo — the format RajTune's stems are rendered into.
 * This is the piece that makes per-stem volume, mute, solo and pan real audio
 * operations instead of UI-only sliders.
 */
@UnstableApi
class StemGainPanAudioProcessor : AudioProcessor {

  private var sampleRate = 0
  private var channelCount = 0
  private var encoding = C.ENCODING_INVALID
  private var active = false

  private var outputBuffer: ByteBuffer = EMPTY_BUFFER
  private var inputEnded = false

  /** Linear gain multiplier (0..2 recommended). 1 = unity. */
  @Volatile var gain: Double = 1.0

  /** Pan: -1 left, 0 centre, +1 right (constant-power). */
  @Volatile var pan: Float = 0f

  companion object {
    private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
  }

  override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    sampleRate = inputAudioFormat.sampleRate
    channelCount = inputAudioFormat.channelCount
    encoding = inputAudioFormat.encoding
    if (encoding != C.ENCODING_PCM_16BIT || channelCount != 2) {
      throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    }
    active = true
    return inputAudioFormat
  }

  override fun isActive(): Boolean = active

  override fun queueInput(input: ByteBuffer) {
    if (input.remaining() == 0) return
    val totalBytes = input.remaining()
    outputBuffer = ByteBuffer.allocateDirect(totalBytes).order(ByteOrder.nativeOrder())

    val panClamped = pan.coerceIn(-1f, 1f)
    val angle = (panClamped + 1.0) * Math.PI / 4.0
    val leftGain = (gain * cos(angle)).coerceIn(0.0, 2.0)
    val rightGain = (gain * sin(angle)).coerceIn(0.0, 2.0)

    while (input.hasRemaining()) {
      val l = input.getShort().toInt()
      val r = input.getShort().toInt()
      val outL = (l * leftGain).roundToIntClamp()
      val outR = (r * rightGain).roundToIntClamp()
      outputBuffer.putShort(outL.toShort())
      outputBuffer.putShort(outR.toShort())
    }
    outputBuffer.flip()
  }

  override fun getOutput(): ByteBuffer {
    val out = outputBuffer
    outputBuffer = EMPTY_BUFFER
    return out
  }

  override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

  override fun queueEndOfStream() {
    inputEnded = true
  }

  override fun reset() {
    sampleRate = 0
    channelCount = 0
    encoding = C.ENCODING_INVALID
    active = false
    inputEnded = false
    outputBuffer = EMPTY_BUFFER
  }

  override fun flush() {
    outputBuffer = EMPTY_BUFFER
    inputEnded = false
  }
}

private fun cos(x: Double): Double = Math.cos(x)
private fun sin(x: Double): Double = Math.sin(x)

private fun Double.roundToIntClamp(): Int = this.toInt().coerceIn(-32768, 32767)
