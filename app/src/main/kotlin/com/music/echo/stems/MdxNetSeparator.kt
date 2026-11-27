package echo.music.iad1tya.stems

import org.jtransforms.fft.FloatFFT_1D
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class MdxStemConfig(
  val name: String,
  val nFft: Int,
  val dimT: Int,
) {
  val dimF = 2048
  val hop = 1024
  val chunkSize = hop * (dimT - 1)
  val trim = nFft / 2
  val segmentSize = chunkSize - 2 * trim
}

/** Inference wrapper matching the KUIELab leaderboard_B STFT layout and overlap trimming. */
internal class MdxNetSeparator(
  private val environment: OrtEnvironment = OrtEnvironment.getEnvironment(),
) {
  private val configs = listOf(
    MdxStemConfig("bass", nFft = 16384, dimT = 256),
    MdxStemConfig("drums", nFft = 4096, dimT = 128),
    MdxStemConfig("other", nFft = 8192, dimT = 256),
    MdxStemConfig("vocals", nFft = 6144, dimT = 256),
  )

  suspend fun separate(
    pcmFile: java.io.File,
    sampleCount: Long,
    modelFiles: Map<String, java.io.File>,
    writer: (stemName: String, segmentStart: Long, left: FloatArray, right: FloatArray, count: Int) -> Unit,
    progress: (stemName: String, fraction: Float) -> Unit,
  ) {
    for ((stemIndex, config) in configs.withIndex()) {
      currentCoroutineContext().ensureActive()
      val model = modelFiles[config.name] ?: error("Missing ${config.name} model")
      val sessionOptions = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(2)
        setInterOpNumThreads(1)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
      }
      val session = environment.createSession(model.absolutePath, sessionOptions)
      sessionOptions.close()
      try {
        val chunks = ((sampleCount + config.segmentSize - 1) / config.segmentSize).coerceAtLeast(1)
        RandomAccessFile(pcmFile, "r").use { source ->
          var segmentIndex = 0L
          while (segmentIndex < chunks) {
            currentCoroutineContext().ensureActive()
            val segmentStart = segmentIndex * config.segmentSize
            val validCount = minOf(config.segmentSize.toLong(), sampleCount - segmentStart).toInt()
            val input = readStereoChunk(source, segmentStart - config.trim, config.chunkSize)
            val spectrum = stft(input.first, input.second, config)
            val output = infer(session, spectrum, config)
            val audio = istft(output, config)
            val left = FloatArray(validCount)
            val right = FloatArray(validCount)
            audio.first.copyInto(left, 0, config.trim, config.trim + validCount)
            audio.second.copyInto(right, 0, config.trim, config.trim + validCount)
            writer(config.name, segmentStart, left, right, validCount)
            segmentIndex++
            progress(config.name, segmentIndex.toFloat() / chunks)
          }
        }
      } finally {
        session.close()
      }
      progress(config.name, (stemIndex + 1) / configs.size.toFloat())
    }
  }

  private fun readStereoChunk(file: RandomAccessFile, startSample: Long, size: Int): Pair<FloatArray, FloatArray> {
    val left = FloatArray(size)
    val right = FloatArray(size)
    val first = maxOf(startSample, 0L)
    val last = minOf(startSample + size, file.length() / 8)
    if (last <= first) return left to right
    val count = (last - first).toInt()
    val bytes = ByteArray(count * 8)
    file.seek(first * 8)
    file.readFully(bytes)
    val values = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    val destination = (first - startSample).toInt()
    for (i in 0 until count) {
      left[destination + i] = values.get()
      right[destination + i] = values.get()
    }
    return left to right
  }

  private fun stft(left: FloatArray, right: FloatArray, cfg: MdxStemConfig): FloatArray {
    val result = FloatArray(4 * cfg.dimF * cfg.dimT)
    val fft = FloatFFT_1D(cfg.nFft.toLong())
    val window = hann(cfg.nFft)
    val frame = FloatArray(cfg.nFft)
    val transformed = FloatArray(cfg.nFft * 2)
    for (channel in 0..1) {
      val samples = if (channel == 0) left else right
      for (time in 0 until cfg.dimT) {
        val center = time * cfg.hop
        for (i in 0 until cfg.nFft) {
          var sourceIndex = center + i - cfg.nFft / 2
          if (sourceIndex < 0) sourceIndex = -sourceIndex
          if (sourceIndex >= samples.size) sourceIndex = 2 * samples.size - 2 - sourceIndex
          frame[i] = if (sourceIndex in samples.indices) samples[sourceIndex] * window[i] else 0f
        }
        java.util.Arrays.fill(transformed, 0f)
        frame.copyInto(transformed, 0)
        fft.realForwardFull(transformed)
        val realChannel = if (channel == 0) 0 else 2
        val imagChannel = realChannel + 1
        for (frequency in 0 until cfg.dimF) {
          val base = frequency * cfg.dimT + time
          result[realChannel * cfg.dimF * cfg.dimT + base] = transformed[2 * frequency]
          result[imagChannel * cfg.dimF * cfg.dimT + base] = transformed[2 * frequency + 1]
        }
      }
    }
    return result
  }

  private fun infer(session: OrtSession, inputData: FloatArray, cfg: MdxStemConfig): FloatArray {
    val shape = longArrayOf(1, 4, cfg.dimF.toLong(), cfg.dimT.toLong())
    OnnxTensor.createTensor(environment, FloatBuffer.wrap(inputData), shape).use { tensor ->
      session.run(mapOf(session.inputNames.first() to tensor)).use { outputs ->
        val output = outputs.get(session.outputNames.first()).get() as OnnxTensor
        return FloatArray(output.floatBuffer.remaining()).also { output.floatBuffer.get(it) }
      }
    }
  }

  private fun istft(spectrum: FloatArray, cfg: MdxStemConfig): Pair<FloatArray, FloatArray> {
    val window = hann(cfg.nFft)
    val fft = FloatFFT_1D(cfg.nFft.toLong())
    val audioLength = cfg.nFft + cfg.hop * (cfg.dimT - 1)
    val channelOutputs = Array(2) { FloatArray(audioLength) }
    val weight = FloatArray(audioLength)
    val full = FloatArray(cfg.nFft * 2)
    for (time in 0 until cfg.dimT) {
      val basePos = time * cfg.hop
      for (i in 0 until cfg.nFft) {
        val f = basePos + i
        weight[f] += window[i] * window[i]
      }
      for (channel in 0..1) {
        val realChannel = if (channel == 0) 0 else 2
        val imagChannel = realChannel + 1
        java.util.Arrays.fill(full, 0f)
        for (frequency in 0 until cfg.dimF) {
          val index = frequency * cfg.dimT + time
          val real = spectrum[realChannel * cfg.dimF * cfg.dimT + index]
          val imag = spectrum[imagChannel * cfg.dimF * cfg.dimT + index]
          full[2 * frequency] = real
          full[2 * frequency + 1] = imag
          if (frequency > 0 && frequency < cfg.nFft / 2) {
            val mirrored = cfg.nFft - frequency
            full[2 * mirrored] = real
            full[2 * mirrored + 1] = -imag
          }
        }
        fft.complexInverse(full, true)
        val output = channelOutputs[channel]
        for (i in 0 until cfg.nFft) output[basePos + i] += full[2 * i] * window[i]
      }
    }
    val left = FloatArray(cfg.chunkSize)
    val right = FloatArray(cfg.chunkSize)
    val offset = cfg.nFft / 2
    for (i in 0 until cfg.chunkSize) {
      val denominator = weight[offset + i]
      if (denominator > 1e-9f) {
        left[i] = channelOutputs[0][offset + i] / denominator
        right[i] = channelOutputs[1][offset + i] / denominator
      }
    }
    return left to right
  }

  private fun hann(size: Int): FloatArray = FloatArray(size) { i ->
    (0.5 - 0.5 * cos(2.0 * PI * i / size)).toFloat()
  }
}
