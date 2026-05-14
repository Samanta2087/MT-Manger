package com.mtmanager.lite.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.mtmanager.lite.databinding.ActivityPdfViewerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mtmanager.lite.utils.ThemeManager
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    companion object { const val EXTRA_FILE_PATH = "extra_file_path" }

    private lateinit var binding: ActivityPdfViewerBinding
    private lateinit var pdfiumCore: PdfiumCore
    private var pdfDocument: PdfDocument? = null
    private var currentPage = 0
    private var pageCount   = 0
    private var pdfFile: File? = null
    private var pdfUri: Uri?  = null   // for content:// URIs from WhatsApp/Gmail

    // Swipe detection
    private var touchDownX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pdfiumCore = PdfiumCore(this)

        // ── Resolve source: intent.data (WhatsApp/external) OR EXTRA_FILE_PATH (internal) ──
        val dataUri = intent.data
        when {
            dataUri != null && dataUri.scheme == "content" -> {
                pdfUri = dataUri
                // Get display name from content resolver
                val name = contentResolver.query(dataUri, null, null, null, null)
                    ?.use { c -> if (c.moveToFirst())
                        c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                        else null } ?: "document.pdf"
                binding.tvPdfTitle.text = name
            }
            dataUri != null && dataUri.scheme == "file" -> {
                pdfFile = File(dataUri.path!!)
                binding.tvPdfTitle.text = pdfFile!!.name
            }
            intent.hasExtra(EXTRA_FILE_PATH) -> {
                pdfFile = File(intent.getStringExtra(EXTRA_FILE_PATH)!!)
                binding.tvPdfTitle.text = pdfFile!!.name
            }
            else -> { finish(); return }
        }

        binding.btnPdfBack.setOnClickListener { finish() }
        binding.btnPrevPage.setOnClickListener { showPage(currentPage - 1) }
        binding.btnNextPage.setOnClickListener { showPage(currentPage + 1) }

        // Swipe left/right when not zoomed
        binding.ivPdfPage.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { touchDownX = ev.x; false }
                android.view.MotionEvent.ACTION_UP   -> {
                    if (binding.ivPdfPage.currentScale <= 1.05f) {
                        val dx = ev.x - touchDownX
                        if (dx < -80)  showPage(currentPage + 1)
                        if (dx >  80)  showPage(currentPage - 1)
                    }
                    false
                }
                else -> false
            }
        }

        tryOpenPdf(password = null)
    }

    // ── Open PDF (with optional password) ────────────────────────────────────

    private fun tryOpenPdf(password: String?) {
        binding.pbLoading.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                // Get ParcelFileDescriptor from either File or content:// URI
                val pfd: ParcelFileDescriptor = when {
                    pdfUri != null ->
                        contentResolver.openFileDescriptor(pdfUri!!, "r")
                            ?: error("Cannot open URI")
                    pdfFile != null ->
                        ParcelFileDescriptor.open(pdfFile!!, ParcelFileDescriptor.MODE_READ_ONLY)
                    else -> error("No source")
                }
                pdfiumCore.newDocument(pfd, password)
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { doc ->
                    pdfDocument?.let { pdfiumCore.closeDocument(it) }
                    pdfDocument = doc
                    pageCount = pdfiumCore.getPageCount(doc)
                    showPage(0)
                }.onFailure { e ->
                    binding.pbLoading.visibility = View.GONE
                    if (e.message?.contains("password", ignoreCase = true) == true
                        || e is com.shockwave.pdfium.PdfPasswordException) {
                        askForPassword(wrongPassword = password != null)
                    } else {
                        binding.tvPageCount.text    = "Error"
                        binding.tvPageIndicator.text = e.message ?: "Cannot open PDF"
                    }
                }
            }
        }
    }

    // ── Password dialog ───────────────────────────────────────────────────────

    private fun askForPassword(wrongPassword: Boolean) {
        val density = resources.displayMetrics.density
        val input = EditText(this).apply {
            hint = "PDF Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding((16 * density).toInt(), (12 * density).toInt(),
                       (16 * density).toInt(), (12 * density).toInt())
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(-1, -2).apply {
                val m = (20 * density).toInt()
                setMargins(m, if (wrongPassword) (4 * density).toInt() else m, m, m)
            }
            addView(input, lp)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("🔒 Password Protected PDF")
            .setMessage(if (wrongPassword) "Wrong password. Try again:" else "Enter the PDF password:")
            .setView(wrapper)
            .setPositiveButton("Open") { _, _ ->
                val pwd = input.text.toString()
                if (pwd.isNotEmpty()) tryOpenPdf(pwd)
                else askForPassword(false)
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .show()

        // Auto-open keyboard
        input.requestFocus()
    }

    // ── Render a page ─────────────────────────────────────────────────────────

    private fun showPage(index: Int) {
        val doc = pdfDocument ?: return
        val target = index.coerceIn(0, pageCount - 1)
        currentPage = target

        binding.tvPageCount.text    = "${target + 1} / $pageCount"
        binding.tvPageIndicator.text = "${target + 1} / $pageCount"
        binding.btnPrevPage.alpha   = if (target > 0)             1f else 0.35f
        binding.btnNextPage.alpha   = if (target < pageCount - 1) 1f else 0.35f
        binding.btnPrevPage.isEnabled = target > 0
        binding.btnNextPage.isEnabled = target < pageCount - 1
        binding.pbLoading.visibility  = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            val bmp = runCatching {
                val screenW = resources.displayMetrics.widthPixels
                pdfiumCore.openPage(doc, target)
                val pw = pdfiumCore.getPageWidthPoint(doc, target)
                val ph = pdfiumCore.getPageHeightPoint(doc, target)
                val w = screenW * 2   // 2× resolution for crisp zoom
                val h = if (pw > 0) (w.toLong() * ph / pw).toInt() else w
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                b.eraseColor(android.graphics.Color.WHITE)
                pdfiumCore.renderPageBitmap(doc, b, target, 0, 0, w, h)
                b
            }.getOrNull()

            withContext(Dispatchers.Main) {
                if (bmp != null) binding.ivPdfPage.setImageBitmap(bmp)
                binding.pbLoading.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfDocument?.let { pdfiumCore.closeDocument(it) }
    }
}
