package com.fyloxen.app.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.fyloxen.app.R
import com.fyloxen.app.databinding.ActivityMainBinding
import com.fyloxen.app.model.SortOrder
import com.fyloxen.app.ui.editor.FileEditorActivity
import com.fyloxen.app.ui.pane.FileClipboard
import com.fyloxen.app.ui.pane.FilePaneFragment
import com.fyloxen.app.ui.pane.PaneCallback
import com.fyloxen.app.ui.search.SearchActivity
import com.fyloxen.app.ui.viewer.ImageViewerActivity
import com.fyloxen.app.utils.PermissionHelper
import com.fyloxen.app.utils.ThemeManager
import com.fyloxen.app.utils.AnalyticsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity(), PaneCallback {

    companion object {
        private const val REQUEST_SEARCH = 1001
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var leftPane: FilePaneFragment
    private lateinit var rightPane: FilePaneFragment
    private var activePane: FilePaneFragment? = null

    // (swap feature removed)

    override fun onCreate(savedInstanceState: Bundle?) {
        // ⚡ Apply persisted theme BEFORE inflation so XML picks the right style
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Analytics: init once, track app open ──────────────────────────────
        AnalyticsManager.init(this)
        AnalyticsManager.trackAppOpen()

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
        // Header bar slides down + fades — faster start delay
        binding.headerBar.alpha = 0f
        binding.headerBar.animate()
            .alpha(1f)
            .translationYBy(-12f).translationY(0f)
            .setDuration(220)
            .setStartDelay(0)   // instant — no perceived lag
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        // Quick Access section
        binding.quickAccessSection.alpha = 0f
        binding.quickAccessSection.animate()
            .alpha(1f)
            .setDuration(200)
            .setStartDelay(60)
            .start()
        // Quick Access cards cascade in
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
                .setDuration(200)
                .setStartDelay((80 + i * 40).toLong())   // was 200+70*i ms — now half
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        }
    }


    /**
     * Paints "FYLOXEN" in #121212 with a very subtle depth gradient
     */
    private fun applyTitleGradient() {
        val tv = findViewById<TextView>(R.id.tvAppTitle) ?: return
        tv.post {
            val w = tv.paint.measureText(tv.text.toString()).takeIf { it > 0f } ?: return@post
            tv.paint.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(
                    0xFF121212.toInt(),   // base
                    0xFF1E1E1E.toInt(),   // subtle highlight
                    0xFF121212.toInt()    // base
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SEARCH && resultCode == RESULT_OK) {
            val dirPath = data?.getStringExtra(SearchActivity.EXTRA_OPEN_DIR) ?: return
            val dir = File(dirPath)
            if (dir.isDirectory && dir.canRead()) {
                (activePane ?: leftPane).navigateTo(dir)
            }
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
                com.fyloxen.app.model.FileItem.MIME_MAP[ext] ?: "*/*"
            }

        // Also resolve a File path if possible (for viewers that need it)
        val file: File? = when (uri.scheme) {
            "file" -> uri.path?.let { File(it) }
            "content" -> {
                val path = com.fyloxen.app.utils.UriUtils.getPathFromUri(this, uri)
                if (path != null) File(path) else null
            }
            else -> null
        }

        when {
            // ── PDF
            mime == "application/pdf" -> {
                startActivity(
                    Intent(this, com.fyloxen.app.ui.viewer.PdfViewerActivity::class.java)
                        .setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
            }

            // ── Image
            mime.startsWith("image/") -> {
                startActivity(
                    Intent(this, com.fyloxen.app.ui.viewer.ImageViewerActivity::class.java)
                        .also { i ->
                            if (file != null) i.putExtra(com.fyloxen.app.ui.viewer.ImageViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                            else i.setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
            }

            // ── Video
            mime.startsWith("video/") -> {
                startActivity(
                    Intent(this, com.fyloxen.app.ui.viewer.VideoPlayerActivity::class.java)
                        .also { i ->
                            if (file != null) i.putExtra(com.fyloxen.app.ui.viewer.VideoPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
                            else i.setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
            }

            // ── Audio
            mime.startsWith("audio/") -> {
                startActivity(
                    Intent(this, com.fyloxen.app.ui.viewer.AudioPlayerActivity::class.java)
                        .also { i ->
                            if (file != null) i.putExtra(com.fyloxen.app.ui.viewer.AudioPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
                            else i.setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
            }

            // ── CSV / TSV
            mime in setOf("text/csv", "text/tab-separated-values", "text/comma-separated-values") -> {
                startActivity(Intent(this, com.fyloxen.app.ui.viewer.CsvViewerActivity::class.java)
                    .also { i ->
                        if (file != null) i.putExtra(com.fyloxen.app.ui.viewer.CsvViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                        else i.setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
            }

            // ── Text / code → editor
            mime.startsWith("text/") && file != null -> {
                startActivity(
                    Intent(this, com.fyloxen.app.ui.editor.FileEditorActivity::class.java)
                        .putExtra(com.fyloxen.app.ui.editor.FileEditorActivity.EXTRA_FILE_PATH, file.absolutePath))
                overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
            }

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
            .setTitle(getString(com.fyloxen.app.R.string.storage_permission_title))
            .setMessage(getString(com.fyloxen.app.R.string.storage_permission_msg))
            .setPositiveButton(getString(com.fyloxen.app.R.string.grant_permission)) { _, _ ->
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
            if (!isDestroyed && !isFinishing) {
                supportFragmentManager.beginTransaction()
                    .replace(binding.leftPaneContainer.id, leftPane)
                    // ⚠️ rightPane is NOT committed — rightPaneContainer is GONE in both themes
                    .commitAllowingStateLoss()

                activePane = leftPane
                leftPane.isActive = true

                setupBottomBar()
            }
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

        // Home — one-tap jump to root storage from anywhere in the directory tree
        binding.btnHome.setOnClickListener {
            val home = android.os.Environment.getExternalStorageDirectory()
            val pane = activePane
            if (pane == null) {
                Toast.makeText(this, "Loading…", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pane.currentPath == home) {
                // Already at home: scroll list to top
                Toast.makeText(this, "Already at Home", Toast.LENGTH_SHORT).show()
            } else {
                pane.navigateTo(home)
            }
        }
        binding.btnHome.setOnLongClickListener {
            Toast.makeText(this, "Go to Home (root storage)", Toast.LENGTH_SHORT).show()
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
            AnalyticsManager.trackFeature("quick_access_images", screen = "MainActivity")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            pane.navigateTo(if (dir.exists()) dir else extRoot)
        }
        binding.btnQuickVideos.setOnClickListener {
            val pane = activePane ?: run {
                Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AnalyticsManager.trackFeature("quick_access_videos", screen = "MainActivity")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
            pane.navigateTo(if (dir.exists()) dir else extRoot)
        }
        binding.btnQuickDocs.setOnClickListener {
            val pane = activePane ?: run {
                Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AnalyticsManager.trackFeature("quick_access_docs", screen = "MainActivity")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            pane.navigateTo(if (dir.exists()) dir else extRoot)
        }
        binding.btnQuickDownloads.setOnClickListener {
            val pane = activePane ?: run {
                Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AnalyticsManager.trackFeature("quick_access_downloads", screen = "MainActivity")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            pane.navigateTo(if (dir.exists()) dir else extRoot)
        }
    }

    private fun setupHeaderBar() {
        // 🔍 Search
        binding.btnSearch.setOnClickListener {
            AnalyticsManager.trackFeature("search_opened", screen = "MainActivity")
            val currentPath = activePane?.currentPath?.absolutePath ?: Environment.getExternalStorageDirectory().absolutePath
            startActivityForResult(Intent(this, SearchActivity::class.java).apply {
                putExtra(SearchActivity.EXTRA_START_PATH, currentPath)
            }, REQUEST_SEARCH)
        }

        // ☰ Hamburger — main app menu (sectioned)
        binding.btnMenu.setOnClickListener {
            val isHidden = activePane?.isShowingHidden() == true
            showMenuSheet("Menu", "Quick actions and settings", listOf(
                SheetSection("File Options", listOf(
                    SheetItem(R.drawable.ic_visibility,
                        if (isHidden) "Hide Hidden Files" else "Show Hidden Files",
                        if (isHidden) "Hide files that start with a dot" else "View files that are hidden",
                        0xFF8B7EC8.toInt()) {
                        AnalyticsManager.trackFeature("toggle_hidden_files", screen = "MainActivity")
                        activePane?.toggleHiddenFiles() },
                    SheetItem(R.drawable.ic_sort, "Sort By", "Sort files and folders", 0xFF8B7EC8.toInt()) {
                        AnalyticsManager.trackFeature("sort_opened", screen = "MainActivity")
                        activePane?.showSortDialog() },
                    SheetItem(R.drawable.ic_edit, "New File / Folder", "Create new file or folder", 0xFF34D399.toInt()) {
                        AnalyticsManager.trackFeature("create_item", screen = "MainActivity")
                        activePane?.showNewItemDialog() }
                )),
                SheetSection("Actions", listOf(
                    SheetItem(R.drawable.ic_refresh, "Refresh", "Reload current folder", 0xFFF9A03F.toInt()) {
                        AnalyticsManager.trackFeature("refresh", screen = "MainActivity")
                        activePane?.refresh() },
                    SheetItem(R.drawable.ic_select_all, "Select All", "Select all items in folder", 0xFF8B7EC8.toInt()) {
                        AnalyticsManager.trackFeature("select_all", screen = "MainActivity")
                        try {
                            activePane?.let { p ->
                                if (!p.isFileAdapterMultiSelectMode()) p.enterMultiSelectMode()
                                p.selectAllFiles()
                            }
                        } catch (e: Exception) { /* fragment may not be ready yet */ }
                    }
                ))
            ))
        }

        // ⋮ More options (sectioned)
        binding.btnMore.setOnClickListener {
            val statsVisible = activePane?.isStatsPanelVisible() == true
            showMenuSheet("More Options", "Additional tools and preferences", listOf(
                SheetSection("Utilities", listOf(
                    SheetItem(R.drawable.ic_info,
                        if (statsVisible) "Hide Stats" else "Show Stats",
                        "View storage and item details",
                        0xFF8B7EC8.toInt()) {
                        AnalyticsManager.trackFeature("toggle_stats", screen = "MainActivity")
                        activePane?.toggleStatsPanel() },
                    SheetItem(R.drawable.ic_paste, "Paste", "Paste copied items here", 0xFF8B7EC8.toInt()) {
                        AnalyticsManager.trackFeature("paste", screen = "MainActivity")
                        activePane?.pasteClipboard() },
                    SheetItem(R.drawable.ic_edit, "New File / Folder", "Create new file or folder", 0xFF34D399.toInt()) {
                        AnalyticsManager.trackFeature("create_item", screen = "MainActivity")
                        activePane?.showNewItemDialog() }
                )),
                SheetSection("Navigation", listOf(
                    SheetItem(R.drawable.ic_search, "Toggle Search Bar", "Show or hide search bar", 0xFF22D3EE.toInt()) {
                        AnalyticsManager.trackFeature("toggle_search_bar", screen = "MainActivity")
                        activePane?.toggleSearchBar() },
                    SheetItem(R.drawable.ic_home, "Go to Home", "Navigate to home screen", 0xFFF9A03F.toInt()) {
                        AnalyticsManager.trackFeature("go_home", screen = "MainActivity")
                        activePane?.navigateTo(android.os.Environment.getExternalStorageDirectory()) },
                    SheetItem(R.drawable.ic_star, "About Fyloxen", "Know more about the app", 0xFFC8A45E.toInt()) {
                        AnalyticsManager.trackFeature("about_opened", screen = "MainActivity")
                        showAboutSheet() }
                ))
            ))
        }
    }

    // ── Premium bottom sheet menu builder ────────────────────────────────────
    private data class SheetItem(
        val iconRes: Int,
        val label: String,
        val subtitle: String,
        val iconTint: Int,
        val action: () -> Unit
    )

    private data class SheetSection(
        val title: String,
        val items: List<SheetItem>
    )

    private fun showMenuSheet(title: String, items: List<SheetItem>) {
        showMenuSheet(title, "", listOf(SheetSection("", items)))
    }

    private fun showMenuSheet(title: String, subtitle: String, sections: List<SheetSection>) {
        val isLight = ThemeManager.isLightGlass(this)
        val sheetStyle = if (isLight) R.style.LiquidGlass_BottomSheetDialog else R.style.FileMenuBottomSheet
        val sheet = BottomSheetDialog(this, sheetStyle)
        val root = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_premium_menu, null, false)

        root.findViewById<TextView>(R.id.tvSheetTitle).text = title
        root.findViewById<TextView>(R.id.tvSheetSubtitle).text = subtitle
        root.findViewById<ImageButton>(R.id.btnSheetClose).setOnClickListener { sheet.dismiss() }

        val divider = root.findViewById<View>(R.id.dividerBetweenSections)

        if (isLight && sections.size >= 2) {
            root.findViewById<LinearLayout>(R.id.menuItemsContainer).visibility = View.GONE

            val section1 = root.findViewById<LinearLayout>(R.id.sectionFileOptions)
            val section2 = root.findViewById<LinearLayout>(R.id.sectionActions)
            val section3 = root.findViewById<LinearLayout>(R.id.sectionUtilities)
            val section4 = root.findViewById<LinearLayout>(R.id.sectionNavigation)

            val container1 = root.findViewById<LinearLayout>(R.id.menuSectionFileOptions)
            val container2 = root.findViewById<LinearLayout>(R.id.menuSectionActions)
            val container3 = root.findViewById<LinearLayout>(R.id.menuSectionUtilities)
            val container4 = root.findViewById<LinearLayout>(R.id.menuSectionNavigation)

            sections.getOrNull(0)?.let { sec ->
                if (sec.items.isNotEmpty()) {
                    section1.visibility = View.VISIBLE
                    sec.items.forEach { item -> addMenuItemRow(container1, item, sheet) }
                }
            }
            sections.getOrNull(1)?.let { sec ->
                if (sec.items.isNotEmpty()) {
                    section2.visibility = View.VISIBLE
                    divider.visibility = View.VISIBLE
                    sec.items.forEach { item -> addMenuItemRow(container2, item, sheet) }
                }
            }
            sections.getOrNull(2)?.let { sec ->
                if (sec.items.isNotEmpty()) {
                    section3.visibility = View.VISIBLE
                    sec.items.forEach { item -> addMenuItemRow(container3, item, sheet) }
                }
            }
            sections.getOrNull(3)?.let { sec ->
                if (sec.items.isNotEmpty()) {
                    section4.visibility = View.VISIBLE
                    sec.items.forEach { item -> addMenuItemRow(container4, item, sheet) }
                }
            }
        } else {
            root.findViewById<LinearLayout>(R.id.menuItemsContainer).visibility = View.VISIBLE
            val container = root.findViewById<LinearLayout>(R.id.menuItemsContainer)
            sections.flatMap { it.items }.forEach { item ->
                addMenuItemRow(container, item, sheet)
            }
        }

        sheet.setContentView(root)
        sheet.show()
    }

    private fun addMenuItemRow(container: LinearLayout, item: SheetItem, sheet: BottomSheetDialog) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_menu_action_premium, container, false)
        row.findViewById<ImageView>(R.id.ivActionIcon).apply {
            setImageResource(item.iconRes)
            setColorFilter(item.iconTint)
        }
        row.findViewById<TextView>(R.id.tvActionLabel).text = item.label
        row.findViewById<TextView>(R.id.tvActionSubtitle).text = item.subtitle
        row.setOnClickListener { item.action(); sheet.dismiss() }
        container.addView(row)
    }

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

    // ── About / Rate / Share / Legal ──────────────────────────────────────────

    /** Premium About sheet — Rate, Share, Privacy Policy, Terms & Conditions */
    private fun showAboutSheet() {
        val sheetStyle = if (ThemeManager.isLightGlass(this))
            R.style.LiquidGlass_BottomSheetDialog
        else
            R.style.FileMenuBottomSheet
        val sheet = BottomSheetDialog(this, sheetStyle)
        val root = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_about, null, false)

        // Version name from PackageManager
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "1.0" }
        root.findViewById<TextView>(R.id.tvAboutVersion).text = "Version $versionName"

        root.findViewById<LinearLayout>(R.id.rowRateApp).setOnClickListener {
            AnalyticsManager.trackFeature("rate_app", screen = "About")
            rateApp()
            sheet.dismiss()
        }
        root.findViewById<LinearLayout>(R.id.rowShareApp).setOnClickListener {
            AnalyticsManager.trackFeature("share_app", screen = "About")
            shareApp()
            sheet.dismiss()
        }
        root.findViewById<LinearLayout>(R.id.rowPrivacyPolicy).setOnClickListener {
            AnalyticsManager.trackFeature("privacy_policy", screen = "About")
            sheet.dismiss()
            showLegalSheet(isPrivacy = true)
        }
        root.findViewById<LinearLayout>(R.id.rowTerms).setOnClickListener {
            AnalyticsManager.trackFeature("terms_conditions", screen = "About")
            sheet.dismiss()
            showLegalSheet(isPrivacy = false)
        }

        sheet.setContentView(root)
        sheet.show()
    }

    /** Opens the Google Play Store listing for this app (Rate the App) */
    private fun rateApp() {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or
                      Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                      Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            startActivity(marketIntent)
        } catch (e: ActivityNotFoundException) {
            // Play Store not installed — open in browser
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }

    /** Android share sheet to invite others to install Fyloxen */
    private fun shareApp() {
        val text = "Check out Fyloxen — a fast, clean file manager for Android!\n" +
                   "https://play.google.com/store/apps/details?id=$packageName"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Fyloxen File Manager")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share Fyloxen via"))
    }

    /**
     * Scrollable legal bottom sheet.
     * [isPrivacy] = true → Privacy Policy, false → Terms & Conditions.
     * Content is written to match the app's actual behaviour — no false claims.
     */
    private fun showLegalSheet(isPrivacy: Boolean) {
        val sheetStyle = if (ThemeManager.isLightGlass(this))
            R.style.LiquidGlass_BottomSheetDialog
        else
            R.style.FileMenuBottomSheet
        val sheet = BottomSheetDialog(this, sheetStyle)
        val root = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_legal, null, false)

        root.findViewById<TextView>(R.id.tvLegalTitle).text =
            if (isPrivacy) "Privacy Policy" else "Terms & Conditions"

        root.findViewById<TextView>(R.id.tvLegalContent).text =
            if (isPrivacy) buildPrivacyPolicy() else buildTermsAndConditions()

        sheet.setContentView(root)
        sheet.show()
    }

    private fun buildPrivacyPolicy(): String = """
PRIVACY POLICY
Last updated: January 1, 2026

Fyloxen ("we", "our", "the app") is a local file manager developed for Android. This policy explains what data we collect and how we use it.

──────────────────────────
1. DATA WE COLLECT
──────────────────────────
We collect only anonymous usage analytics:
  • A randomly generated device identifier (not linked to your name, account, or any personal information)
  • App version number
  • Android OS version
  • Feature usage events (e.g., "folder opened", "file shared")
  • Crash reports with stack traces

We do NOT collect, read, transmit, or store:
  • Any of your files, photos, documents, or media
  • Your name, email, phone number, or any personal identity
  • Location data
  • Contact lists or call logs

──────────────────────────
2. HOW WE USE THIS DATA
──────────────────────────
Anonymous analytics are used exclusively to:
  • Fix crashes and bugs
  • Understand which features are most useful
  • Improve app performance and stability

We never sell, rent, share, or monetise your data.

──────────────────────────
3. FILE & STORAGE ACCESS
──────────────────────────
Fyloxen requires the MANAGE_EXTERNAL_STORAGE permission to browse and manage files on your device. This permission is used only for the file management features you interact with. We never scan your files in the background or transmit file contents anywhere.

──────────────────────────
4. DATA SECURITY
──────────────────────────
Analytics data is transmitted over HTTPS using API key authentication. Data is stored on secure servers with restricted access. You may request deletion of your anonymous analytics data by emailing samantas6085@gmail.com.

──────────────────────────
5. THIRD-PARTY LIBRARIES
──────────────────────────
Fyloxen does not integrate any third-party advertising or tracking SDKs. No data is shared with advertising networks.

──────────────────────────
6. CHILDREN'S PRIVACY
──────────────────────────
Fyloxen is not directed at children under the age of 13. We do not knowingly collect data from children.

──────────────────────────
7. CHANGES TO THIS POLICY
──────────────────────────
We may update this Privacy Policy from time to time. Changes will be reflected in the updated date at the top of this document. Continued use of the app constitutes your acceptance of the updated policy.

──────────────────────────
8. CONTACT US
──────────────────────────
For any privacy-related questions:
  Email: samantas6085@gmail.com

© 2026 Fyloxen. All rights reserved.
""".trimIndent()

    private fun buildTermsAndConditions(): String = """
TERMS AND CONDITIONS
Last updated: January 1, 2026

Please read these Terms and Conditions carefully before using Fyloxen ("the app", "we", "our").

──────────────────────────
1. ACCEPTANCE OF TERMS
──────────────────────────
By downloading, installing, or using Fyloxen, you agree to be bound by these Terms. If you do not agree, please uninstall the app.

──────────────────────────
2. LICENSE TO USE
──────────────────────────
We grant you a personal, non-exclusive, non-transferable, revocable licence to use Fyloxen on your Android device strictly in accordance with these Terms. You may not:
  • Copy, modify, or distribute the app
  • Reverse engineer or decompile the app
  • Use the app to build a competing product
  • Remove or alter any copyright or trademark notices

──────────────────────────
3. PERMITTED USE
──────────────────────────
You may use Fyloxen to organise, manage, browse, edit, and view files on your personal Android device. You agree not to use the app for any unlawful purpose or in any way that violates these Terms.

──────────────────────────
4. STORAGE PERMISSION
──────────────────────────
Fyloxen requires device storage access to function. You are responsible for managing what files are on your device. We are not responsible for accidental deletion or modification of your files.

──────────────────────────
5. DISCLAIMER OF WARRANTIES
──────────────────────────
Fyloxen is provided "as is" without any warranty of any kind, express or implied. We do not guarantee that the app will be error-free, uninterrupted, or free from viruses. You use the app at your own risk.

──────────────────────────
6. LIMITATION OF LIABILITY
──────────────────────────
To the fullest extent permitted by applicable law, Fyloxen and its developers shall not be liable for any indirect, incidental, special, consequential, or punitive damages arising from your use of, or inability to use, the app — including but not limited to loss of data or files.

──────────────────────────
7. INTELLECTUAL PROPERTY
──────────────────────────
All content, design, code, and trademarks in Fyloxen are the property of the Fyloxen development team and are protected by applicable intellectual property laws.

──────────────────────────
8. TERMINATION
──────────────────────────
We reserve the right to terminate or suspend your access to the app at any time, without notice, for conduct that we believe violates these Terms or is harmful to other users, us, or third parties.

──────────────────────────
9. CHANGES TO THESE TERMS
──────────────────────────
We may update these Terms at any time. Continued use of the app after changes are posted constitutes your acceptance of the revised Terms.

──────────────────────────
10. CONTACT US
──────────────────────────
For any questions regarding these Terms:
  Email: samantas6085@gmail.com

© 2026 Fyloxen. All rights reserved.
""".trimIndent()



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
        val item = com.fyloxen.app.model.FileItem(file)

        when {
            // ── Text / code → built-in editor ────────────────────────────────
            item.isTextFile() -> {
                if (file.length() > 2 * 1024 * 1024) {
                    val sizeMB = file.length() / (1024.0 * 1024.0)
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Large File Warning")
                        .setMessage(String.format("File is %.1f MB. Opening may be slow. Continue?", sizeMB))
                        .setPositiveButton("Open Anyway") { _, _ ->
                            AnalyticsManager.trackFeature("open_text_large", screen = "FilePane", extra = file.extension)
                            openInEditor(file)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    AnalyticsManager.trackFeature("open_text", screen = "FilePane", extra = file.extension)
                    openInEditor(file)
                }
            }

            // ── Images → built-in image viewer ───────────────────────────────
            item.isImageFile() -> {
                AnalyticsManager.trackFeature("open_image", screen = "FilePane", extra = file.extension)
                startActivity(Intent(this, ImageViewerActivity::class.java).apply {
                    putExtra(ImageViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                })
            }

            // ── Video → built-in video player ────────────────────────────────
            item.isVideoFile() -> {
                AnalyticsManager.trackFeature("open_video", screen = "FilePane", extra = file.extension)
                startActivity(Intent(this, com.fyloxen.app.ui.viewer.VideoPlayerActivity::class.java).apply {
                    putExtra(com.fyloxen.app.ui.viewer.VideoPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
                })
            }

            // ── Audio → built-in audio player ────────────────────────────────
            item.isAudioFile() -> {
                AnalyticsManager.trackFeature("open_audio", screen = "FilePane", extra = file.extension)
                startActivity(Intent(this, com.fyloxen.app.ui.viewer.AudioPlayerActivity::class.java).apply {
                    putExtra(com.fyloxen.app.ui.viewer.AudioPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
                })
            }

            // ── PDF → built-in PDF viewer ─────────────────────────────────────
            item.extension == "pdf" -> {
                AnalyticsManager.trackFeature("open_pdf", screen = "FilePane")
                startActivity(Intent(this, com.fyloxen.app.ui.viewer.PdfViewerActivity::class.java).apply {
                    putExtra(com.fyloxen.app.ui.viewer.PdfViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                })
            }

            // ── CSV / TSV → built-in spreadsheet viewer ───────────────────────
            item.extension in setOf("csv", "tsv") -> {
                AnalyticsManager.trackFeature("open_csv", screen = "FilePane", extra = file.extension)
                startActivity(Intent(this, com.fyloxen.app.ui.viewer.CsvViewerActivity::class.java).apply {
                    putExtra(com.fyloxen.app.ui.viewer.CsvViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                })
            }

            // ── APK → built-in APK info then install prompt ───────────────────
            item.isApkFile() -> {
                AnalyticsManager.trackFeature("open_apk", screen = "FilePane")
                showApkInfoDialog(file)
            }

            // ── XLSX / XLS → parse to CSV, open in CsvViewer (like CSV) ──────
            item.extension in setOf("xlsx", "xls", "xlsm", "xlsb", "ods", "numbers") -> {
                AnalyticsManager.trackFeature("open_spreadsheet", screen = "FilePane", extra = file.extension)
                openXlsxAsCsv(file)
            }

            // ── DOCX / ODT → render in DocxViewer (like PDF) ───────────────
            item.extension in setOf("docx", "odt") -> {
                AnalyticsManager.trackFeature("open_docx", screen = "FilePane", extra = file.extension)
                startActivity(Intent(this, com.fyloxen.app.ui.viewer.DocxViewerActivity::class.java).apply {
                    putExtra(com.fyloxen.app.ui.viewer.DocxViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                })
            }

            // ── PPTX / ODP → extract text, open in editor ────────────────
            item.extension in setOf("pptx", "odp") -> {
                AnalyticsManager.trackFeature("open_pptx", screen = "FilePane", extra = file.extension)
                openOfficeDocAsText(file)
            }

            // ── Unknown / binary → try to read as text, else show info ────────
            else -> {
                val mime = guessTextOrBinary(file)
                if (mime == "text/plain") {
                    AnalyticsManager.trackFeature("open_text", screen = "FilePane", extra = file.extension)
                    openInEditor(file)
                } else {
                    AnalyticsManager.trackFeature("open_unknown", screen = "FilePane", extra = file.extension)
                    showUnknownFileDialog(file)
                }
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
            val csv = com.fyloxen.app.utils.XlsxParser.toCsv(file)
            if (csv.isBlank()) {
                withContext(Dispatchers.Main) { showOfficeOpenDialog(file) }
                return@launch
            }
            val tmp = java.io.File(cacheDir, "${file.nameWithoutExtension}.csv")
            tmp.writeText(csv)
            withContext(Dispatchers.Main) {
                startActivity(Intent(this@MainActivity,
                    com.fyloxen.app.ui.viewer.CsvViewerActivity::class.java).apply {
                        putExtra(com.fyloxen.app.ui.viewer.CsvViewerActivity.EXTRA_FILE_PATH,
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
        val mime = com.fyloxen.app.model.FileItem(file).mimeType()
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
        val item = com.fyloxen.app.model.FileItem(file)
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
            activePane?.let { pane ->
                pane.setSortOrder(SortOrder.NAME_ASC)
                updateSortUi(SortOrder.NAME_ASC)
            }
            Toast.makeText(this, "Sort reset to default", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
