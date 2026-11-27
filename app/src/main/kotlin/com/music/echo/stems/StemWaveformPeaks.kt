package echo.music.iad1tya.stems

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Computes min/max peaks for a WAV file by reading its 16-bit PCM directly.
 * RajTune renders stems as 44.1 kHz stereo 16-bit WAV, but this parser only
 * relies on the standard 44-byte RIFF header plus chunk walk.
 */
object StemWaveformPeaks {

  data class Peaks(val min: FloatArray, val max: FloatArray, val samplesPerPeak: Int) {
    fun amplitudeAt(index: Int): Float {
      if (min.isEmpty()) return 0f
      val i = index.coerceIn(0, min.size - 1)
      return maxOf(kotlin.math.abs(min[i]), kotlin.math.abs(max[i]))
    }
  }

  /** Read peaks (about [targetPeakCount] buckets) from a content URI WAV. */
  fun loadFromUri(context: Context, uri: Uri, targetPeakCount: Int = 512): Peaks? {
    val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return null
    BufferedInputStream(stream).use { input ->
      // Parse RIFF header enough to find the data chunk.
      val header = ByteArray(44)
      val read = input.read(header)
      if (read < 44) return null
      val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
      val chunkId = String(header, 0, 4)
      if (chunkId != "RIFF") return null
      val fmtStart = buffer.getInt(20)
      val numChannels = buffer.getShort(22).toInt()
      val sampleRate = buffer.getInt(24)
      val bitsPerSample = buffer.getShort(34).toInt()
      if (bitsPerSample != 16 || numChannels <= 0) return null
      buffer.position(40)
      val dataSize = buffer.getInt(40)
      val bytesPerFrame = numChannels * 2
      val totalFrames = maxOf(1, dataSize / bytesPerFrame)
      val approxFramesPerPeak = maxOf(1, totalFrames / targetPeakCount)
      val samplesPerPeak = approxFramesPerPeak // frames, not samples
      val min = FloatArray(targetPeakCount)
      val max = FloatArray(targetPeakCount)
      val framePerPeak = FloatArray(samplesPerPeak * numChannels) { 0f }
      var framesRead = 0L
      var peakIndex = 0
      val sampleBuffer = ByteArray(64 * 1024)
      var leftover = ByteArray(0)
      while (true) {
        val readBytes = input.read(sampleBuffer)
        if (readBytes <= 0) break
        val chunk = leftover + sampleBuffer.copyOf(readBytes)
        val framesInChunk = chunk.size / bytesPerFrame
        framesRead += framesInChunk
        var offset = 0
        for (f in 0 until framesInChunk) {
          val left = ByteBuffer.wrap(chunk, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat() / 32768f
          val right = if (numChannels >= 2) ByteBuffer.wrap(chunk, offset + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat() / 32768f else left
          val peak = maxOf(kotlin.math.abs(left), kotlin.math.abs(right))
          if (peakIndex < targetPeakCount) {
            min[peakIndex] = minOf(min[peakIndex], -peak)
            max[peakIndex] = maxOf(max[peakIndex], peak)
          }
          offset += bytesPerFrame
          if ((framesRead % samplesPerPeak) == 0L) {
            peakIndex++
            if (peakIndex >= targetPeakCount) break
          }
        }
        leftover = chunk.copyOfRange(framesInChunk * bytesPerFrame, chunk.size)
      }
      return Peaks(min, max, samplesPerPeak)
    }
  }
}
