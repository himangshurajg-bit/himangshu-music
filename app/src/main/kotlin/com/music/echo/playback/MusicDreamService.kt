package echo.music.iad1tya.playback

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import echo.music.iad1tya.R
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

/**
 * Android system screensaver for music playback. Android only starts this after the user enables
 * RajTune under the device's Screen saver settings; it is separate from the in-app AOD screen.
 */
class MusicDreamService : DreamService(), Player.Listener {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val artworkExecutor = Executors.newSingleThreadExecutor { task ->
    Thread(task, "dream-artwork").apply { isDaemon = true }
  }
  private val artworkRequest = AtomicInteger()
  private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
  private var controller: MediaController? = null
  private var artwork: ImageView? = null
  private var title: TextView? = null
  private var artist: TextView? = null
  private var clock: TextView? = null
  private var lyrics: TextView? = null
  private var position: TextView? = null
  private var duration: TextView? = null
  private var seekBar: SeekBar? = null
  private var previousButton: Button? = null
  private var playPause: Button? = null
  private var nextButton: Button? = null
  private var userSeeking = false

  private val tick = object : Runnable {
    override fun run() {
      refreshPlayback()
      mainHandler.postDelayed(this, 1_000L)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    setInteractive(true)
    setFullscreen(true)
    setScreenBright(false)
    window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
    setContentView(createContent())

    val token = SessionToken(this, android.content.ComponentName(this, MusicService::class.java))
    controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
      future.addListener(
        {
          if (!future.isCancelled) {
            runCatching { future.get() }.onSuccess { connected ->
              controller = connected
              connected.addListener(this)
              renderMetadata(connected.mediaMetadata)
              refreshPlayback()
            }
          }
        },
        ContextCompat.getMainExecutor(this),
      )
    }
  }

  override fun onDreamingStarted() {
    super.onDreamingStarted()
    mainHandler.removeCallbacks(tick)
    mainHandler.post(tick)
  }

  override fun onDreamingStopped() {
    mainHandler.removeCallbacks(tick)
    super.onDreamingStopped()
  }

  override fun onDetachedFromWindow() {
    mainHandler.removeCallbacks(tick)
    controller?.removeListener(this)
    controller = null
    controllerFuture?.let { MediaController.releaseFuture(it) }
    controllerFuture = null
    artworkRequest.incrementAndGet()
    super.onDetachedFromWindow()
  }

  override fun onDestroy() {
    artworkRequest.incrementAndGet()
    artworkExecutor.shutdownNow()
    super.onDestroy()
  }

