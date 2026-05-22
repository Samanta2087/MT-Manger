package com.fyloxen.app.ui.editor

import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fyloxen.app.R
import com.fyloxen.app.databinding.ActivityFileEditorBinding
import com.fyloxen.app.utils.FileUtils
import com.fyloxen.app.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        private const val LARGE_FILE_THRESHOLD = 5L * 1024 * 1024   // 5 MB
    }

    private lateinit var binding: ActivityFileEditorBinding
    private lateinit var file: File
    private lateinit var lineAdapter: LineAdapter

    // Large-file streaming mode (active when file ≥ 5 MB)
    private var largeEngine: LargeFileEditorEngine? = null
    private var streamAdapter: StreamingLineAdapter? = null
    private val isLargeMode get() = largeEngine != null

    private var originalContent = ""
    private var isModified = false
    private var isReadOnly = false
    private var fontSize = 13f

    // Undo / Redo — snapshots of the whole lines array
    private val undoStack = ArrayDeque<List<String>>(20)
    private val redoStack = ArrayDeque<List<String>>(20)
    private var ignoreUndoCapture = false

    // Find state
    private data class Match(val line: Int, val start: Int, val end: Int)
    private var findMatches = listOf<Match>()
    private var currentMatchIdx = -1

    // Floating text action popup
    private var textActionPopup: PopupWindow? = null
    private lateinit var popupView: View

    // Prevents popup from being dismissed during model updates (cut/paste/select-all)
    private var suppressPopupDismiss = false

    // Debounce handler — prevents the ongoing long-press touch from
    // immediately dismissing the popup the moment it appears.
    private val selectionHandler = Handler(Looper.getMainLooper())
    private var pendingShow: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityFileEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val resolvedFile = resolveFileFromIntent()
        if (resolvedFile == null) {
            Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        file = resolvedFile
        isReadOnly = !file.canWrite()

        setupToolbar()
        setupTextActionBar()
        setupFindReplace()

        if (file.length() >= LARGE_FILE_THRESHOLD) {
            loadLargeFile()
        } else {
            setupRecyclerView()
            loadFile()
        }
    }

    // ── Intent resolution ─────────────────────────────────────────────────────
    private fun resolveFileFromIntent(): File? {
        intent.getStringExtra(EXTRA_FILE_PATH)?.let { return File(it) }
        val uri = intent.data ?: return null
        return when (uri.scheme) {
            "file" -> uri.path?.let { File(it) }
            "content" -> try {
                val name = getFileNameFromUri(uri) ?: "opened_file.txt"
                val tmp = File(cacheDir, name)
                contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                tmp
            } catch (e: Exception) { null }
            else -> null
        }
    }

    private fun getFileNameFromUri(uri: android.net.Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = c.getString(idx)
            }
        }
        return name ?: uri.lastPathSegment
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { onBackPressed() }
        binding.tvEditorFileName.text = file.name
        binding.tvEditorPath.text = file.parent ?: ""
        binding.tvFileType.text = file.extension.uppercase().ifEmpty { "TXT" }

        binding.btnSave.setOnClickListener { saveFile() }
        binding.btnUndo.setOnClickListener { undo() }
        binding.btnRedo.setOnClickListener { redo() }
        binding.btnFind.setOnClickListener {
            binding.findReplaceBar.visibility =
                if (binding.findReplaceBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.btnEditorMenu.setOnClickListener { showEditorMenu(it) }

        if (isReadOnly) {
            binding.btnSave.isEnabled = false
            Toast.makeText(this, getString(R.string.read_only_warning), Toast.LENGTH_SHORT).show()
        }
    }

    // ── RecyclerView setup ────────────────────────────────────────────────────
    private fun setupRecyclerView() {
        lineAdapter = LineAdapter(
            lines         = mutableListOf(""),
            onChanged     = { onEditorContentChanged() },
            onCursorMoved = { line, col -> updateCursorPosition(line, col) },
            onSelectionChanged = { hasSelection -> onSelectionChanged(hasSelection) },
            onLongPress   = { showTextActionBar() }
        ).apply {
            isReadOnly = this@FileEditorActivity.isReadOnly
            fontSize   = this@FileEditorActivity.fontSize
        }

        binding.rvEditor.apply {
            layoutManager = LinearLayoutManager(this@FileEditorActivity)
            adapter        = lineAdapter
            isFocusable    = false
            setItemViewCacheSize(30)
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    if (lineAdapter.isSelectAllActive) {
                        lineAdapter.clearSelectAll()
                    }
                    val et = lineAdapter.lastFocusedEdit
                    val hasActiveSelection = et != null &&
                        et.selectionStart != et.selectionEnd
                    if (!hasActiveSelection) hideTextActionBar()
                }
                false
            }
        }
    }

    // ── Content changed callback ──────────────────────────────────────────────
    fun onEditorContentChanged() {
        if (ignoreUndoCapture) return
        captureUndoState()
        setModified(true)
    }

    // ── Selection show/hide (debounced) ──────────────────────────────────────────
    private fun onSelectionChanged(hasSelection: Boolean) {
        // In large-file mode lineAdapter is never initialized — guard required
        if (!isLargeMode) {
            if (lineAdapter.isSelectAllActive) return
        }
        if (suppressPopupDismiss) return

        // Always cancel any pending show first
        pendingShow?.let { selectionHandler.removeCallbacks(it) }
        pendingShow = null

        if (hasSelection) {
            val r = Runnable {
                pendingShow = null
                if (!isLargeMode) {
                    val et = lineAdapter.lastFocusedEdit ?: return@Runnable
                    if (et.selectionStart != et.selectionEnd) showTextActionBar()
                } else {
                    val et = streamAdapter?.lastFocusedEdit ?: return@Runnable
                    if (et.selectionStart != et.selectionEnd) showTextActionBar()
                }
            }
            pendingShow = r
            selectionHandler.postDelayed(r, 300)
        } else {
            hideTextActionBar()
        }
    }

    private fun showTextActionBar() {
        // Already showing — just reposition
        if (textActionPopup?.isShowing == true) {
            repositionPopup(); return
        }
        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false   // not focusable — keyboard stays open
        )
        popup.isOutsideTouchable = false  // don't let the long-press gesture dismiss it
        popup.isTouchable = true
        popup.setOnDismissListener { textActionPopup = null }
        textActionPopup = popup

        // Measure so we know popup size before positioning
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val (x, y) = calcPopupXY()
        popup.showAtLocation(binding.root, Gravity.NO_GRAVITY, x, y)
    }

    private fun repositionPopup() {
        val popup = textActionPopup ?: return
        val (x, y) = calcPopupXY()
        popup.update(x, y, -1, -1)
    }

    /** Returns (x, y) in screen coordinates to place the popup above the selection. */
    private fun calcPopupXY(): Pair<Int, Int> {
        val et      = lineAdapter.lastFocusedEdit
        val screenW = resources.displayMetrics.widthPixels
        val popupW  = popupView.measuredWidth.takeIf  { it > 0 } ?: (48 * 5)
        val popupH  = popupView.measuredHeight.takeIf { it > 0 } ?: 46
        val margin  = 12

        if (et == null || et.layout == null) {
            return Pair((screenW - popupW) / 2, 160)
        }

        // Status bar height so showAtLocation y=0 == top of screen
        val statusBarH = run {
            val r = android.util.TypedValue()
            if (theme.resolveAttribute(android.R.attr.actionBarSize, r, true)) 0
            val res = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (res > 0) resources.getDimensionPixelSize(res) else 0
        }

        val selStart = et.selectionStart.coerceIn(0, et.text?.length ?: 0)
        val layout   = et.layout
        val line     = layout.getLineForOffset(selStart)
        val lineTop  = layout.getLineTop(line)
        val charX    = layout.getPrimaryHorizontal(selStart).toInt()

        // Use getLocationOnScreen for accuracy with soft keyboard
        val loc = IntArray(2)
        et.getLocationOnScreen(loc)

        val rawX = loc[0] + charX - popupW / 2
        val rawY = loc[1] + lineTop - et.scrollY - popupH - margin - statusBarH

        val finalX = rawX.coerceIn(margin, screenW - popupW - margin)
        val finalY = rawY.coerceAtLeast(margin)

        return Pair(finalX, finalY)
    }

    private fun hideTextActionBar() {
        // Keep popup alive while select-all mode is active OR during model updates
        if (!isLargeMode && (lineAdapter.isSelectAllActive || suppressPopupDismiss)) return
        if (isLargeMode && suppressPopupDismiss) return
        try {
            textActionPopup?.dismiss()
        } catch (_: Exception) { /* activity may be finishing */ }
        textActionPopup = null
    }

    /** Force-close the popup regardless of select-all state (used by select-all actions). */
    private fun forceHideTextActionBar() {
        textActionPopup?.dismiss()
        textActionPopup = null
    }

    /** Gathers (lineIndex, selStart..selEnd) pairs for visible selections across rows. */
    private fun gatherSelections(): List<Pair<Int, Pair<Int, Int>>> {
        val result = mutableListOf<Pair<Int, Pair<Int, Int>>>()
        val rv = binding.rvEditor
        for (i in 0 until rv.childCount) {
            val vh = rv.getChildViewHolder(rv.getChildAt(i)) as? LineAdapter.LineVH ?: continue
            val pos = vh.bindingAdapterPosition
            if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) continue
            val et = vh.b.etLine
            val textLen = et.text?.length ?: 0
            val s = et.selectionStart
            val e = et.selectionEnd
            if (s >= 0 && e > s && s < textLen && e <= textLen) {
                result.add(Pair(pos, Pair(s, e)))
            }
        }
        return result
    }

    // ── Text action bar (floating popup) ───────────────────────────────────────
    private fun setupTextActionBar() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        popupView = layoutInflater.inflate(R.layout.popup_text_action_bar, null)

        popupView.findViewById<ImageButton>(R.id.btnActSearch).setOnClickListener {
            val et = lineAdapter.lastFocusedEdit ?: return@setOnClickListener
            val sel = if (et.selectionStart < et.selectionEnd)
                et.text?.substring(et.selectionStart, et.selectionEnd) ?: "" else ""
            hideTextActionBar()
            binding.findReplaceBar.visibility = View.VISIBLE
            binding.etFindText.setText(sel)
            binding.etFindText.selectAll()
            binding.etFindText.requestFocus()
        }

        popupView.findViewById<ImageButton>(R.id.btnActSelectAll).setOnClickListener {
            if (lineAdapter.isSelectAllActive) {
                lineAdapter.clearSelectAll()
                forceHideTextActionBar()
            } else {
                lineAdapter.selectAll()
                showTextActionBar()
            }
        }

        popupView.findViewById<ImageButton>(R.id.btnActCut).setOnClickListener {
            if (lineAdapter.isSelectAllActive) {
                // Cut entire file content
                val allText = lineAdapter.getContent()
                clipboard.setPrimaryClip(ClipData.newPlainText("cut_all", allText))
                lineAdapter.lines.clear()
                lineAdapter.lines.add("")
                suppressPopupDismiss = true
                lineAdapter.isSelectAllActive = false
                lineAdapter.notifyDataSetChanged()
                suppressPopupDismiss = false
                onEditorContentChanged()
                // Re-show popup so Paste remains accessible
                binding.root.postDelayed({ showTextActionBar() }, 100)
                Toast.makeText(this, "All text cut", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Multi-range cut: gather selected ranges from all rows
            val selRanges = gatherSelections()
            if (selRanges.isNotEmpty()) {
                val sb = StringBuilder()
                for ((idx, r) in selRanges.withIndex()) {
                    if (idx > 0) sb.append('\n')
                    sb.append(lineAdapter.lines[selRanges[idx].first].substring(r.second.first, r.second.second))
                }
                clipboard.setPrimaryClip(ClipData.newPlainText("cut", sb.toString()))
                // Delete in reverse order to preserve indices
                for (i in selRanges.indices.reversed()) {
                    val (lineIdx, range) = selRanges[i]
                    val line = lineAdapter.lines[lineIdx]
                    lineAdapter.lines[lineIdx] = line.substring(0, range.first) + line.substring(range.second)
                }
                // Merge consecutive cut lines if they're now adjacent fragments
                lineAdapter.isSelectAllActive = false
                lineAdapter.notifyDataSetChanged()
                forceHideTextActionBar()
                onEditorContentChanged()
                return@setOnClickListener
            }
            val et = lineAdapter.lastFocusedEdit ?: return@setOnClickListener
            val s = et.selectionStart; val e = et.selectionEnd
            if (s >= e) return@setOnClickListener
            clipboard.setPrimaryClip(ClipData.newPlainText("cut", et.text?.substring(s, e)))
            et.text?.delete(s, e)
            hideTextActionBar()
            Toast.makeText(this, "Cut", Toast.LENGTH_SHORT).show()
        }

        popupView.findViewById<ImageButton>(R.id.btnActCopy).setOnClickListener {
            if (lineAdapter.isSelectAllActive) {
                // Copy entire file content
                val allText = lineAdapter.getContent()
                clipboard.setPrimaryClip(ClipData.newPlainText("copy_all", allText))
                lineAdapter.clearSelectAll()
                forceHideTextActionBar()
                Toast.makeText(this, "All text copied", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Check for custom multi-line selection
            val selRanges = gatherSelections()
            if (selRanges.isNotEmpty()) {
                val sb = StringBuilder()
                for ((idx, r) in selRanges.withIndex()) {
                    if (idx > 0) sb.append('\n')
                    sb.append(lineAdapter.lines[selRanges[idx].first].substring(r.second.first, r.second.second))
                }
                clipboard.setPrimaryClip(ClipData.newPlainText("copy", sb.toString()))
                hideTextActionBar()
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val et = lineAdapter.lastFocusedEdit ?: return@setOnClickListener
            val s = et.selectionStart; val e = et.selectionEnd
            if (s >= e) return@setOnClickListener
            clipboard.setPrimaryClip(ClipData.newPlainText("copy", et.text?.substring(s, e)))
            hideTextActionBar()
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }

        popupView.findViewById<ImageButton>(R.id.btnActPaste).setOnClickListener {
            if (lineAdapter.isSelectAllActive) {
                // Replace entire file content with clipboard
                val clip = clipboard.primaryClip ?: run {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (clip.itemCount == 0) return@setOnClickListener
                val text = clip.getItemAt(0).coerceToText(this).toString()
                val newLines = text.split("\n").toMutableList()
                lineAdapter.lines.clear()
                lineAdapter.lines.addAll(if (newLines.isEmpty()) mutableListOf("") else newLines)
                suppressPopupDismiss = true
                lineAdapter.isSelectAllActive = false
                lineAdapter.notifyDataSetChanged()
                suppressPopupDismiss = false
                onEditorContentChanged()
                forceHideTextActionBar()
                Toast.makeText(this, "Pasted", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val et = lineAdapter.lastFocusedEdit ?: return@setOnClickListener
            val pos = lineAdapter.lastFocusedLine
            val clip = clipboard.primaryClip ?: return@setOnClickListener
            if (clip.itemCount == 0) return@setOnClickListener
            val text  = clip.getItemAt(0).coerceToText(this).toString()
            val start = et.selectionStart.coerceAtLeast(0)
            val end   = et.selectionEnd.coerceAtLeast(start)
            if (pos < 0 || pos >= lineAdapter.lines.size) {
                et.text?.replace(start, end, text)
            } else if (!text.contains('\n')) {
                // Single-line paste: simple in-place replacement
                et.text?.replace(start, end, text)
            } else {
                // Multi-line paste: insert directly into model
                lineAdapter.insertMultiLine(pos, text, start, end)
            }
            hideTextActionBar()
            Toast.makeText(this, "Pasted", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Find / Replace ────────────────────────────────────────────────────────
    private fun setupFindReplace() {
        binding.btnCloseFindReplace.setOnClickListener {
            binding.findReplaceBar.visibility = View.GONE
            findMatches = emptyList(); currentMatchIdx = -1
            binding.tvMatchCount.text = ""
        }
        binding.etFindText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { performFind() }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.btnFindPrev.setOnClickListener { navigateMatch(-1) }
        binding.btnFindNext.setOnClickListener { navigateMatch(1) }
        binding.btnReplace.setOnClickListener   { replaceCurrentMatch() }
        binding.btnReplaceAll.setOnClickListener { replaceAll() }
    }

    private fun performFind() {
        val query = binding.etFindText.text?.toString() ?: return
        findMatches = emptyList(); currentMatchIdx = -1
        if (query.isEmpty()) { binding.tvMatchCount.text = ""; return }
        val matches = mutableListOf<Match>()
        lineAdapter.lines.forEachIndexed { lineIdx, lineText ->
            var idx = lineText.indexOf(query)
            while (idx >= 0) {
                matches.add(Match(lineIdx, idx, idx + query.length))
                idx = lineText.indexOf(query, idx + 1)
            }
        }
        findMatches = matches
        currentMatchIdx = if (matches.isNotEmpty()) 0 else -1
        updateMatchCount()
        if (currentMatchIdx >= 0) scrollToMatch(findMatches[currentMatchIdx])
    }

    private fun navigateMatch(dir: Int) {
        if (findMatches.isEmpty()) return
        currentMatchIdx = (currentMatchIdx + dir + findMatches.size) % findMatches.size
        updateMatchCount()
        scrollToMatch(findMatches[currentMatchIdx])
    }

    private fun scrollToMatch(m: Match) {
        binding.rvEditor.scrollToPosition(m.line)
        binding.rvEditor.post {
            val vh = binding.rvEditor.findViewHolderForAdapterPosition(m.line) as? LineAdapter.LineVH
            vh?.b?.etLine?.apply {
                requestFocus()
                setSelection(m.start.coerceAtMost(text?.length ?: 0),
                             m.end.coerceAtMost(text?.length ?: 0))
            }
        }
    }

    private fun updateMatchCount() {
        binding.tvMatchCount.text = if (findMatches.isEmpty()) "0 matches"
        else "${currentMatchIdx + 1}/${findMatches.size}"
    }

    private fun replaceCurrentMatch() {
        if (currentMatchIdx < 0 || currentMatchIdx >= findMatches.size) return
        val replacement = binding.etReplaceText.text?.toString() ?: return
        val m = findMatches[currentMatchIdx]
        val lineText = lineAdapter.lines[m.line]
        lineAdapter.lines[m.line] = lineText.substring(0, m.start) + replacement + lineText.substring(m.end)
        lineAdapter.notifyItemChanged(m.line)
        onEditorContentChanged()
        performFind()
    }

    private fun replaceAll() {
        val query = binding.etFindText.text?.toString()?.takeIf { it.isNotEmpty() } ?: return
        val replacement = binding.etReplaceText.text?.toString() ?: return
        var count = 0
        lineAdapter.lines.forEachIndexed { i, line ->
            val newLine = line.replace(query, replacement)
            if (newLine != line) { lineAdapter.lines[i] = newLine; count++; lineAdapter.notifyItemChanged(i) }
        }
        Toast.makeText(this, "Replaced $count lines", Toast.LENGTH_SHORT).show()
        onEditorContentChanged()
        findMatches = emptyList(); currentMatchIdx = -1; binding.tvMatchCount.text = ""
    }

    // ── Load / Save ─────────────────────────────────────────────

    // ── LARGE FILE MODE ────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun loadLargeFile() {
        val prog = ProgressDialog(this).apply {
            setTitle("Opening — ${file.name}")
            setMessage("Indexing file… (${formatBytes(file.length())})")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false; max = 100; setCancelable(false); show()
        }
        val engine = LargeFileEditorEngine(file)
        largeEngine = engine

        lifecycleScope.launch {
            engine.buildIndex { pct ->
                runOnUiThread { prog.progress = pct }
            }
            prog.dismiss()

            // Set up streaming RecyclerView
            val sa = StreamingLineAdapter(
                engine         = engine,
                scope          = lifecycleScope,
                isReadOnly     = isReadOnly,
                fontSize       = fontSize,
                onChanged      = { setModified(true) },
                onCursorMoved  = { l, c -> updateCursorPosition(l, c) },
                onSelectionChanged = { has -> onSelectionChanged(has) }
            )
            streamAdapter = sa

            binding.rvEditor.apply {
                layoutManager = LinearLayoutManager(this@FileEditorActivity)
                adapter        = sa
                isFocusable    = false
                setItemViewCacheSize(40)
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        val et = sa.lastFocusedEdit
                        if (et == null || et.selectionStart == et.selectionEnd) hideTextActionBar()
                    }
                    false
                }
            }

            binding.tvEncoding.text = "UTF-8 • STREAMING"
            binding.tvFileType.text = file.extension.uppercase().ifEmpty { "TXT" }
            binding.btnSave.isEnabled = !isReadOnly
            setModified(false)

            Toast.makeText(this@FileEditorActivity,
                "${formatBytes(file.length())} file — ${engine.lineCount} lines loaded",
                Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLargeFile() {
        val engine = largeEngine ?: return
        val prog = ProgressDialog(this).apply {
            setTitle("Saving — ${file.name}")
            setMessage("Writing ${engine.lineCount} lines…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false; max = 100; setCancelable(false); show()
        }
        lifecycleScope.launch {
            val result = engine.saveToFile { pct ->
                runOnUiThread { prog.progress = pct }
            }
            prog.dismiss()
            result.onSuccess {
                setModified(false)
                Toast.makeText(this@FileEditorActivity,
                    getString(R.string.file_saved), Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(this@FileEditorActivity,
                    "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun formatBytes(b: Long): String = when {
        b >= 1_073_741_824L -> "%.1f GB".format(b / 1_073_741_824.0)
        b >= 1_048_576L     -> "%.1f MB".format(b / 1_048_576.0)
        b >= 1_024L         -> "%.1f KB".format(b / 1_024.0)
        else                -> "$b B"
    }

    // ── SMALL FILE MODE ──────────────────────────────────────────────────────────
    private fun loadFile() {
        binding.tvEncoding.text = "Loading..."
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            val fileSize = file.length()
            val result = withContext(Dispatchers.IO) {
                if (fileSize > 100 * 1024) FileUtils.readTextLimited(file, 100 * 1024)
                else FileUtils.readText(file)
            }
            result.onSuccess { content ->
                originalContent = content
                val splitLines = content.split("\n").toMutableList()
                ignoreUndoCapture = true
                lineAdapter.fileExtension = file.extension  // MUST be set before notify
                lineAdapter.lines.clear()
                lineAdapter.lines.addAll(if (splitLines.isEmpty()) mutableListOf("") else splitLines)
                lineAdapter.notifyDataSetChanged()
                ignoreUndoCapture = false

                binding.tvEncoding.text = "UTF-8"
                binding.tvFileType.text = file.extension.uppercase().ifEmpty { "TXT" }
                setModified(false)
                binding.btnSave.isEnabled = !isReadOnly

                if (fileSize > 100 * 1024) {
                    withContext(Dispatchers.Main) { showLoadFullFileOption(fileSize) }
                }
            }.onFailure {
                Toast.makeText(this@FileEditorActivity, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showLoadFullFileOption(fileSize: Long) {
        val sizeMB = fileSize / (1024.0 * 1024.0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Load Full File?")
            .setMessage(String.format("Showing first 100KB. Load full %.1f MB?", sizeMB))
            .setPositiveButton("Load Full") { _, _ -> loadFullFile() }
            .setNegativeButton("Keep Partial", null)
            .show()
    }

    private fun loadFullFile() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { FileUtils.readText(file) }
            result.onSuccess { content ->
                originalContent = content
                val splitLines = content.split("\n").toMutableList()
                ignoreUndoCapture = true
                lineAdapter.lines.clear()
                lineAdapter.lines.addAll(splitLines)
                lineAdapter.notifyDataSetChanged()
                ignoreUndoCapture = false
                binding.tvEncoding.text = "UTF-8"
                Toast.makeText(this@FileEditorActivity, "Full file loaded", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveFile() {
        if (isReadOnly) return
        if (isLargeMode) { saveLargeFile(); return }
        val content = lineAdapter.getContent()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = FileUtils.writeText(file, content)
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    originalContent = content
                    setModified(false)
                    Toast.makeText(this@FileEditorActivity, getString(R.string.file_saved), Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this@FileEditorActivity, "${getString(R.string.file_save_error)}: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────────
    private fun captureUndoState() {
        if (ignoreUndoCapture) return
        undoStack.addLast(lineAdapter.lines.toList())
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(lineAdapter.lines.toList())
        val state = undoStack.removeLast()
        ignoreUndoCapture = true
        lineAdapter.lines.clear(); lineAdapter.lines.addAll(state)
        lineAdapter.notifyDataSetChanged()
        ignoreUndoCapture = false
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(lineAdapter.lines.toList())
        val state = redoStack.removeLast()
        ignoreUndoCapture = true
        lineAdapter.lines.clear(); lineAdapter.lines.addAll(state)
        lineAdapter.notifyDataSetChanged()
        ignoreUndoCapture = false
    }

    // ── Cursor / Status ───────────────────────────────────────────────────────
    fun updateCursorPosition(line: Int, col: Int) {
        binding.tvCursorPos.text = "Ln ${line + 1}, Col ${col + 1}"
    }

    private fun setModified(modified: Boolean) {
        isModified = modified
        binding.tvModified.visibility = if (modified) View.VISIBLE else View.GONE
    }

    // ── Editor menu ───────────────────────────────────────────────────────────
    private fun showEditorMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 1, "Increase Font Size")
        popup.menu.add(0, 2, 2, "Decrease Font Size")
        popup.menu.add(0, 3, 3, "Share File")
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                1 -> changeFontSize(+1f)
                2 -> changeFontSize(-1f)
                3 -> shareFile()
            }
            true
        }
        popup.show()
    }

    private fun changeFontSize(delta: Float) {
        fontSize = (fontSize + delta).coerceIn(8f, 32f)
        if (isLargeMode) {
            streamAdapter?.fontSize = fontSize
            streamAdapter?.notifyItemRangeChanged(0, streamAdapter?.itemCount ?: 0)
        } else {
            lineAdapter.fontSize = fontSize
            lineAdapter.notifyItemRangeChanged(0, lineAdapter.itemCount)
        }
    }

    private fun shareFile() {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share ${file.name}"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot share", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        if (isModified) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Unsaved Changes")
                .setMessage("Save changes to ${file.name} before leaving?")
                .setPositiveButton("Save") { _, _ -> saveFile(); finish() }
                .setNegativeButton("Discard") { _, _ -> finish() }
                .setNeutralButton(getString(R.string.cancel), null)
                .show()
        } else super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        largeEngine?.close()
    }
}
