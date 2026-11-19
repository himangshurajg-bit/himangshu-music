package echo.music.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/** Stereo motion choices. These are stereo panning patterns, not HRTF/binaural rendering. */
enum class EightDMotionMode(val label: String, val description: String) {
  ORBIT("360° Orbit", "Smooth equal-power circular pan"),
  SLOW_MOTION("Slow Motion", "A gentle, slow stereo sweep"),
  FAST_SPIN("Fast Spin", "A quicker stereo sweep"),
  WAVE("Wave Motion", "A smooth back-and-forth sweep"),
  REVERSE_ORBIT("Reverse Orbit", "Orbit in the opposite direction"),
  RANDOM_ORBIT("Random Orbit", "Slow, smoothed, changing pan positions"),
  STATIC_SPATIAL("Static Spatial", "A wider stereo image without movement"),
  CUSTOM("Custom Motion", "Orbit shaped by your speed, depth and direction controls"),
}

/** Immutable snapshot copied from DataStore to the audio thread. */
data class EightDAudioParameters(
  val enabled: Boolean = false,
  val mode: EightDMotionMode = EightDMotionMode.ORBIT,
  val rotationSpeed: Float = 1f,
  val spatialDepth: Float = 0.7f,
  val stereoWidth: Float = 0.5f,
  val bassEnhancement: Float = 0f,
  val reverb: Float = 0f,
  val delay: Float = 0f,
  val intensity: Float = 0.8f,
  val reverseDirection: Boolean = false,
) {
  fun clamped() = copy(
    rotationSpeed = rotationSpeed.coerceIn(0.5f, 3f),
    spatialDepth = spatialDepth.coerceIn(0f, 1f),
    stereoWidth = stereoWidth.coerceIn(0f, 1f),
    bassEnhancement = bassEnhancement.coerceIn(0f, 1f),
    reverb = reverb.coerceIn(0f, 1f),
    delay = delay.coerceIn(0f, 1f),
    intensity = intensity.coerceIn(0f, 1f),
  )
}

/**
 * Low-latency Media3 processor for stereo PCM. The dry path is processed immediately; optional
 * ambience and echo are mixed from short parallel delay lines, so they do not add block latency.
 * Runtime changes are published as one immutable snapshot and smoothed on the audio thread.
 *
 * Unsupported encodings and multichannel layouts are left to Media3's normal fallback chain.
 * Mono remains mono (panning cannot create a stereo image from one channel).
 */
@UnstableApi
class EightDAudioProcessor : AudioProcessor {
  @Volatile
  var parameters: EightDAudioParameters = EightDAudioParameters()
    set(value) { field = value.clamped() }

  private var sampleRate = 0
  private var channelCount = 0
  private var encoding = C.ENCODING_INVALID
  private var active = false
  private var smoothingAlpha = 0f
  private var randomSmoothingAlpha = 0f
  private var bassAlpha = 0f
  private var outputBuffer = EMPTY_BUFFER
  private var inputEnded = false

  private var phase = 0.0
  private var pan = 0f
  private var width = 1f
  private var intensity = 0.8f
  private var bass = 0f
  private var reverbMix = 0f
  private var delayMix = 0f
  private var randomPan = 0f
  private var randomTarget = 0f
  private var randomSamplesLeft = 0
  private var randomState = 0x13579BDF
  private var lastEnabled = false
  private var frameOutLeft = 0f
  private var frameOutRight = 0f
  private var lowLeft = 0f
  private var lowRight = 0f
  private var delayLeft = FloatArray(1)
  private var delayRight = FloatArray(1)
  private var reverbLeftA = FloatArray(1)
  private var reverbRightA = FloatArray(1)
  private var reverbLeftB = FloatArray(1)
  private var reverbRightB = FloatArray(1)
  private var delayIndex = 0
  private var reverbIndexA = 0
  private var reverbIndexB = 0

