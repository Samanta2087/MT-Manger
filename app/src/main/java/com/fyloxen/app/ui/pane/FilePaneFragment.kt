package com.fyloxen.app.ui.pane

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.fyloxen.app.R
import com.fyloxen.app.adapter.FileAdapter
import com.fyloxen.app.databinding.FragmentFilePaneBinding
import com.fyloxen.app.model.FileItem
import com.fyloxen.app.model.SortOrder
import com.fyloxen.app.utils.FileUtils
import com.fyloxen.app.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

interface PaneCallback {
    fun onPaneActivated(pane: FilePaneFragment)
    fun onFileOpenRequest(file: File)
    fun getOtherPane(current: FilePaneFragment): FilePaneFragment?
    fun onClipboardChanged()
    fun onSortChanged(sortOrder: SortOrder)
}

class FilePaneFragment : Fragment() {

    private var _binding: FragmentFilePaneBinding? = null
    private val binding get() = _binding!!

    private lateinit var fileAdapter: FileAdapter
    private var callback: PaneCallback? = null

    var currentPath: File = Environment.getExternalStorageDirectory()
        private set

    private val navHistory = ArrayDeque<File>()
    private val navFuture  = ArrayDeque<File>()
    private var sortOrder  = SortOrder.NAME_ASC
    private var showHidden = false
    private var filterQuery = ""
    private var allFiles   = listOf<FileItem>()
    private var fileToHighlight: File? = null
    private var loadJob: Job? = null   // tracks the active file-load so we can cancel stale loads

    // ── Scroll position memory ────────────────────────────────────────────────
    // Maps absolute folder path → (firstVisibleItemPosition, pixelOffset)
    // Saved before leaving a folder; restored after submitList() on back/forward.
    private val scrollPositionCache = HashMap<String, Pair<Int, Int>>()
    private var pendingScrollRestore: String? = null   // path whose position should be restored next

    fun isShowingHidden(): Boolean = showHidden

    fun getCurrentSortOrder(): SortOrder = sortOrder

    fun setSortOrder(order: SortOrder) {
        sortOrder = order
        refresh()
    }

    var isActive = false
        set(value) {
            field = value
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? PaneCallback
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFilePaneBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFileList()
        setupSearch()
        setupSwipeRefresh()
        setupSelectionBar()   // ← wired here, view is guaranteed to exist

        binding.root.setOnClickListener { callback?.onPaneActivated(this) }
        navigateTo(currentPath, addToHistory = false)
    }

    // ─── Setup ────────────────────────────────────────────────────────────

