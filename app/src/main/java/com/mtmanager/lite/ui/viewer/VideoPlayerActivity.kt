package com.mtmanager.lite.ui.viewer

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mtmanager.lite.databinding.ActivityVideoPlayerBinding
import com.mtmanager.lite.utils.ThemeManager
import java.io.File

class VideoPlayerActivity : AppCompatActivity() {

    companion object { const val EXTRA_FILE_PATH = "extra_file_path" }

    private lateinit var binding: ActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    private var isTracking = false
    private var controlsVisible = true
    private var userPaused = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    private lateinit var gestureDetector: GestureDetector

    private val seekRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (!isTracking && (p.isPlaying || p.playbackState == Player.STATE_READY)) {
                val dur = p.duration.takeIf { it > 0L } ?: 1L
                val pos = p.currentPosition
                binding.seekBar.progress = (pos * 1000L / dur).toInt()
                binding.tvCurrent.text = formatTime(pos)
            }
            if (p.playbackState != Player.STATE_IDLE) {
                handler.postDelayed(this, 500L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)

        // Immersive fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        goFullscreen()

        // ── Resolve source URI ─────────────────────────────────────────────
        val dataUri: Uri? = intent.data
        val filePath: String? = intent.getStringExtra(EXTRA_FILE_PATH)

        val uri: Uri
        val title: String

        when {
            // From another app via content:// — use directly
            dataUri != null && dataUri.scheme == "content" -> {
                uri = dataUri
                title = try {
                    contentResolver.query(dataUri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst())
                            c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                        else null
                    } ?: dataUri.lastPathSegment ?: "Video"
                } catch (_: Exception) { dataUri.lastPathSegment ?: "Video" }
            }
            // From another app via file:// — use directly
            dataUri != null && dataUri.scheme == "file" -> {
                uri = dataUri
                title = dataUri.lastPathSegment ?: "Video"
            }
            // From our app via extra path
            filePath != null -> {
                val file = File(filePath)
                if (!file.exists()) {
                    Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                    finish(); return
                }
                // Use file:// directly — ExoPlayer handles it natively, no FileProvider needed
                uri = Uri.fromFile(file)
                title = file.name
            }
            else -> { finish(); return }
        }

        binding.tvVideoTitle.text = title
        binding.btnVideoBack.setOnClickListener { finish() }
        binding.seekBar.max = 1000
        binding.pbLoading.visibility = View.VISIBLE

        initPlayer(uri)
        setupControls()
        setupGestures()
        scheduleHide()
    }

    // ── ExoPlayer init ─────────────────────────────────────────────────────

    private fun initPlayer(uri: Uri) {
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        binding.playerView.player = exo

        // Simple MediaItem — ExoPlayer auto-detects format from extension/content
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.playWhenReady = true

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.btnPlayPause.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        binding.pbLoading.visibility = View.VISIBLE
                    }
                    Player.STATE_READY -> {
                        binding.pbLoading.visibility = View.GONE
                        binding.tvDuration.text = formatTime(exo.duration.coerceAtLeast(0L))
                        handler.removeCallbacks(seekRunnable)
                        handler.post(seekRunnable)
                        // Detect video vs audio-only
                        val hasVideo = exo.videoSize.width > 0
                        binding.playerView.visibility = if (hasVideo) View.VISIBLE else View.GONE
                        binding.audioArt.visibility   = if (hasVideo) View.GONE   else View.VISIBLE
                    }
                    Player.STATE_ENDED -> {
                        binding.pbLoading.visibility = View.GONE
                        binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                        handler.removeCallbacks(seekRunnable)
                        showControls()
                        cancelHide()
                    }
                    Player.STATE_IDLE -> {
                        binding.pbLoading.visibility = View.GONE
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                binding.pbLoading.visibility = View.GONE
                Toast.makeText(this@VideoPlayerActivity,
                    "Playback error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        })
    }

    // ── Controls ────────────────────────────────────────────────────────────

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            player?.let { p ->
                if (p.isPlaying) {
                    p.pause(); userPaused = true
                } else {
                    if (p.playbackState == Player.STATE_ENDED) p.seekTo(0L)
                    p.play(); userPaused = false
                }
            }
            scheduleHide()
        }
        binding.btnRewind.setOnClickListener {
            player?.let { p -> p.seekTo((p.currentPosition - 10_000L).coerceAtLeast(0L)) }
            showSkipAnim("« 10s"); scheduleHide()
        }
        binding.btnFastForward.setOnClickListener {
            player?.let { p -> p.seekTo((p.currentPosition + 10_000L).coerceAtMost(p.duration)) }
            showSkipAnim("10s »"); scheduleHide()
        }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar)  { isTracking = true; cancelHide() }
            override fun onStopTrackingTouch(sb: SeekBar)   { isTracking = false; scheduleHide() }
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val dur = player?.duration?.takeIf { it > 0L } ?: return
                    player?.seekTo(p * dur / 1000L)
                    binding.tvCurrent.text = formatTime(p * dur / 1000L)
                }
            }
        })
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControls(); return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val w = binding.playerView.width
                if (w <= 0) return true
                player?.let { p ->
                    if (e.x < w / 2) {
                        p.seekTo((p.currentPosition - 10_000L).coerceAtLeast(0L))
                        showSkipAnim("« 10s")
                    } else {
                        p.seekTo((p.currentPosition + 10_000L).coerceAtMost(p.duration))
                        showSkipAnim("10s »")
                    }
                }
                return true
            }
        })
        binding.playerView.setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev); true }
    }

    // ── Controls visibility ─────────────────────────────────────────────────

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        controlsVisible = true
        binding.controlsBar.visibility = View.VISIBLE
        binding.topBar.visibility = View.VISIBLE
        binding.controlsBar.animate().alpha(1f).setDuration(200).start()
        binding.topBar.animate().alpha(1f).setDuration(200).start()
        scheduleHide()
    }

    private fun hideControls() {
        controlsVisible = false
        binding.controlsBar.animate().alpha(0f).setDuration(250)
            .withEndAction { binding.controlsBar.visibility = View.GONE }.start()
        binding.topBar.animate().alpha(0f).setDuration(250)
            .withEndAction { binding.topBar.visibility = View.GONE }.start()
    }

    private fun scheduleHide() { cancelHide(); handler.postDelayed(hideRunnable, 3500L) }
    private fun cancelHide()   { handler.removeCallbacks(hideRunnable) }

    private fun showSkipAnim(text: String) {
        binding.tvSkipHint.text = text
        binding.tvSkipHint.alpha = 1f
        binding.tvSkipHint.visibility = View.VISIBLE
        binding.tvSkipHint.animate().alpha(0f).setDuration(700)
            .withEndAction { binding.tvSkipHint.visibility = View.GONE }.start()
    }

    // ── Fullscreen ──────────────────────────────────────────────────────────

    private fun goFullscreen() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000L
        val h = s / 3600L
        return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600L) / 60L, s % 60L)
        else "%d:%02d".format(s / 60L, s % 60L)
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        goFullscreen()
        // Only resume playback if the user didn't explicitly pause
        if (!userPaused && player?.playbackState == Player.STATE_READY) {
            player?.play()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
