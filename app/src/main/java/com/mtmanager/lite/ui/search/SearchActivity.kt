package com.mtmanager.lite.ui.search

import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mtmanager.lite.R
import com.mtmanager.lite.databinding.ActivitySearchBinding
import com.mtmanager.lite.model.FileItem
import com.mtmanager.lite.utils.FileUtils
import com.mtmanager.lite.utils.PermissionHelper
import com.mtmanager.lite.utils.SearchIndex
import com.mtmanager.lite.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_START_PATH = "extra_start_path"
        const val EXTRA_OPEN_DIR = "extra_open_dir"
    }

    private lateinit var binding: ActivitySearchBinding
    private var searchJob: Job? = null
    private var activeFilter: Set<String>? = null
    private var activeChip: TextView? = null
    private lateinit var startPath: File
    private var indexReady = false

    private val results = mutableListOf<File>()
    private lateinit var resultsAdapter: SearchResultsAdapter

    private data class FilterOption(val label: String, val extensions: Set<String>?)

    private val filterOptions = listOf(
        FilterOption("All", null),
        FilterOption("Docs", FileUtils.DOC_EXTENSIONS),
        FilterOption("Images", FileUtils.IMAGE_EXTENSIONS),
        FilterOption("Audio", FileUtils.AUDIO_EXTENSIONS),
        FilterOption("Video", FileUtils.VIDEO_EXTENSIONS),
        FilterOption("Archives", FileUtils.ARCHIVE_EXTENSIONS),
        FilterOption("Code", FileUtils.CODE_EXTENSIONS)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startPath = intent.getStringExtra(EXTRA_START_PATH)?.let { File(it) }
            ?.takeIf { it.isDirectory && it.canRead() }
            ?: Environment.getExternalStorageDirectory()

        SearchIndex.init(this)

        binding.btnBack.setOnClickListener { finish() }

        resultsAdapter = SearchResultsAdapter(results) { file ->
            openFile(file)
        }

        binding.searchResultsRv.apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = resultsAdapter
        }

        setupFilterChips()

        if (!PermissionHelper.hasStoragePermission(this)) {
            showEmptyState("Storage permission required")
            binding.etSearch.isEnabled = false
            binding.etSearch.hint = "Grant storage permission first"
            return
        }

        showEmptyState("Preparing search index…")

        lifecycleScope.launch {
            val indexed = withContext(Dispatchers.IO) {
                SearchIndex.isIndexed()
            }
            if (indexed) {
                val age = withContext(Dispatchers.IO) {
                    SearchIndex.getIndexAgeMs()
                }
                if (age > 86_400_000L) {
                    withContext(Dispatchers.IO) { SearchIndex.clearIndex() }
                    buildIndex()
                } else {
                    indexReady = true
                    val count = withContext(Dispatchers.IO) { SearchIndex.getIndexedCount() }
                    showEmptyState("Search in /${startPath.name} (${count} files)")
                    setupTextWatcher()
                }
            } else {
                buildIndex()
            }
        }
    }

    private suspend fun buildIndex() {
        showEmptyState("Building search index…")
        binding.tvResultsHeader.visibility = View.VISIBLE
        binding.tvResultsHeader.text = "Indexing files…"

        val count = withContext(Dispatchers.IO) {
            SearchIndex.indexDirectory(startPath, maxDepth = 3)
        }

        if (count >= 0) {
            indexReady = true
            binding.tvResultsHeader.visibility = View.GONE
            showEmptyState("Search in /${startPath.name} (${count} files indexed)")
            setupTextWatcher()
        } else {
            indexReady = true
            showEmptyState("Search in /${startPath.name}")
            setupTextWatcher()
        }
    }

    private fun openFile(file: File) {
        if (file.isDirectory) {
            val resultIntent = android.content.Intent()
            resultIntent.putExtra(EXTRA_OPEN_DIR, file.absolutePath)
            setResult(RESULT_OK, resultIntent)
            finish()
            return
        }
        val item = FileItem(file)
        when {
            item.isTextFile() -> {
                startActivity(android.content.Intent(this,
                    com.mtmanager.lite.ui.editor.FileEditorActivity::class.java).apply {
                    putExtra(com.mtmanager.lite.ui.editor.FileEditorActivity.EXTRA_FILE_PATH,
                        file.absolutePath)
                })
            }
            item.isImageFile() -> {
                startActivity(android.content.Intent(this,
                    com.mtmanager.lite.ui.viewer.ImageViewerActivity::class.java).apply {
                    putExtra(com.mtmanager.lite.ui.viewer.ImageViewerActivity.EXTRA_FILE_PATH,
                        file.absolutePath)
                })
            }
            item.isAudioFile() -> {
                startActivity(android.content.Intent(this,
                    com.mtmanager.lite.ui.viewer.AudioPlayerActivity::class.java).apply {
                    putExtra(com.mtmanager.lite.ui.viewer.AudioPlayerActivity.EXTRA_FILE_PATH,
                        file.absolutePath)
                })
            }
            item.isVideoFile() -> {
                startActivity(android.content.Intent(this,
                    com.mtmanager.lite.ui.viewer.VideoPlayerActivity::class.java).apply {
                    putExtra(com.mtmanager.lite.ui.viewer.VideoPlayerActivity.EXTRA_FILE_PATH,
                        file.absolutePath)
                })
            }
            item.extension == "pdf" -> {
                startActivity(android.content.Intent(this,
                    com.mtmanager.lite.ui.viewer.PdfViewerActivity::class.java).apply {
                    putExtra(com.mtmanager.lite.ui.viewer.PdfViewerActivity.EXTRA_FILE_PATH,
                        file.absolutePath)
                })
            }
            item.extension in setOf("csv", "tsv") -> {
                startActivity(android.content.Intent(this,
                    com.mtmanager.lite.ui.viewer.CsvViewerActivity::class.java).apply {
                    putExtra(com.mtmanager.lite.ui.viewer.CsvViewerActivity.EXTRA_FILE_PATH,
                        file.absolutePath)
                })
            }
            item.extension in setOf("docx", "odt") -> {
                startActivity(android.content.Intent(this,
                    com.mtmanager.lite.ui.viewer.DocxViewerActivity::class.java).apply {
                    putExtra(com.mtmanager.lite.ui.viewer.DocxViewerActivity.EXTRA_FILE_PATH,
                        file.absolutePath)
                })
            }
            item.isApkFile() -> showApkInfoDialog(file)
            else -> {
                val resultIntent = android.content.Intent()
                resultIntent.putExtra(EXTRA_OPEN_DIR, file.parent)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun showApkInfoDialog(file: File) {
        val pm = packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
        val msg = if (info != null) {
            "Package: ${info.packageName}\nVersion: ${info.versionName}\nSize: ${
                android.text.format.Formatter.formatShortFileSize(this, file.length())}"
        } else {
            "Could not read APK info.\nSize: ${
                android.text.format.Formatter.formatShortFileSize(this, file.length())}"
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setMessage(msg)
            .setPositiveButton("Install") { _, _ ->
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this, "$packageName.provider", file)
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot install: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private var textWatcherSetup = false

    private fun setupTextWatcher() {
        if (textWatcherSetup) return
        textWatcherSetup = true

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { runSearch() }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.requestFocus()
        binding.etSearch.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun setupFilterChips() {
        val density = resources.displayMetrics.density
        val hPad = (12 * density).toInt()
        val vPad = (4 * density).toInt()
        val chips = mutableListOf<TextView>()

        val chipNormalBg = obtainStyledAttributes(intArrayOf(R.attr.xyvionBgChipNormal)).let { ta ->
            val d = ta.getDrawable(0); ta.recycle(); d
        }
        val chipSelectedBg = obtainStyledAttributes(intArrayOf(R.attr.xyvionBgChipSelected)).let { ta ->
            val d = ta.getDrawable(0); ta.recycle(); d
        }
        val textColorPrimary = obtainStyledAttributes(intArrayOf(R.attr.xyvionTextPrimary)).let { ta ->
            val c = ta.getColor(0, 0); ta.recycle(); c
        }
        val textColorSecondary = obtainStyledAttributes(intArrayOf(R.attr.xyvionTextSecondary)).let { ta ->
            val c = ta.getColor(0, 0); ta.recycle(); c
        }

        filterOptions.forEachIndexed { index, option ->
            val chip = TextView(this).apply {
                text = option.label
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding(hPad, vPad, hPad, vPad)
                gravity = Gravity.CENTER
                background = chipNormalBg?.constantState?.newDrawable()?.mutate()
                setTextColor(textColorSecondary)
                tag = index
                setOnClickListener {
                    val clicked = it as TextView
                    val idx = clicked.tag as Int
                    val opt = filterOptions[idx]
                    if (activeChip == clicked) return@setOnClickListener
                    activeChip?.background = chipNormalBg?.constantState?.newDrawable()?.mutate()
                    activeChip?.setTextColor(textColorSecondary)
                    activeChip = clicked
                    clicked.background = chipSelectedBg?.constantState?.newDrawable()?.mutate()
                    clicked.setTextColor(textColorPrimary)
                    activeFilter = opt.extensions
                    runSearch()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = (6 * density).toInt() }
            chip.layoutParams = lp
            chips.add(chip)
        }
        chips.forEach { binding.filterChips.addView(it) }
        activeFilter = null
        activeChip = chips[0]
        chips[0].background = chipSelectedBg?.constantState?.newDrawable()?.mutate()
        chips[0].setTextColor(textColorPrimary)
    }

    private fun runSearch() {
        val query = binding.etSearch.text?.toString()?.trim() ?: ""
        if (query.isEmpty()) {
            showEmptyState("Search in /${startPath.name}")
            return
        }
        if (query.length < 2) {
            showEmptyState("Type at least 2 characters")
            return
        }

        searchJob?.cancel()
        val currentFilter = activeFilter
        binding.tvResultsHeader.visibility = View.VISIBLE
        binding.tvResultsHeader.text = "Searching for \"$query\"…"

        searchJob = lifecycleScope.launch {
            delay(200)
            try {
                val ftsResults = if (indexReady) {
                    withContext(Dispatchers.IO) {
                        SearchIndex.search(query, currentFilter)
                    }
                } else null

                if (ftsResults != null && ftsResults.isNotEmpty()) {
                    results.clear()
                    results.addAll(ftsResults.map { it.toFile() })
                    resultsAdapter.notifyDataSetChanged()
                    binding.emptyState.visibility = View.GONE
                    binding.searchResultsRv.visibility = View.VISIBLE
                    binding.tvResultsHeader.visibility = View.VISIBLE
                    binding.tvResultsHeader.text = "${results.size} result(s) for \"$query\""
                } else {
                    val found = withContext(Dispatchers.IO) {
                        FileUtils.searchFiles(startPath, query, filter = currentFilter)
                    }
                    results.clear()
                    results.addAll(found)
                    resultsAdapter.notifyDataSetChanged()
                    if (found.isEmpty()) {
                        showEmptyState("No files found for \"$query\"")
                        binding.tvResultsHeader.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.searchResultsRv.visibility = View.VISIBLE
                        binding.tvResultsHeader.visibility = View.VISIBLE
                        binding.tvResultsHeader.text = "${found.size} result(s) for \"$query\""
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            } catch (e: Exception) {
                showEmptyState("Search error: ${e.message}")
            }
        }
    }

    private fun showEmptyState(message: String) {
        results.clear()
        resultsAdapter.notifyDataSetChanged()
        binding.emptyState.visibility = View.VISIBLE
        binding.searchResultsRv.visibility = View.GONE
        binding.tvResultsHeader.visibility = View.GONE
        binding.tvEmptyMsg.text = message
    }
}


class SearchResultsAdapter(
    private val items: List<File>,
    private val onClick: (File) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val rowCard: android.view.View = view.findViewById(R.id.itemRowCard)
        val ivIcon: android.widget.ImageView = view.findViewById(R.id.ivFileIcon)
        val iconContainer: android.widget.FrameLayout = view.findViewById(R.id.iconContainer)
        val tvName: TextView = view.findViewById(R.id.tvFileName)
        val tvPath: TextView = view.findViewById(R.id.tvFileDate)
        val tvSize: TextView = view.findViewById(R.id.tvFileSize)
        val tvBadge: TextView = view.findViewById(R.id.tvExtBadge)
        val tvDot: TextView = view.findViewById(R.id.tvDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]
        val item = FileItem(file)
        val ctx = holder.itemView.context

        if (file.isDirectory) {
            holder.ivIcon.setImageResource(R.drawable.img_folder_new)
            holder.ivIcon.clearColorFilter()
            holder.ivIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            holder.iconContainer.setBackgroundResource(android.R.color.transparent)
        } else {
            when (item.extension.lowercase()) {
                "py"  -> { holder.ivIcon.setImageResource(R.drawable.img_python); holder.ivIcon.clearColorFilter(); holder.ivIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP }
                "bak" -> { holder.ivIcon.setImageResource(R.drawable.img_bak); holder.ivIcon.clearColorFilter(); holder.ivIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE }
                "txt" -> { holder.ivIcon.setImageResource(R.drawable.img_txt); holder.ivIcon.clearColorFilter(); holder.ivIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE }
                "enc" -> { holder.ivIcon.setImageResource(R.drawable.img_enc); holder.ivIcon.clearColorFilter(); holder.ivIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE }
                "pdf" -> { holder.ivIcon.setImageResource(R.drawable.img_pdf); holder.ivIcon.clearColorFilter(); holder.ivIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE }
                "zip", "gz", "tar", "rar", "7z", "xz", "bz2" -> { holder.ivIcon.setImageResource(R.drawable.img_zip); holder.ivIcon.clearColorFilter(); holder.ivIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE }
                "jpg", "jpeg", "png", "gif", "webp", "bmp"   -> { holder.ivIcon.setImageResource(R.drawable.img_jpg); holder.ivIcon.clearColorFilter(); holder.ivIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE }
                "mp3", "wav", "aac", "flac", "ogg", "m4a"    -> { holder.ivIcon.setImageResource(R.drawable.img_mp3); holder.ivIcon.clearColorFilter(); holder.ivIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE }
                "mp4", "mkv", "avi", "mov", "wmv", "3gp"     -> { holder.ivIcon.setImageResource(R.drawable.img_mp4); holder.ivIcon.clearColorFilter(); holder.ivIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE }
                else  -> { holder.ivIcon.setImageResource(R.drawable.ic_file_flat); holder.ivIcon.setColorFilter(ctx.getColor(R.color.file_default)); holder.ivIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE }
            }
            holder.iconContainer.setBackgroundResource(R.drawable.bg_icon_container)
        }

        holder.tvName.text = file.name
        holder.tvPath.text = file.parent?.replaceBeforeLast("/", "")?.removePrefix("/") ?: ""
        holder.tvDot.visibility = if (file.isDirectory) View.GONE else View.VISIBLE
        holder.tvSize.text = if (file.isDirectory) "" else item.formattedSize()

        if (!file.isDirectory && item.extension.isNotEmpty()) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text = item.extension.uppercase()
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        holder.rowCard.setOnClickListener { onClick(file) }
    }

    override fun getItemCount() = items.size
}