  override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
    renderMetadata(mediaMetadata)
    refreshPlayback()
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    playPause?.text = if (isPlaying) getString(R.string.dream_pause) else getString(R.string.dream_play)
  }

  override fun onPlaybackStateChanged(playbackState: Int) = refreshPlayback()

  private fun createContent(): View {
    val outer = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      setPadding(dp(24), dp(16), dp(24), dp(16))
      setBackgroundColor(Color.BLACK)
    }
    clock = TextView(this).apply {
      textSize = 30f
      setTextColor(Color.LTGRAY)
      gravity = Gravity.CENTER
      typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
      text = ""
    }
    outer.addView(clock, matchWrap())

    val scroll = ScrollView(this).apply { isFillViewport = true }
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      setPadding(0, dp(12), 0, dp(12))
    }
    artwork = ImageView(this).apply {
      scaleType = ImageView.ScaleType.CENTER_CROP
      setBackgroundColor(Color.rgb(28, 28, 28))
      contentDescription = getString(R.string.dream_album_artwork)
    }
    content.addView(artwork, LinearLayout.LayoutParams(dp(232), dp(232)).apply { bottomMargin = dp(18) })

    title = TextView(this).apply {
      textSize = 23f
      typeface = Typeface.DEFAULT_BOLD
      setTextColor(Color.WHITE)
      gravity = Gravity.CENTER
      maxLines = 2
    }
    artist = TextView(this).apply {
      textSize = 15f
      setTextColor(Color.LTGRAY)
      gravity = Gravity.CENTER
      maxLines = 1
    }
    content.addView(title, matchWrap())
    content.addView(artist, matchWrap())

    seekBar = SeekBar(this).apply {
      max = 1_000
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(bar: SeekBar?) { userSeeking = true }
        override fun onStopTrackingTouch(bar: SeekBar?) {
          val player = controller
          if (player != null && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            player.seekTo((player.duration.coerceAtLeast(0L) * (bar?.progress ?: 0)) / 1_000L)
          }
          userSeeking = false
          refreshPlayback()
        }
        override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
      })
    }
    content.addView(seekBar, matchWrap().apply { topMargin = dp(6) })
    val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    position = smallTime()
    duration = smallTime().apply { gravity = Gravity.END }
    times.addView(position, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    times.addView(duration, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    content.addView(times, matchWrap())

    val controls = LinearLayout(this).apply {
      gravity = Gravity.CENTER
      orientation = LinearLayout.HORIZONTAL
    }
    previousButton = button(getString(R.string.dream_previous)) {
      controller?.takeIf { it.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) }
        ?.seekToPreviousMediaItem()
    }
    playPause = button(getString(R.string.dream_play)) {
      controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    nextButton = button(getString(R.string.dream_next)) {
      controller?.takeIf { it.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) }
        ?.seekToNextMediaItem()
    }
    controls.addView(previousButton)
    controls.addView(playPause)
    controls.addView(nextButton)
    content.addView(controls, matchWrap().apply { topMargin = dp(8) })

    lyrics = TextView(this).apply {
      textSize = 16f
      setTextColor(Color.rgb(190, 190, 190))
      gravity = Gravity.CENTER
      maxLines = 4
      setPadding(dp(8), dp(14), dp(8), dp(8))
    }
    content.addView(lyrics, matchWrap())
    scroll.addView(content)
    outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    return outer
  }

  private fun renderMetadata(metadata: MediaMetadata) {
    title?.text = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: getString(R.string.dream_nothing_playing)
    artist?.text = metadata.artist?.toString().orEmpty()
    val data = metadata.artworkData
    val uri = metadata.artworkUri
    val request = artworkRequest.incrementAndGet()
    if (data == null && uri == null) {
      artwork?.setImageDrawable(null)
    } else {
      artworkExecutor.execute {
        val bitmap = runCatching {
          data?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            ?: uri?.let { artworkUri ->
              contentResolver.openInputStream(artworkUri)?.use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
        mainHandler.post {
          if (request == artworkRequest.get()) artwork?.setImageBitmap(bitmap)
        }
      }
    }
    updateLyrics()
  }

  private fun updateLyrics() {
    val extra = controller?.mediaMetadata?.extras
    val lyricInfo = extra?.getString(OplusLyricInfo.METADATA_KEY)
    val lyricText = runCatching { JSONObject(lyricInfo ?: "{}").optString("lyric") }.getOrNull()
    lyrics?.text = dreamLyricsAt(lyricText, controller?.currentPosition ?: 0L)
  }

  private fun refreshPlayback() {
    val player = controller
    val current = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
    val total = player?.duration?.takeIf { it > 0L } ?: 0L
    clock?.text = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
    position?.text = formatDreamTime(current)
    duration?.text = formatDreamTime(total)
    if (!userSeeking) seekBar?.progress = if (total > 0L) ((current * 1_000L) / total).toInt().coerceIn(0, 1_000) else 0
    playPause?.text = if (player?.isPlaying == true) getString(R.string.dream_pause) else getString(R.string.dream_play)
    playPause?.isEnabled = player != null && player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
    previousButton?.isEnabled = player?.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) == true
    nextButton?.isEnabled = player?.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) == true
    updateLyrics()
  }

  private fun button(label: String, action: () -> Unit) = Button(this).apply {
    text = label
    setTextColor(Color.WHITE)
    setOnClickListener { action() }
  }

  private fun smallTime() = TextView(this).apply { textSize = 12f; setTextColor(Color.GRAY) }
  private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

internal fun formatDreamTime(positionMs: Long): String {
  val seconds = (positionMs.coerceAtLeast(0L) / 1_000L)
  val hours = seconds / 3_600L
  val minutes = (seconds / 60L) % 60L
  val remainder = seconds % 60L
  return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder)
  else "%d:%02d".format(seconds / 60L, remainder)
}

/** Select the current and next line from the synced LRC text supplied by the app's media session. */
internal fun dreamLyricsAt(lyric: String?, positionMs: Long): String {
  if (lyric.isNullOrBlank()) return "Lyrics unavailable"
  val timestamped = Regex("^\\[(\\d+):(\\d{2})(?:\\.(\\d{1,3}))?](.*)$")
  val lines = lyric.lineSequence().mapNotNull { raw ->
    val match = timestamped.matchEntire(raw.trim()) ?: return@mapNotNull null
    val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    val at = match.groupValues[1].toLong() * 60_000L + match.groupValues[2].toLong() * 1_000L + fraction
    at to match.groupValues[4].trim()
  }.filter { it.second.isNotBlank() }.sortedBy { it.first }.toList()
  if (lines.isEmpty()) return lyric.lineSequence().map(String::trim).filter(String::isNotEmpty).take(2).joinToString("\n").ifBlank { "Lyrics unavailable" }
  val index = lines.indexOfLast { it.first <= positionMs.coerceAtLeast(0L) }.coerceAtLeast(0)
  return lines.drop(index).take(2).joinToString("\n") { it.second }
}
