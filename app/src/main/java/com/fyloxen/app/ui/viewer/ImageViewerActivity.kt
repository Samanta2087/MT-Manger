package com.fyloxen.app.ui.viewer

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fyloxen.app.databinding.ActivityImageViewerBinding
import com.fyloxen.app.utils.ThemeManager
import java.io.File

class ImageViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
    }

    private lateinit var binding: ActivityImageViewerBinding
    private var imageUri: Uri? = null
    private var file: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Resolve source: external URI (from other apps) OR internal file path
        val dataUri: Uri? = intent.data
        when {
            dataUri != null && dataUri.scheme == "content" -> {
                imageUri = dataUri
                val name = contentResolver.query(dataUri, null, null, null, null)
                    ?.use { c -> if (c.moveToFirst())
                        c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                        else null } ?: dataUri.lastPathSegment ?: "image"
                binding.tvImageName.text = name
            }
            dataUri != null && dataUri.scheme == "file" -> {
                file = File(dataUri.path!!)
                binding.tvImageName.text = file!!.name
            }
            intent.hasExtra(EXTRA_FILE_PATH) -> {
                file = File(intent.getStringExtra(EXTRA_FILE_PATH)!!)
                binding.tvImageName.text = file!!.name
            }
            else -> { finish(); return }
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { shareImage() }
        loadImage()
    }

    private fun loadImage() {
        try {
            val bitmap = when {
                imageUri != null -> {
                    // content:// URI — use ContentResolver stream
                    contentResolver.openInputStream(imageUri!!)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                file != null -> BitmapFactory.decodeFile(file!!.absolutePath)
                else -> null
            }

            if (bitmap != null) {
                binding.ivImage.setImageBitmap(bitmap)
                val sizeInfo = file?.let { " · ${formatSize(it.length())}" } ?: ""
                binding.tvImageInfo.text = "${bitmap.width} × ${bitmap.height} px$sizeInfo"
            } else {
                Toast.makeText(this, "Cannot decode image", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun shareImage() {
        try {
            val shareUri = when {
                imageUri != null -> imageUri!!  // already a shareable URI
                file != null -> androidx.core.content.FileProvider.getUriForFile(
                    this, "$packageName.provider", file!!)
                else -> return
            }
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share image"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot share", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