    private fun setupFileList() {
        fileAdapter = FileAdapter(
            onItemClick = { item -> handleItemClick(item) },
            onItemLongClick = { item, view -> handleItemLongClick(item, view); true }
        )
        binding.fileListRv.apply {
            val lm = LinearLayoutManager(requireContext())
            lm.initialPrefetchItemCount = 8  // prefetch 8 items during idle (reduces input latency)
            layoutManager = lm
            adapter = fileAdapter
            setHasFixedSize(true)
            itemAnimator = null             // no add/remove flicker
            setItemViewCacheSize(20)        // keep 20 off-screen views warm (was 15)
            recycledViewPool.setMaxRecycledViews(0, 25)
        }
    }


    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterQuery = s?.toString() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.btnCloseSearch.setOnClickListener {
            binding.searchBar.visibility = View.GONE
            binding.etSearch.text = null
            filterQuery = ""
            applyFilter()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(requireContext().getColor(R.color.accent_blue))
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            requireContext().getColor(R.color.bg_card))
        binding.swipeRefresh.setOnRefreshListener { refresh() }
    }

    fun toggleStatsPanel() {
        val panel = binding.statsPanel
        if (panel.visibility == View.VISIBLE) {
            val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
            anim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(a: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(a: android.view.animation.Animation?) {}
                override fun onAnimationEnd(a: android.view.animation.Animation?) {
                    panel.visibility = View.GONE
                }
            })
            panel.startAnimation(anim)
        } else {
            panel.visibility = View.VISIBLE
            panel.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.slide_down))
        }
    }

    fun isStatsPanelVisible() = binding.statsPanel.visibility == View.VISIBLE


    private fun setupSelectionBar() {
        binding.btnCopy.setOnClickListener {
            val selected = fileAdapter.getSelectedItems()
            FileClipboard.set(selected.map { it.file }, isCut = false)
            callback?.onClipboardChanged()
            exitMultiSelect()
            Toast.makeText(context, "${selected.size} item(s) copied", Toast.LENGTH_SHORT).show()
        }
        binding.btnCut.setOnClickListener {
            val selected = fileAdapter.getSelectedItems()
            FileClipboard.set(selected.map { it.file }, isCut = true)
            callback?.onClipboardChanged()
            exitMultiSelect()
            Toast.makeText(context, "${selected.size} item(s) cut", Toast.LENGTH_SHORT).show()
        }
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmDialog(fileAdapter.getSelectedItems())
        }
        binding.btnShare.setOnClickListener {
            fileAdapter.getSelectedItems().firstOrNull()?.let { shareFile(it.file) }
        }
        binding.btnRename.setOnClickListener {
            val selected = fileAdapter.getSelectedItems()
            if (selected.size == 1) {
                exitMultiSelect()
                showRenameDialog(selected[0])
            } else {
                Toast.makeText(context, "Select exactly one item to rename", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnSelectAll.setOnClickListener {
            fileAdapter.selectAll()
            updateSelectionBar()
        }
        binding.btnMove.setOnClickListener {
            val selected = fileAdapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            showMoveDialog(selected.map { it.file })
        }
        binding.btnCancelSelect.setOnClickListener {
            exitMultiSelect()
        }
    }

    // ─── Navigation ───────────────────────────────────────────────────────

    fun navigateTo(path: File, addToHistory: Boolean = true) {
        if (!path.exists() || !path.isDirectory) return
        // Save scroll position of the folder we are LEAVING before navigating away
        if (addToHistory) {
            saveScrollPosition(currentPath.absolutePath)
            navHistory.addLast(currentPath)
        }
        pendingScrollRestore = null   // forward nav: don't restore (new folder starts at top)
        navFuture.clear()
        currentPath = path
        callback?.onPaneActivated(this)
        loadFiles(animate = addToHistory)
    }

    fun navigateBack(): Boolean {
        if (navHistory.isEmpty()) return false
        navFuture.addFirst(currentPath)
        currentPath = navHistory.removeLast()
        pendingScrollRestore = currentPath.absolutePath   // restore where we were in this folder
        loadFiles(animate = true, isBack = true)
        return true
    }

    fun navigateForward(): Boolean {
        if (navFuture.isEmpty()) return false
        navHistory.addLast(currentPath)
        currentPath = navFuture.removeFirst()
        pendingScrollRestore = currentPath.absolutePath   // restore where we were in this folder
        loadFiles(animate = true)
        return true
    }

    fun navigateParent(): Boolean {
        val parent = currentPath.parentFile ?: return false
        if (!parent.canRead()) return false
        navigateTo(parent)
        return true
    }

    /**
     * Safely updates the stored path without triggering any view operations.
     * Used by the Swap feature for the inactive/background pane that is not
     * attached to a fragment container and therefore has no view lifecycle.
     */
    fun swapPath(path: File) {
        if (path.exists() && path.isDirectory) {
            currentPath = path
            navHistory.clear()
            navFuture.clear()
        }
    }

    fun locateAndHighlight(file: File) {
        val parent = if (file.isDirectory) file else file.parentFile
        if (parent == null || !parent.exists()) return
        fileToHighlight = if (file.isDirectory) null else file
        navigateTo(parent, addToHistory = true)
    }

    fun canGoBack() = navHistory.isNotEmpty()
    fun canGoForward() = navFuture.isNotEmpty()

    fun refresh() {
        binding.swipeRefresh.isRefreshing = true
        loadFiles()
    }

    // ─── File Loading ─────────────────────────────────────────────────────

    private fun loadFiles(animate: Boolean = false, isBack: Boolean = false) {
        // Cancel any in-flight load — only the latest navigation wins
        loadJob?.cancel()

        // Immediately clear stale contents — user sees a blank pane instantly
        // rather than the old folder's files during the IO wait.
        // This is what makes navigation FEEL instant.
        fileAdapter.submitList(emptyList())
        binding.fileListRv.alpha = 1f
        binding.fileListRv.translationY = 0f

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            allFiles = withContext(Dispatchers.IO) {
                FileUtils.listFiles(currentPath, showHidden, sortOrder)
            }
            applyFilter(animate = animate, isBack = isBack)
            updateStats()
            binding.swipeRefresh.isRefreshing = false
        }
    }


    private fun applyFilter(animate: Boolean = false, isBack: Boolean = false) {
        val filtered = if (filterQuery.isBlank()) allFiles
        else allFiles.filter { it.name.contains(filterQuery, ignoreCase = true) }

        fileAdapter.submitList(filtered.toList()) {
            // Smooth entrance animation after list is committed
            if (animate) {
                val rv = binding.fileListRv
                val startY = if (isBack) -28f else 28f   // back = from above, forward = from below
                rv.alpha = 0f
                rv.translationY = startY
                rv.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(2.5f))
                    .setStartDelay(30)   // tiny delay lets layout pass complete first
                    .start()
            }

            fileToHighlight?.let { target ->
                val index = filtered.indexOfFirst { it.file.absolutePath == target.absolutePath }
                if (index != -1) {
                    binding.fileListRv.scrollToPosition(index)
                    if (!fileAdapter.isMultiSelectMode) fileAdapter.enterMultiSelectMode()
                    val item = filtered[index]
                    if (item !in fileAdapter.getSelectedItems()) fileAdapter.toggleSelection(item)
                    updateSelectionBar()
                }
                fileToHighlight = null
                pendingScrollRestore = null   // highlight wins over scroll restore
                return@submitList
            }

            // ── Priority 2: restore saved scroll position (Back / Forward nav) ──
            val restorePath = pendingScrollRestore
            if (restorePath != null) {
                pendingScrollRestore = null
                val (pos, offset) = scrollPositionCache[restorePath] ?: return@submitList
                (binding.fileListRv.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(pos, offset)
            }
        }

        val isEmpty = filtered.isEmpty()
        binding.emptyState.visibility  = if (isEmpty) View.VISIBLE else View.GONE
        binding.swipeRefresh.visibility = if (isEmpty) View.GONE   else View.VISIBLE
        if (isEmpty) binding.swipeRefresh.isRefreshing = false
    }

    /** Captures the current scroll position and stores it keyed by [pathKey]. */
    private fun saveScrollPosition(pathKey: String) {
        val lm  = binding.fileListRv.layoutManager as? LinearLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos == RecyclerView.NO_POSITION) return
        val view   = lm.findViewByPosition(pos) ?: return
        val offset = view.top - binding.fileListRv.paddingTop
        scrollPositionCache[pathKey] = Pair(pos, offset)
    }

    private fun updateStats() {
        val dirs  = allFiles.count { it.isDirectory }
        val files = allFiles.count { !it.isDirectory }
        binding.tvFolderCount.text = "$dirs"
        binding.tvFileCount.text   = "$files"
    }

    // ─── Item Interaction ─────────────────────────────────────────────────

    private fun handleItemClick(item: FileItem) {
        callback?.onPaneActivated(this)
        if (fileAdapter.isMultiSelectMode) {
            fileAdapter.toggleSelection(item)
            updateSelectionBar()
            return
        }
        // Single-click on a supported archive → show extract dialog immediately
        if (!item.isDirectory && ZipUtils.isSupported(item.file)) {
            showExtractDialog(item)
            return
        }
        if (item.isDirectory) {
            navigateTo(item.file)
        } else {
            callback?.onFileOpenRequest(item.file)
        }
    }

    private fun handleItemLongClick(item: FileItem, anchor: View) {
        callback?.onPaneActivated(this)
        // If already in multi-select, just toggle selection
        if (fileAdapter.isMultiSelectMode) {
            fileAdapter.toggleSelection(item)
            updateSelectionBar()
            return
        }
        // Show styled bottom sheet — theme-aware style
        val sheetStyle = if (com.fyloxen.app.utils.ThemeManager.isLightGlass(requireContext()))
            R.style.LiquidGlass_BottomSheetDialog
        else
            R.style.FileMenuBottomSheet
        val sheet = BottomSheetDialog(requireContext(), sheetStyle)
        val root  = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_generic_menu, null, false)
        root.findViewById<TextView>(R.id.tvSheetTitle).text = item.name
        val container = root.findViewById<LinearLayout>(R.id.menuItemsContainer)

        data class Action(val icon: Int, val label: String, val tint: Int, val run: () -> Unit)
        val actions = mutableListOf(
            Action(R.drawable.ic_edit,       getString(R.string.rename),     0xFF4F8EF7.toInt()) { showRenameDialog(item) },
            Action(R.drawable.ic_select_all, "Select",                        0xFFA78BFA.toInt()) {
                if (!fileAdapter.isMultiSelectMode) fileAdapter.enterMultiSelectMode()
                fileAdapter.toggleSelection(item); updateSelectionBar() },
            Action(R.drawable.ic_copy,       getString(R.string.copy),       0xFF22D3EE.toInt()) {
                FileClipboard.set(listOf(item.file), isCut = false); callback?.onClipboardChanged() },
            Action(R.drawable.ic_cut,        getString(R.string.cut),        0xFFFBBF24.toInt()) {
                FileClipboard.set(listOf(item.file), isCut = true);  callback?.onClipboardChanged() },
            Action(R.drawable.ic_move,       "Move To…",                   0xFF10B981.toInt()) { showMoveDialog(listOf(item.file)) },
            Action(R.drawable.ic_delete,     getString(R.string.delete),     0xFFEF4444.toInt()) { showDeleteConfirmDialog(listOf(item)) },
            Action(R.drawable.ic_share,      getString(R.string.share),      0xFF34D399.toInt()) { shareFile(item.file) },
            Action(R.drawable.ic_info,       getString(R.string.properties), 0xFF8BA8CC.toInt()) { showFileInfoDialog(item) }
        )
        // ✨ Prepend Extract Here for supported archives
        if (!item.isDirectory && ZipUtils.isSupported(item.file)) {
            actions.add(0, Action(R.drawable.ic_extract, "Extract Here", 0xFF10B981.toInt()) {
                sheet.dismiss()
                showExtractDialog(item)
            })
        }
        actions.forEach { action ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_menu_action, container, false)
            row.findViewById<ImageView>(R.id.ivActionIcon).apply {
                setImageResource(action.icon)
                setColorFilter(action.tint)
            }
            row.findViewById<TextView>(R.id.tvActionLabel).text = action.label
            row.setOnClickListener { action.run(); sheet.dismiss() }
            container.addView(row)
        }
        sheet.setContentView(root)
        sheet.show()
    }

    // ─── Zip Extraction ────────────────────────────────────────────────────

    private fun showExtractDialog(item: FileItem) {
        val destDir = File(item.file.parent, item.file.nameWithoutExtension)
        val isEncrypted = runCatching { ZipUtils.isEncrypted(item.file) }.getOrDefault(false)

        val dialogView = layoutInflater.inflate(R.layout.dialog_extract, null)
        dialogView.findViewById<android.widget.TextView>(R.id.tvExtractFileName).text = item.name
        dialogView.findViewById<android.widget.TextView>(R.id.tvExtractDestHint).text =
            "→ ${destDir.absolutePath}/"

        val tvPasswordLabel = dialogView.findViewById<android.widget.TextView>(R.id.tvPasswordLabel)
        val tilPassword     = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPassword)
        val etPassword      = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword)
        val tvError         = dialogView.findViewById<android.widget.TextView>(R.id.tvExtractError)

        if (isEncrypted) {
            tvPasswordLabel.visibility = View.VISIBLE
            tilPassword.visibility     = View.VISIBLE
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Extract Archive")
            .setView(dialogView)
            .setPositiveButton("Extract", null)   // null — we handle click manually for retry
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val password = etPassword.text?.toString()?.takeIf { it.isNotBlank() }
                tvError.visibility = View.GONE

                // If dest already exists, confirm overwrite
                if (destDir.exists()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Folder Already Exists")
                        .setMessage("\"${destDir.name}\" already exists. Overwrite?")
                        .setPositiveButton("Overwrite") { _, _ ->
                            dialog.dismiss()
                            doExtract(item.file, destDir, password, tvError, dialog)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                } else {
                    dialog.dismiss()
                    doExtract(item.file, destDir, password, tvError, dialog)
                }
            }
        }
        dialog.show()
    }

    private fun doExtract(
        zipFile: File,
        destDir: File,
        password: String?,
        tvError: android.widget.TextView?,
        parentDialog: androidx.appcompat.app.AlertDialog?
    ) {
        Toast.makeText(context, "📦 Extracting ${zipFile.name}…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ZipUtils.extract(zipFile, destDir, password)
            }
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                Toast.makeText(
                    context,
                    "✅ Extracted $count file(s) → ${destDir.name}/",
                    Toast.LENGTH_SHORT
                ).show()
                // Open the extracted folder automatically
                navigateTo(destDir)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Extraction failed"
                val isWrongPassword = msg.contains("password", ignoreCase = true) ||
                    msg.contains("wrong", ignoreCase = true) ||
                    msg.contains("incorrect", ignoreCase = true)
                if (isWrongPassword) {
                    Toast.makeText(context, "🔒 Wrong password — try again", Toast.LENGTH_SHORT).show()
                    // Re-open dialog so user can retry with a different password
                    val fakeItem = com.fyloxen.app.model.FileItem(zipFile)
                    showExtractDialog(fakeItem)
                } else {
                    Toast.makeText(context, "❌ $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateSelectionBar() {
        val selected = fileAdapter.getSelectedItems()
        if (selected.isEmpty() && fileAdapter.isMultiSelectMode) {
            exitMultiSelect()
            return
        }
        val count = selected.size
        if (count > 0) {
            binding.selectionBar.visibility = View.VISIBLE
            binding.tvSelectedCount.visibility = View.VISIBLE
            binding.tvSelectedCount.text = "$count selected"
        } else {
            binding.selectionBar.visibility = View.GONE
            binding.tvSelectedCount.visibility = View.GONE
        }
    }

    fun exitMultiSelect() {
        fileAdapter.exitMultiSelectMode()
        binding.selectionBar.visibility = View.GONE
        binding.tvSelectedCount.visibility = View.GONE
    }

    fun isFileAdapterMultiSelectMode(): Boolean = fileAdapter.isMultiSelectMode

    fun enterMultiSelectMode() {
        fileAdapter.enterMultiSelectMode()
    }

    fun selectAllFiles() {
        fileAdapter.selectAll()
        updateSelectionBar()
    }

    // ─── File Operations ──────────────────────────────────────────────────

    fun showNewItemDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_item, null)
        val rbFile: android.widget.RadioButton = dialogView.findViewById(R.id.rbFile)
        val rbFolder: android.widget.RadioButton = dialogView.findViewById(R.id.rbFolder)
        val etName: android.widget.EditText = dialogView.findViewById(R.id.etName)
        val tvError: android.widget.TextView = dialogView.findViewById(R.id.tvError)
        rbFile.isChecked = true

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) { tvError.text = "Name is required"; tvError.visibility = View.VISIBLE; return@setPositiveButton }
                val result = if (rbFile.isChecked) FileUtils.createFile(currentPath, name)
                else FileUtils.createFolder(currentPath, name)
                result.onSuccess { refresh() }
                result.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showRenameDialog(item: FileItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_rename, null)
        val etName: android.widget.EditText = dialogView.findViewById(R.id.etNewName)
        etName.setText(item.name)
        etName.selectAll()

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty() || name == item.name) return@setPositiveButton
                FileUtils.rename(item.file, name)
                    .onSuccess { refresh() }
                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showDeleteConfirmDialog(items: List<FileItem>) {
        val msg = if (items.size == 1)
            getString(R.string.delete_msg, items[0].name)
        else getString(R.string.delete_multiple_msg, items.size)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete)
            .setMessage(msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    items.forEach { FileUtils.delete(it.file) }
                    withContext(Dispatchers.Main) { exitMultiSelect(); refresh() }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showFileInfoDialog(item: FileItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_file_info, null)
        dialogView.findViewById<android.widget.TextView>(R.id.tvInfoIcon).text =
            if (item.isDirectory) FileUtils.getFolderIcon() else FileUtils.getFileIcon(item.extension)
        dialogView.findViewById<android.widget.TextView>(R.id.tvInfoName).text = item.name
        dialogView.findViewById<android.widget.TextView>(R.id.tvInfoType).text =
            if (item.isDirectory) "Directory" else "File (.${item.extension})"
        dialogView.findViewById<android.widget.TextView>(R.id.tvInfoPath).text = item.file.absolutePath
        dialogView.findViewById<android.widget.TextView>(R.id.tvInfoDate).text = item.formattedDate()
        dialogView.findViewById<android.widget.TextView>(R.id.tvInfoReadable).text =
            if (item.file.canRead()) "Yes" else "No"
        dialogView.findViewById<android.widget.TextView>(R.id.tvInfoWritable).text =
            if (item.file.canWrite()) "Yes" else "No"

        val sizeText = if (item.isDirectory) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val s = FileUtils.getFolderSize(item.file)
                withContext(Dispatchers.Main) {
                    dialogView.findViewById<android.widget.TextView>(R.id.tvInfoSize).text =
                        FileUtils.formatSize(s)
                }
            }
            "Calculating..."
        } else item.formattedSize()
        dialogView.findViewById<android.widget.TextView>(R.id.tvInfoSize).text = sizeText

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    fun showSortDialog() {
        val options = SortOrder.values().map { it.label }.toTypedArray()
        val current = SortOrder.values().indexOf(sortOrder)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(options, current) { dialog, which ->
                sortOrder = SortOrder.values()[which]
                callback?.onSortChanged(sortOrder)
                refresh()
                dialog.dismiss()
            }
            .show()
    }

    fun toggleHiddenFiles() {
        showHidden = !showHidden
        refresh()
    }

    fun toggleSearchBar() {
        if (binding.searchBar.visibility == View.VISIBLE) {
            binding.searchBar.visibility = View.GONE
            filterQuery = ""
            applyFilter()
        } else {
            binding.searchBar.visibility = View.VISIBLE
            binding.etSearch.requestFocus()
        }
    }

    fun toggleLayoutMode() {
        android.widget.Toast.makeText(requireContext(), "Grid Layout Comming Soon", android.widget.Toast.LENGTH_SHORT).show()
    }

    // ─── Context Menu ─────────────────────────────────────────────────────

    fun showItemContextMenu(item: FileItem, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 0, 0, if (item.isDirectory) "Open" else "Open")
        if (!item.isDirectory && item.isTextFile()) popup.menu.add(0, 1, 1, "Edit")
        popup.menu.add(0, 2, 2, getString(R.string.rename))
        popup.menu.add(0, 3, 3, getString(R.string.copy))
        popup.menu.add(0, 4, 4, getString(R.string.cut))
        popup.menu.add(0, 5, 5, getString(R.string.delete))
        popup.menu.add(0, 6, 6, getString(R.string.share))
        popup.menu.add(0, 7, 7, getString(R.string.properties))
        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                0 -> handleItemClick(item)
                1 -> callback?.onFileOpenRequest(item.file)
                2 -> showRenameDialog(item)
                3 -> { FileClipboard.set(listOf(item.file), isCut = false); callback?.onClipboardChanged() }
                4 -> { FileClipboard.set(listOf(item.file), isCut = true); callback?.onClipboardChanged() }
                5 -> showDeleteConfirmDialog(listOf(item))
                6 -> shareFile(item.file)
                7 -> showFileInfoDialog(item)
            }
            true
        }
        popup.show()
    }

    private fun showPaneContextMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 0, 0, "Refresh")
        popup.menu.add(0, 1, 1, if (showHidden) "Hide Hidden Files" else "Show Hidden Files")
        popup.menu.add(0, 2, 2, getString(R.string.sort_by))
        popup.menu.add(0, 3, 3, "Filter")
        popup.menu.add(0, 4, 4, "Select All")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> refresh()
                1 -> toggleHiddenFiles()
                2 -> showSortDialog()
                3 -> toggleSearchBar()
                4 -> {
                    if (!fileAdapter.isMultiSelectMode) fileAdapter.enterMultiSelectMode()
                    fileAdapter.selectAll(); updateSelectionBar()
                }
            }
            true
        }
        popup.show()
    }

    // ─── Clipboard & Paste ────────────────────────────────────────────────

    fun pasteClipboard() {
        val clip = FileClipboard.get() ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            clip.files.forEach { src ->
                val result = if (clip.isCut) FileUtils.move(src, currentPath)
                else FileUtils.copy(src, currentPath)
                result.onFailure {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (clip.isCut) FileClipboard.clear()
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    // ─── Share ────────────────────────────────────────────────────────────

    private fun shareFile(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.provider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = com.fyloxen.app.model.FileItem(file).mimeType()
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "Share ${file.name}"))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun getSelectedFiles() = fileAdapter.getSelectedItems()

    // ─── Move ─────────────────────────────────────────────────────────────────

    fun showMoveDialog(filesToMove: List<File>) {
        // Start browsing from the current directory
        var browsePath = currentPath

        val dialogView = layoutInflater.inflate(R.layout.dialog_move_picker, null)
        val tvPath      = dialogView.findViewById<android.widget.TextView>(R.id.tvMovePath)
        val rvFolders   = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvMoveFolders)
        val btnParent   = dialogView.findViewById<android.widget.ImageButton>(R.id.btnMoveParent)

        fun refreshBrowser() {
            tvPath.text = browsePath.absolutePath
            val dirs = browsePath.listFiles()
                ?.filter { it.isDirectory && it.canRead() }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()

            rvFolders.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                inner class VH(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v)
                override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH {
                    val row = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_move_folder, parent, false)
                    return VH(row)
                }
                override fun getItemCount() = dirs.size
                override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, pos: Int) {
                    val dir = dirs[pos]
                    holder.itemView.findViewById<android.widget.TextView>(R.id.tvMoveFolderName).text = dir.name
                    holder.itemView.setOnClickListener {
                        browsePath = dir
                        refreshBrowser()
                    }
                }
            }
            btnParent.isEnabled = browsePath.parentFile != null
        }

        rvFolders.layoutManager = LinearLayoutManager(requireContext())
        refreshBrowser()

        btnParent.setOnClickListener {
            browsePath.parentFile?.let { browsePath = it; refreshBrowser() }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Move ${filesToMove.size} item(s) to…")
            .setView(dialogView)
            .setPositiveButton("Move Here") { _, _ ->
                performMove(filesToMove, browsePath)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performMove(files: List<File>, destination: File) {
        if (!destination.exists() || !destination.isDirectory) {
            Toast.makeText(context, "Invalid destination", Toast.LENGTH_SHORT).show()
            return
        }
        exitMultiSelect()
        Toast.makeText(context, "Moving ${files.size} item(s)…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var failed = 0
            files.forEach { src ->
                val result = FileUtils.move(src, destination)
                if (result.isFailure) failed++
            }
            withContext(Dispatchers.Main) {
                if (failed == 0) {
                    Toast.makeText(context, "✅ Moved to ${destination.name}/", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "⚠ $failed item(s) failed to move", Toast.LENGTH_SHORT).show()
                }
                refresh()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Global clipboard shared between panes
object FileClipboard {
    data class ClipData(val files: List<File>, val isCut: Boolean)
    private var data: ClipData? = null
    fun set(files: List<File>, isCut: Boolean) { data = ClipData(files, isCut) }
    fun get() = data
    fun clear() { data = null }
    fun hasData() = data != null
}