  override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    sampleRate = inputAudioFormat.sampleRate
    channelCount = inputAudioFormat.channelCount
    encoding = inputAudioFormat.encoding
    if ((encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) || channelCount !in 1..2) {
      throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    }
    resetDspState()
    smoothingAlpha = (1f - exp(-1f / (SMOOTHING_SECONDS * sampleRate))).coerceIn(0f, 1f)
    randomSmoothingAlpha = (1f - exp(-1f / (RANDOM_SMOOTHING_SECONDS * sampleRate))).coerceIn(0f, 1f)
    bassAlpha = (1f - exp(-2f * PI.toFloat() * BASS_CUTOFF_HZ / sampleRate)).coerceIn(0f, 1f)
    delayLeft = FloatArray(max(1, sampleRate * DELAY_MS / 1000))
    delayRight = FloatArray(delayLeft.size)
    reverbLeftA = FloatArray(max(1, sampleRate * REVERB_A_MS / 1000))
    reverbRightA = FloatArray(reverbLeftA.size)
    reverbLeftB = FloatArray(max(1, sampleRate * REVERB_B_MS / 1000))
    reverbRightB = FloatArray(reverbLeftB.size)
    active = true
    return inputAudioFormat
  }

  override fun isActive(): Boolean = active

  override fun queueInput(inputBuffer: ByteBuffer) {
    val byteCount = inputBuffer.remaining()
    if (byteCount == 0) return
    if (outputBuffer.capacity() < byteCount) {
      outputBuffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
    } else {
      outputBuffer.clear()
      outputBuffer.order(ByteOrder.nativeOrder())
    }

    val target = parameters
    if (!target.enabled) {
      // Exact sample-preserving bypass. No decode/re-encode, gain change, or effect tail.
      outputBuffer.put(inputBuffer)
      outputBuffer.flip()
      lastEnabled = false
      return
    }

    if (!lastEnabled) clearEffectTails()
    lastEnabled = true
    inputBuffer.order(ByteOrder.nativeOrder())
    when (encoding) {
      C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, outputBuffer, target)
      C.ENCODING_PCM_FLOAT -> processFloat(inputBuffer, outputBuffer, target)
    }
    outputBuffer.flip()
  }

  private fun processPcm16(input: ByteBuffer, output: ByteBuffer, target: EightDAudioParameters) {
    val frameCount = input.remaining() / (2 * channelCount)
    repeat(frameCount) {
      val left = input.getShort().toFloat() / 32768f
      val right = if (channelCount == 2) input.getShort().toFloat() / 32768f else left
      processFrame(left, right, target)
      output.putShort((softLimit(frameOutLeft) * 32767f).toInt().coerceIn(-32768, 32767).toShort())
      if (channelCount == 2) {
        output.putShort((softLimit(frameOutRight) * 32767f).toInt().coerceIn(-32768, 32767).toShort())
      }
    }
  }

  private fun processFloat(input: ByteBuffer, output: ByteBuffer, target: EightDAudioParameters) {
    val frameCount = input.remaining() / (4 * channelCount)
    repeat(frameCount) {
      val left = input.float
      val right = if (channelCount == 2) input.float else left
      processFrame(left, right, target)
      output.putFloat(softLimit(frameOutLeft))
      if (channelCount == 2) output.putFloat(softLimit(frameOutRight))
    }
  }

  private fun processFrame(leftIn: Float, rightIn: Float, target: EightDAudioParameters) {
    val panTarget = nextPan(target)
    pan += smoothingAlpha * (panTarget - pan)
    width += smoothingAlpha * ((0.5f + target.stereoWidth) - width)
    intensity += smoothingAlpha * (target.intensity - intensity)
    bass += smoothingAlpha * (target.bassEnhancement - bass)
    reverbMix += smoothingAlpha * (target.reverb - reverbMix)
    delayMix += smoothingAlpha * (target.delay - delayMix)

    // A one-pole low shelf adds up to a modest low-frequency lift without boosting highs.
    lowLeft += bassAlpha * (leftIn - lowLeft)
    lowRight += bassAlpha * (rightIn - lowRight)
    var left = leftIn + lowLeft * bass * MAX_BASS_ADD
    var right = rightIn + lowRight * bass * MAX_BASS_ADD

    if (channelCount == 2) {
      val mid = (left + right) * 0.5f
      val side = (left - right) * 0.5f * width
      val panAngle = ((pan.coerceIn(-1f, 1f) + 1f) * 0.25f * PI).toFloat()
      val equalPowerScale = 1.41421356f
      val movedLeft = mid * cos(panAngle) * equalPowerScale + side
      val movedRight = mid * sin(panAngle) * equalPowerScale - side
      left += (movedLeft - left) * intensity
      right += (movedRight - right) * intensity
    }

    val dL = delayLeft[delayIndex]
    val dR = delayRight[delayIndex]
    delayLeft[delayIndex] = left + dL * DELAY_FEEDBACK
    delayRight[delayIndex] = right + dR * DELAY_FEEDBACK
    left += dL * delayMix * DELAY_WET
    right += dR * delayMix * DELAY_WET
    delayIndex = (delayIndex + 1) % delayLeft.size

    val rLa = reverbLeftA[reverbIndexA]
    val rRa = reverbRightA[reverbIndexA]
    reverbLeftA[reverbIndexA] = left + rLa * REVERB_FEEDBACK + rRa * REVERB_CROSS_FEEDBACK
    reverbRightA[reverbIndexA] = right + rRa * REVERB_FEEDBACK + rLa * REVERB_CROSS_FEEDBACK
    val rLb = reverbLeftB[reverbIndexB]
    val rRb = reverbRightB[reverbIndexB]
    reverbLeftB[reverbIndexB] = left + rLb * REVERB_FEEDBACK + rRb * REVERB_CROSS_FEEDBACK
    reverbRightB[reverbIndexB] = right + rRb * REVERB_FEEDBACK + rLb * REVERB_CROSS_FEEDBACK
    left += (rLa + rLb) * 0.5f * reverbMix * REVERB_WET
    right += (rRa + rRb) * 0.5f * reverbMix * REVERB_WET
    reverbIndexA = (reverbIndexA + 1) % reverbLeftA.size
    reverbIndexB = (reverbIndexB + 1) % reverbLeftB.size

    frameOutLeft = left
    frameOutRight = right
  }

  private fun nextPan(target: EightDAudioParameters): Float {
    if (target.mode == EightDMotionMode.STATIC_SPATIAL) return 0f
    if (target.mode == EightDMotionMode.RANDOM_ORBIT) {
      if (randomSamplesLeft <= 0) {
        randomState = randomState xor (randomState shl 13)
        randomState = randomState xor (randomState ushr 17)
        randomState = randomState xor (randomState shl 5)
        randomTarget = (randomState.toFloat() / Int.MAX_VALUE).coerceIn(-1f, 1f)
        randomSamplesLeft = max(1, (sampleRate * RANDOM_HOLD_SECONDS / target.rotationSpeed).toInt())
      }
      randomSamplesLeft--
      randomPan += randomSmoothingAlpha * (randomTarget - randomPan)
      return randomPan * target.spatialDepth * if (target.reverseDirection) -1f else 1f
    }

    val modeFrequency = when (target.mode) {
      EightDMotionMode.SLOW_MOTION -> 0.045
      EightDMotionMode.FAST_SPIN -> 0.65
      EightDMotionMode.WAVE -> 0.12
      else -> 0.14
    }
    val reverse = (target.reverseDirection xor (target.mode == EightDMotionMode.REVERSE_ORBIT))
    phase += (2.0 * PI * modeFrequency * target.rotationSpeed * (if (reverse) -1.0 else 1.0)) / sampleRate
    if (phase > PI) phase -= 2.0 * PI
    if (phase < -PI) phase += 2.0 * PI
    val raw = if (target.mode == EightDMotionMode.WAVE) {
      (2.0 / PI * asin(sin(phase))).toFloat()
    } else {
      sin(phase).toFloat()
    }
    return raw * target.spatialDepth
  }

  private fun softLimit(value: Float): Float {
    val magnitude = abs(value)
    if (!magnitude.isFinite()) return 0f
    if (magnitude <= LIMITER_KNEE) return value
    val compressed = LIMITER_KNEE + (1f - LIMITER_KNEE) * tanh((magnitude - LIMITER_KNEE) / (1f - LIMITER_KNEE))
    return if (value < 0f) -compressed else compressed
  }

  private fun clearEffectTails() {
    delayLeft.fill(0f)
    delayRight.fill(0f)
    reverbLeftA.fill(0f)
    reverbRightA.fill(0f)
    reverbLeftB.fill(0f)
    reverbRightB.fill(0f)
  }

  private fun resetDspState() {
    phase = 0.0
    pan = 0f
    width = 1f
    intensity = parameters.intensity
    bass = parameters.bassEnhancement
    reverbMix = 0f
    delayMix = 0f
    randomPan = 0f
    randomTarget = 0f
    randomSamplesLeft = 0
    lowLeft = 0f
    lowRight = 0f
    delayIndex = 0
    reverbIndexA = 0
    reverbIndexB = 0
    lastEnabled = false
  }

  override fun getOutput(): ByteBuffer {
    val buffer = outputBuffer
    outputBuffer = EMPTY_BUFFER
    return buffer
  }

  override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

  @Deprecated("Deprecated in Java")
  override fun flush() {
    outputBuffer = EMPTY_BUFFER
    inputEnded = false
    resetDspState()
  }

  override fun reset() {
    @Suppress("DEPRECATION") flush()
    sampleRate = 0
    channelCount = 0
    encoding = C.ENCODING_INVALID
    active = false
    delayLeft = FloatArray(1)
    delayRight = FloatArray(1)
    reverbLeftA = FloatArray(1)
    reverbRightA = FloatArray(1)
    reverbLeftB = FloatArray(1)
    reverbRightB = FloatArray(1)
  }

  override fun queueEndOfStream() { inputEnded = true }

  companion object {
    private const val SMOOTHING_SECONDS = 0.012f
    private const val RANDOM_SMOOTHING_SECONDS = 0.16f
    private const val RANDOM_HOLD_SECONDS = 0.9f
    private const val BASS_CUTOFF_HZ = 170f
    private const val MAX_BASS_ADD = 0.75f
    private const val DELAY_MS = 180
    private const val REVERB_A_MS = 29
    private const val REVERB_B_MS = 37
    private const val DELAY_FEEDBACK = 0.22f
    private const val DELAY_WET = 0.30f
    private const val REVERB_FEEDBACK = 0.40f
    private const val REVERB_CROSS_FEEDBACK = 0.08f
    private const val REVERB_WET = 0.28f
    private const val LIMITER_KNEE = 0.94f
    private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
  }
}
