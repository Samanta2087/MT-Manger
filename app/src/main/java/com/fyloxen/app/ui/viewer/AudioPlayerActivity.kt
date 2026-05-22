package com.fyloxen.app.ui.viewer

import android.animation.ObjectAnimator
import android.graphics.LinearGradient
import android.graphics.Shader
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fyloxen.app.databinding.ActivityAudioPlayerBinding
import com.fyloxen.app.utils.ThemeManager
import java.io.File
import java.io.FileInputStream

class AudioPlayerActivity : AppCompatActivity() {

    companion object { const val EXTRA_FILE_PATH = "extra_file_path" }

    private lateinit var binding: ActivityAudioPlayerBinding
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val seekUpdater = object : Runnable {
        override fun run() {
            player?.let { mp ->
                if (mp.isPlaying) {
                    val pos = mp.currentPosition
                    val dur = mp.duration.takeIf { it > 0 } ?: 1
                    binding.seekBar.progress = pos * 1000 / dur
                    binding.tvCurrent.text = fmt(pos)
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor     = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        binding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Resolve audio source ────────────────────────────────────────────
        val dataUri: Uri? = intent.data
        val title: String
        val setup: MediaPlayer.() -> Unit

        when {
            dataUri != null && dataUri.scheme == "content" -> {
                title = contentResolver.query(dataUri, null, null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(
                        c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null }
                    ?: dataUri.lastPathSegment ?: "audio"
                setup = {
                    contentResolver.openFileDescriptor(dataUri, "r")
                        ?.use { setDataSource(it.fileDescriptor) }
                        ?: Toast.makeText(this@AudioPlayerActivity, "Cannot open", Toast.LENGTH_SHORT).show()
                }
            }
            intent.hasExtra(EXTRA_FILE_PATH) -> {
                val f = File(intent.getStringExtra(EXTRA_FILE_PATH)!!)
                title = f.name
                setup = { FileInputStream(f).use { setDataSource(it.fd) } }
            }
            else -> { finish(); return }
        }

        // Song name as subtitle (filename without extension)
        binding.tvAudioSubtitle.text = title.substringBeforeLast(".")
        binding.tvAudioTitle.text    = title
        binding.btnAudioBack.setOnClickListener { finish() }

        // ── Gradient "Feel the Music": cyan → pink ─────────────────────────
        binding.tvFeelMusic.post {
            val w = binding.tvFeelMusic.paint.measureText(binding.tvFeelMusic.text.toString())
            binding.tvFeelMusic.paint.shader = LinearGradient(0f, 0f, w, 0f,
                intArrayOf(0xFF00C3FF.toInt(), 0xFFFF00EA.toInt()),
                null, Shader.TileMode.CLAMP)
            binding.tvFeelMusic.invalidate()
        }

        // ── MediaPlayer setup ───────────────────────────────────────────────
        player = MediaPlayer().apply {
            try { setup() } catch (e: Exception) {
                Toast.makeText(this@AudioPlayerActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }
            prepare()
            binding.tvDuration.text = fmt(duration)
            binding.seekBar.max = 1000
            start()
            updateIcon()
            handler.post(seekUpdater)
            setOnCompletionListener { updateIcon() }
        }

        // ── Listeners ───────────────────────────────────────────────────────
        binding.btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.start() }
            updateIcon(); bounce(binding.btnPlayPause, 0.88f)
        }
        binding.btnRewind.setOnClickListener {
            player?.seekTo((player!!.currentPosition - 10_000).coerceAtLeast(0))
            bounce(binding.btnRewind, 0.90f)
        }
        binding.btnFastForward.setOnClickListener {
            player?.seekTo(player!!.currentPosition + 10_000)
            bounce(binding.btnFastForward, 0.90f)
        }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(p * (player?.duration ?: 0) / 1000)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    private fun bounce(v: android.view.View, min: Float) {
        ObjectAnimator.ofFloat(v, "scaleX", 1f, min, 1f).apply { duration = 160; start() }
        ObjectAnimator.ofFloat(v, "scaleY", 1f, min, 1f).apply { duration = 160; start() }
    }

    private fun updateIcon() {
        val playing = player?.isPlaying == true
        binding.btnPlayPause.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        binding.btnPlayPause.setColorFilter(0xFFFFFFFF.toInt())
    }

    private fun fmt(ms: Int) = "%d:%02d".format(ms / 1000 / 60, ms / 1000 % 60)

    override fun onPause() { super.onPause(); player?.pause(); updateIcon() }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(seekUpdater)
        player?.release(); player = null
    }
}
