package com.mtmanager.lite.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mtmanager.lite.R
import com.mtmanager.lite.databinding.ActivityMainBinding
import com.mtmanager.lite.model.SortOrder
import com.mtmanager.lite.ui.editor.FileEditorActivity
import com.mtmanager.lite.ui.pane.FileClipboard
import com.mtmanager.lite.ui.pane.FilePaneFragment
import com.mtmanager.lite.ui.pane.PaneCallback
import com.mtmanager.lite.ui.search.SearchActivity
import com.mtmanager.lite.ui.viewer.ImageViewerActivity
import com.mtmanager.lite.utils.PermissionHelper
import com.mtmanager.lite.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity(), PaneCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var leftPane: FilePaneFragment
    private lateinit var rightPane: FilePaneFragment
    private var activePane: FilePaneFragment? = null

    // Toggle state for glassmorphism toolbar
    private var isSwapToggled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // ⚡ Apply persisted theme BEFORE inflation so XML picks the right style
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Disable nested scrolling so touch events reach child click listeners reliably
        binding.rootScroll.isNestedScrollingEnabled = false

        if (!PermissionHelper.hasStoragePermission(this)) {
            showPermissionScreen()
        } else {
            initPanes()
            handleIntent(intent)
        }

        // Apply gradient to app title after layout is measured
        binding.root.post {
            applyTitleGradient()
            // ── Light theme only: animate UI elements on launch ──
            if (ThemeManager.isLightGlass(this)) {
                animateLightThemeEntrance()
            }
        }
    }

    /** Staggered entrance animations for Light Glass theme */
    private fun animateLightThemeEntrance() {
        val floatIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.lg_card_float_in)
        // Header bar slides down + fades
        binding.headerBar.alpha = 0f
        binding.headerBar.animate()
            .alpha(1f)
            .translationYBy(-20f).translationY(0f)
            .setDuration(350)
            .setStartDelay(50)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        // Quick Access section floats in with slight delay
        binding.quickAccessSection.startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.lg_card_float_in).also {
                it.startOffset = 120
            }
        )
        // Quick Access individual cards cascade in
        val cards = listOf(
            binding.btnQuickImages,
            binding.btnQuickVideos,
            binding.btnQuickDocs,
            binding.btnQuickDownloads
        )
        cards.forEachIndexed { i, card ->
            card.alpha = 0f
            card.animate()
                .alpha(1f)
                .scaleX(1f).scaleY(1f)
                .translationY(0f)
                .setDuration(320)
                .setStartDelay((200 + i * 70).toLong())
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        }
    }


    /**
     * Paints "XYvion" with a left-to-right gradient matching the logo:
     * electric blue → violet → hot pink
     */
    private fun applyTitleGradient() {
        val tv = findViewById<TextView>(R.id.tvAppTitle) ?: return
        tv.post {
            val w = tv.paint.measureText(tv.text.toString()).takeIf { it > 0f } ?: return@post
            // Gradient adapts to theme: same vivid colors look great on both
            tv.paint.shader = LinearGradient(
                0f, 0f, w, tv.textSize,
                intArrayOf(
                    0xFF4FC3F7.toInt(),   // sky blue
                    0xFF7C4DFF.toInt(),   // deep violet
                    0xFFE91E8C.toInt()    // vivid pink
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            tv.invalidate()
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasStoragePermission(this)) {
            if (!::leftPane.isInitialized) initPanes()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (PermissionHelper.hasStoragePermission(this) && ::leftPane.isInitialized) {
            handleIntent(intent)
        }
    }

    /**
     * Routes any ACTION_VIEW intent (from WhatsApp, Gmail, Files app, etc.)
     * directly to the correct built-in viewer based on MIME type.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return

        // Resolve MIME type: from intent first, then ContentResolver, then extension guess
        val mime: String = intent.type
            ?: contentResolver.getType(uri)
            ?: run {
                val name = uri.lastPathSegment ?: ""
                val ext  = name.substringAfterLast('.', "").lowercase()
                com.mtmanager.lite.model.FileItem.MIME_MAP[ext] ?: "*/*"
            }

        // Also resolve a File path if possible (for viewers that need it)
        val file: File? = when (uri.scheme) {
            "file" -> uri.path?.let { File(it) }
            "content" -> {
                val path = com.mtmanager.lite.utils.UriUtils.getPathFromUri(this, uri)
                if (path != null) File(path) else null
            }
            else -> null
        }

        when {
            // ── PDF
            mime == "application/pdf" -> startActivity(
                Intent(this, com.mtmanager.lite.ui.viewer.PdfViewerActivity::class.java)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))

            // ── Image
            mime.startsWith("image/") -> startActivity(
                Intent(this, com.mtmanager.lite.ui.viewer.ImageViewerActivity::class.java)
                    .also { i ->
                        if (file != null) i.putExtra(com.mtmanager.lite.ui.viewer.ImageViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                        else i.setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })

            // ── Video
            mime.startsWith("video/") -> startActivity(
                Intent(this, com.mtmanager.lite.ui.viewer.VideoPlayerActivity::class.java)
                    .also { i ->
                        if (file != null) i.putExtra(com.mtmanager.lite.ui.viewer.VideoPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
                        else i.setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })

            // ── Audio
            mime.startsWith("audio/") -> startActivity(
                Intent(this, com.mtmanager.lite.ui.viewer.AudioPlayerActivity::class.java)
                    .also { i ->
                        if (file != null) i.putExtra(com.mtmanager.lite.ui.viewer.AudioPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
                        else i.setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })

            // ── CSV / TSV
            mime in setOf("text/csv", "text/tab-separated-values", "text/comma-separated-values") ->
                startActivity(Intent(this, com.mtmanager.lite.ui.viewer.CsvViewerActivity::class.java)
                    .also { i ->
                        if (file != null) i.putExtra(com.mtmanager.lite.ui.viewer.CsvViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                        else i.setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })

            // ── Text / code → editor
            mime.startsWith("text/") && file != null -> startActivity(
                Intent(this, com.mtmanager.lite.ui.editor.FileEditorActivity::class.java)
                    .putExtra(com.mtmanager.lite.ui.editor.FileEditorActivity.EXTRA_FILE_PATH, file.absolutePath))

            // ── Fallback: locate in file browser OR open with system
            file != null -> {
                binding.root.postDelayed({
                    val pane = activePane ?: if (::leftPane.isInitialized) leftPane else return@postDelayed
                    if (pane.isAdded && pane.view != null) pane.locateAndHighlight(file)
                }, 600)
            }

            else -> Toast.makeText(this, "Cannot open: $mime", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionScreen() {
        binding.dualPaneContainer.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(com.mtmanager.lite.R.string.storage_permission_title))
            .setMessage(getString(com.mtmanager.lite.R.string.storage_permission_msg))
            .setPositiveButton(getString(com.mtmanager.lite.R.string.grant_permission)) { _, _ ->
                PermissionHelper.requestPermission(this)
            }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .create()
        dialog.show()
    }

    private fun initPanes() {
        // ── Step 1: Nuke any old fragment saved in rightPaneContainer ──────
        // When activity recreates (theme switch), FragmentManager restores
        // ALL previously-committed fragments. We must remove it synchronously
        // before doing anything else — or it will appear in the right column.
        supportFragmentManager.findFragmentById(R.id.rightPaneContainer)?.let { stale ->
            supportFragmentManager.beginTransaction()
                .remove(stale)
                .commitNow()           // synchronous — done before any layout pass
        }

        // ── Step 2: Collapse the right pane container to zero ──────────────
        binding.rightPaneContainer.visibility = View.GONE
        (binding.rightPaneContainer.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { lp ->
            lp.weight = 0f
            lp.width  = 0
            binding.rightPaneContainer.layoutParams = lp
        }

        binding.dualPaneContainer.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.VISIBLE

        leftPane = FilePaneFragment()
        rightPane = FilePaneFragment() // kept for swap-path compatibility, not attached to UI

        // Wire header buttons immediately — they use null-safe activePane at click time
        setupHeaderBar()
        // Wire Quick Access immediately — same null-safe pattern
        setupQuickAccess()

        // Defer fragment transaction to next frame to avoid blocking onCreate
        binding.root.post {
            supportFragmentManager.beginTransaction()
                .replace(binding.leftPaneContainer.id, leftPane)
                // ⚠️ rightPane is NOT committed — rightPaneContainer is GONE in both themes
                .commit()

            activePane = leftPane
            leftPane.isActive = true

            setupBottomBar()
        }
    }

    private fun setupBottomBar() {
        // Back — with active flash
        binding.btnNavBack.setOnClickListener {
            flashActive(binding.btnNavBack)
            val pane = activePane ?: return@setOnClickListener
            if (!pane.navigateBack()) Toast.makeText(this, "No history", Toast.LENGTH_SHORT).show()
        }
        binding.btnNavBack.setOnLongClickListener {
            Toast.makeText(this, "Go back", Toast.LENGTH_SHORT).show()
            true
        }

        // Forward — with active flash
        binding.btnNavForward.setOnClickListener {
            flashActive(binding.btnNavForward)
            val pane = activePane ?: return@setOnClickListener
            if (!pane.navigateForward()) Toast.makeText(this, "No forward history", Toast.LENGTH_SHORT).show()
        }
        binding.btnNavForward.setOnLongClickListener {
            Toast.makeText(this, "Go forward", Toast.LENGTH_SHORT).show()
            true
        }

        // Create / Add — ripple-like scale feedback
        binding.btnCreate.setOnClickListener { view ->
            view.animate()
                .scaleX(0.92f).scaleY(0.92f)
                .setDuration(80)
                .withEndAction {
                    view.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
                        .start()
                }
                .start()
            activePane?.showNewItemDialog()
        }
        binding.btnCreate.setOnLongClickListener {
            Toast.makeText(this, "Add new item", Toast.LENGTH_SHORT).show()
            true
        }

        // Folders section sort button
        binding.btnSort.setOnClickListener { activePane?.showSortDialog() }

        // Bottom bar Sort
        binding.btnSortBar.setOnClickListener { activePane?.showSortDialog() }
        binding.btnSortBar.setOnLongClickListener {
            Toast.makeText(this, "Sort items", Toast.LENGTH_SHORT).show()
            true
        }

        // Initialize sort UI to match the active pane
        activePane?.getCurrentSortOrder()?.let { updateSortUi(it) }

        // Swap — toggle visual state + action
        binding.btnSwap.setOnClickListener {
            try {
                isSwapToggled = !isSwapToggled
                binding.btnSwap.isSelected = isSwapToggled
                val leftPath = leftPane.currentPath
                val rightPath = rightPane.currentPath
                leftPane.navigateTo(rightPath, addToHistory = false)
                // rightPane is never attached to UI — use swapPath() to avoid view lifecycle crash
                rightPane.swapPath(leftPath)
            } catch (e: Exception) {
                Log.e("MainActivity", "Swap failed", e)
                Toast.makeText(this, "Swap failed: ${e.message}", Toast.LENGTH_SHORT).show()
                isSwapToggled = false
                binding.btnSwap.isSelected = false
            }
        }
        binding.btnSwap.setOnLongClickListener {
            Toast.makeText(this, "Swap panes", Toast.LENGTH_SHORT).show()
            true
        }

        binding.btnPaste.setOnClickListener { activePane?.pasteClipboard() }

        // ── FAB breathing glow animation ─────────────────────────────────────
        if (ThemeManager.isLightGlass(this)) {
            startFabBreathAnimation()
            startBadgePulse()
        }
    }

    /** Briefly flash a toolbar button to indicate active press (Back/Forward) */
    private fun flashActive(view: View) {
        view.isSelected = true
        view.postDelayed({ view.isSelected = false }, 250)
    }

    /** Pulse animation for the Sort notification badge */
    private fun startBadgePulse() {
        val badge = binding.sortBadge
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.18f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.18f, 1f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.85f, 1f)
        ObjectAnimator.ofPropertyValuesHolder(badge, scaleX, scaleY, alpha).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            startDelay = 400
            start()
        }
    }

    /** Breathing glow pulse: scale the glow ring up/down in a loop */
    private fun startFabBreathAnimation() {
        val glow = binding.fabGlowRing
        glow.animate()
            .scaleX(1.28f).scaleY(1.28f)
            .alpha(0.50f)
            .setDuration(1200)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction {
                glow.animate()
                    .scaleX(0.85f).scaleY(0.85f)
                    .alpha(0.15f)
                    .setDuration(1200)
                    .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                    .withEndAction { startFabBreathAnimation() }
                    .start()
            }
            .start()
    }

    /** Wires Quick Access category shortcuts to common system directories */
    private fun setupQuickAccess() {
        val extRoot = android.os.Environment.getExternalStorageDirectory()

        binding.btnQuickImages.setOnClickListener {
            val pane = activePane ?: run {
                Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            pane.navigateTo(if (dir.exists()) dir else extRoot)
        }
        binding.btnQuickVideos.setOnClickListener {
            val pane = activePane ?: run {
                Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
            pane.navigateTo(if (dir.exists()) dir else extRoot)
        }
        binding.btnQuickDocs.setOnClickListener {
            val pane = activePane ?: run {
                Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            pane.navigateTo(if (dir.exists()) dir else extRoot)
        }
        binding.btnQuickDownloads.setOnClickListener {
            val pane = activePane ?: run {
                Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            pane.navigateTo(if (dir.exists()) dir else extRoot)
        }
    }

    private fun setupHeaderBar() {
        // 🔍 Search
        binding.btnSearch.setOnClickListener {
            val currentPath = activePane?.currentPath?.absolutePath ?: Environment.getExternalStorageDirectory().absolutePath
            startActivity(Intent(this, SearchActivity::class.java).apply {
                putExtra(SearchActivity.EXTRA_START_PATH, currentPath)
            })
        }

        // ☰ Hamburger — main app menu
        binding.btnMenu.setOnClickListener {
            val isHidden = activePane?.isShowingHidden() == true
            showMenuSheet("Main Menu", listOf(
                SheetItem(R.drawable.ic_visibility,
                    if (isHidden) "Hide Hidden Files" else "Show Hidden Files",
                    0xFF22D3EE.toInt()) { activePane?.toggleHiddenFiles() },
                SheetItem(R.drawable.ic_sort, "Sort By", 0xFF4F8EF7.toInt()) {
                    activePane?.showSortDialog() },
                SheetItem(R.drawable.ic_swap, "Swap Panes", 0xFFFBBF24.toInt()) {
                    try {
                        val l = leftPane.currentPath; val r = rightPane.currentPath
                        leftPane.navigateTo(r, addToHistory = false)
                        rightPane.swapPath(l)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Menu swap failed", e)
                        Toast.makeText(this@MainActivity, "Swap failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                SheetItem(R.drawable.ic_refresh, "Refresh", 0xFF34D399.toInt()) {
                    activePane?.refresh() },
                SheetItem(R.drawable.ic_select_all, "Select All", 0xFFA78BFA.toInt()) {
                    try {
                        activePane?.let { p ->
                            if (!p.isFileAdapterMultiSelectMode()) p.enterMultiSelectMode()
                            p.selectAllFiles()
                        }
                    } catch (e: Exception) { /* fragment may not be ready yet */ }
                }
            ))
        }

        // ⋮ More options
        binding.btnMore.setOnClickListener {
            val statsVisible = activePane?.isStatsPanelVisible() == true
            showMenuSheet("More Options", listOf(
                SheetItem(R.drawable.ic_info,
                    if (statsVisible) "Hide Stats" else "Show Stats",
                    0xFF4F8EF7.toInt()) {
                    activePane?.toggleStatsPanel() },
                SheetItem(R.drawable.ic_paste, "Paste", 0xFF4F8EF7.toInt()) {
                    activePane?.pasteClipboard() },
                SheetItem(R.drawable.ic_edit, "New File / Folder", 0xFF34D399.toInt()) {
                    activePane?.showNewItemDialog() },
                SheetItem(R.drawable.ic_search, "Toggle Search Bar", 0xFF22D3EE.toInt()) {
                    activePane?.toggleSearchBar() },
                SheetItem(R.drawable.ic_home, "Go to Home", 0xFFFB923C.toInt()) {
                    activePane?.navigateTo(android.os.Environment.getExternalStorageDirectory()) }
            ))
        }
    }

    // ── Generic bottom sheet menu builder ────────────────────────────────────
    private data class SheetItem(
        val iconRes: Int,
        val label: String,
        val iconTint: Int,
        val action: () -> Unit
    )

    private fun showMenuSheet(title: String, items: List<SheetItem>) {
        val sheetStyle = if (ThemeManager.isLightGlass(this))
            R.style.LiquidGlass_BottomSheetDialog
        else
            R.style.FileMenuBottomSheet
        val sheet = BottomSheetDialog(this, sheetStyle)
        val root = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_generic_menu, null, false)
        root.findViewById<TextView>(R.id.tvSheetTitle).text = title
        val container = root.findViewById<LinearLayout>(R.id.menuItemsContainer)
        items.forEach { item ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_menu_action, container, false)
            row.findViewById<ImageView>(R.id.ivActionIcon).apply {
                setImageResource(item.iconRes)
                setColorFilter(item.iconTint)
            }
            row.findViewById<TextView>(R.id.tvActionLabel).text = item.label
            row.setOnClickListener { item.action(); sheet.dismiss() }
            container.addView(row)
        }
        sheet.setContentView(root)
        sheet.show()
    }

    // Helper — reads hidden state from pane
    private fun getHiddenState(pane: FilePaneFragment) = pane.isShowingHidden()

    /** Updates both header sort label and bottom-bar badge to reflect current sort */
    private fun updateSortUi(sortOrder: SortOrder) {
        binding.tvSortLabel.text = sortOrder.label
        if (sortOrder == SortOrder.NAME_ASC) {
            binding.sortBadge.visibility = View.GONE
        } else {
            binding.sortBadge.visibility = View.VISIBLE
            binding.sortBadge.text = ""   // dot indicator only — no confusing number
        }
    }

    // ─── PaneCallback ─────────────────────────────────────────────────────

    override fun onPaneActivated(pane: FilePaneFragment) {
        if (activePane == pane) return
        activePane?.isActive = false
        activePane = pane
        pane.isActive = true
    }

    override fun onSortChanged(sortOrder: SortOrder) {
        updateSortUi(sortOrder)
    }

    override fun onFileOpenRequest(file: File) {
        val item = com.mtmanager.lite.model.FileItem(file)

        when {
            // ── Text / code → built-in editor ────────────────────────────────
            item.isTextFile() -> {
                if (file.length() > 2 * 1024 * 1024) {
                    val sizeMB = file.length() / (1024.0 * 1024.0)
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Large File Warning")
                        .setMessage(String.format("File is %.1f MB. Opening may be slow. Continue?", sizeMB))
                        .setPositiveButton("Open Anyway") { _, _ -> openInEditor(file) }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    openInEditor(file)
                }
            }

            // ── Images → built-in image viewer ───────────────────────────────
            item.isImageFile() -> startActivity(
                Intent(this, ImageViewerActivity::class.java).apply {
                    putExtra(ImageViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                })

            // ── Video → built-in video player ────────────────────────────────
            item.isVideoFile() -> startActivity(
                Intent(this, com.mtmanager.lite.ui.viewer.VideoPlayerActivity::class.java).apply {
                    putExtra(com.mtmanager.lite.ui.viewer.VideoPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
                })

            // ── Audio → built-in audio player ────────────────────────────────
            item.isAudioFile() -> startActivity(
                Intent(this, com.mtmanager.lite.ui.viewer.AudioPlayerActivity::class.java).apply {
                    putExtra(com.mtmanager.lite.ui.viewer.AudioPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
                })

            // ── PDF → built-in PDF viewer ─────────────────────────────────────
            item.extension == "pdf" -> startActivity(
                Intent(this, com.mtmanager.lite.ui.viewer.PdfViewerActivity::class.java).apply {
                    putExtra(com.mtmanager.lite.ui.viewer.PdfViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                })

            // ── CSV / TSV → built-in spreadsheet viewer ───────────────────────
            item.extension in setOf("csv", "tsv") -> startActivity(
                Intent(this, com.mtmanager.lite.ui.viewer.CsvViewerActivity::class.java).apply {
                    putExtra(com.mtmanager.lite.ui.viewer.CsvViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                })

            // ── APK → built-in APK info then install prompt ───────────────────
            item.isApkFile() -> showApkInfoDialog(file)

            // ── XLSX / XLS → parse to CSV, open in CsvViewer (like CSV) ──────
            item.extension in setOf("xlsx", "xls", "xlsm", "xlsb", "ods", "numbers") ->
                openXlsxAsCsv(file)

            // ── DOCX / ODT → render in DocxViewer (like PDF) ───────────────
            item.extension in setOf("docx", "odt") ->
                startActivity(Intent(this,
                    com.mtmanager.lite.ui.viewer.DocxViewerActivity::class.java).apply {
                        putExtra(com.mtmanager.lite.ui.viewer.DocxViewerActivity.EXTRA_FILE_PATH,
                            file.absolutePath) })

            // ── PPTX / ODP → extract text, open in editor ────────────────
            item.extension in setOf("pptx", "odp") ->
                openOfficeDocAsText(file)

            // ── Unknown / binary → try to read as text, else show info ────────
            else -> {
                val mime = guessTextOrBinary(file)
                if (mime == "text/plain") openInEditor(file)
                else showUnknownFileDialog(file)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun openInEditor(file: File) {
        startActivity(Intent(this, FileEditorActivity::class.java).apply {
            putExtra(FileEditorActivity.EXTRA_FILE_PATH, file.absolutePath)
        })
    }

    /** Parse xlsx → temp CSV → open in CsvViewerActivity */
    private fun openXlsxAsCsv(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            val csv = com.mtmanager.lite.utils.XlsxParser.toCsv(file)
            if (csv.isBlank()) {
                withContext(Dispatchers.Main) { showOfficeOpenDialog(file) }
                return@launch
            }
            val tmp = java.io.File(cacheDir, "${file.nameWithoutExtension}.csv")
            tmp.writeText(csv)
            withContext(Dispatchers.Main) {
                startActivity(Intent(this@MainActivity,
                    com.mtmanager.lite.ui.viewer.CsvViewerActivity::class.java).apply {
                        putExtra(com.mtmanager.lite.ui.viewer.CsvViewerActivity.EXTRA_FILE_PATH,
                            tmp.absolutePath) })
            }
        }
    }

    /**
     * DOCX / PPTX: ZIP archives of XML.
     * Paragraph-aware extraction: replaces <w:p> / </w:p> with newlines
     * BEFORE stripping all other tags, so words stay on the same line.
     */
    private fun openOfficeDocAsText(file: File) {
        val ext = file.extension.lowercase()

        val targets: List<String> = when (ext) {
            "docx", "odt" -> listOf("word/document.xml", "content.xml")
            "pptx", "odp" -> listOf(
                "ppt/slides/slide1.xml", "ppt/slides/slide2.xml",
                "ppt/slides/slide3.xml", "content.xml")
            else           -> listOf("word/document.xml", "content.xml")
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val text = runCatching {
                val sb = StringBuilder()
                java.util.zip.ZipInputStream(file.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && targets.any { name.endsWith(it) }) {
                            val raw = zis.bufferedReader(Charsets.UTF_8).readText()
                            val cleaned = raw
                                // ── Paragraph / line breaks FIRST ──
                                .replace(Regex("</w:p>"), "\n")       // docx paragraphs
                                .replace(Regex("</a:p>"), "\n")       // pptx paragraphs
                                .replace(Regex("</text:p>"), "\n")    // odt paragraphs
                                .replace(Regex("<w:br[^>]*/>"), "\n") // line breaks
                                .replace(Regex("<w:tab[^>]*/>"), "\t")// tabs
                                // ── Strip ALL remaining tags ──
                                .replace(Regex("<[^>]+>"), "")
                                // ── Decode XML entities ──
                                .replace("&amp;",  "&")
                                .replace("&lt;",   "<")
                                .replace("&gt;",   ">")
                                .replace("&quot;", "\"")
                                .replace("&apos;", "'")
                                .replace("&nbsp;", " ")
                                // ── Collapse whitespace (spaces only, not newlines) ──
                                .replace(Regex("[ \t]{2,}"), " ")
                                .replace(Regex("\n{3,}"), "\n\n")
                                .trim()
                            if (cleaned.isNotBlank()) {
                                if (sb.isNotEmpty()) sb.append("\n\n")
                                sb.append(cleaned)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                sb.toString().trim()
            }.getOrElse { "Could not extract text from ${file.name}:\n${it.message}" }

            withContext(Dispatchers.Main) {
                if (text.isBlank()) {
                    showOfficeOpenDialog(file)
                } else {
                    val tmp = java.io.File(cacheDir, "${file.nameWithoutExtension}_preview.txt")
                    tmp.writeText(text)
                    openInEditor(tmp)
                }
            }
        }
    }

    /** Fallback: let the user pick between an external Office app or raw binary open */
    private fun showOfficeOpenDialog(file: File) {
        val mime = com.mtmanager.lite.model.FileItem(file).mimeType()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Open ${file.name}")
            .setMessage("No readable text was extracted.\nOpen with an external app (Word, Sheets, WPS, etc.)?")
            .setPositiveButton("Open Externally") { _, _ ->
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this, "$packageName.provider", file)
                    startActivity(Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    // No app installed — open chooser with */*
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            this, "$packageName.provider", file)
                        startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "*/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            "Open with…"))
                    } catch (ex: Exception) {
                        Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Shows APK package info with an option to install. */
    private fun showApkInfoDialog(file: File) {
        val pm = packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
        val msg = if (info != null) {
            "Package: ${info.packageName}\nVersion: ${info.versionName}\nSize: ${
                android.text.format.Formatter.formatShortFileSize(this, file.length())}"
        } else {
            "Could not read APK info.\nSize: ${android.text.format.Formatter.formatShortFileSize(this, file.length())}"
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("📦 ${file.name}")
            .setMessage(msg)
            .setPositiveButton("Install") { _, _ ->
                // Use FileProvider for safe URI sharing
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", file)
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot install: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Last resort for truly unknown/binary files. */
    private fun showUnknownFileDialog(file: File) {
        val item = com.mtmanager.lite.model.FileItem(file)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Cannot Open")
            .setMessage("\"${file.name}\" is a binary file (${item.formattedSize()}).\n" +
                "XYvion cannot display this file type.\n\nOpen as text anyway?")
            .setPositiveButton("Open as Text") { _, _ -> openInEditor(file) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Reads the first 512 bytes to guess if a file is readable text.
     */
    private fun guessTextOrBinary(file: File): String {
        return try {
            val bytes = file.inputStream().use { it.readNBytes(512) }
            val nonPrintable = bytes.count { b ->
                b < 0x09.toByte() || (b > 0x0D.toByte() && b < 0x20.toByte())
            }
            if (nonPrintable < bytes.size * 0.05) "text/plain" else "*/*"
        } catch (e: Exception) { "*/*" }
    }

    override fun getOtherPane(current: FilePaneFragment): FilePaneFragment? =
        if (current == leftPane) rightPane else leftPane

    override fun onClipboardChanged() {
        binding.btnPaste.visibility = if (FileClipboard.hasData()) View.VISIBLE else View.GONE
    }

    override fun onBackPressed() {
        val pane = activePane ?: run { super.onBackPressed(); return }
        if (!pane.navigateBack()) super.onBackPressed()
    }

    /** Keyboard: ESC clears toggle state and resets sort to default */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {
            isSwapToggled = false
            binding.btnSwap.isSelected = false
            activePane?.let { pane ->
                pane.setSortOrder(SortOrder.NAME_ASC)
                updateSortUi(SortOrder.NAME_ASC)
            }
            Toast.makeText(this, "All states cleared", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
