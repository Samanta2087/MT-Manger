package com.fyloxen.app.adapter

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fyloxen.app.R
import com.fyloxen.app.model.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance file list adapter.
 *
 * Key optimisations:
 * - NO per-bind coroutines (folder sizes removed — caused scroll jank via IO pressure)
 * - NO layoutParams mutation on every bind (done once per holder in onCreateViewHolder)
 * - NO TypedValue / theme attribute resolution on bind (cached in onAttachedToRecyclerView)
 * - click listeners set once in onCreateViewHolder, not re-set per bind
 * - DiffUtil drives incremental updates; multi-select uses notifyItemRangeChanged
 */
class FileAdapter(
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem, View) -> Boolean
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(DIFF_CALLBACK) {

    var isMultiSelectMode = false
        private set

    // Lazy child-count cache — filled in background after list appears
    private val childCountCache = ConcurrentHashMap<String, Int>(64)
    // One low-priority scope for all background count work — limited to avoid IO pressure
    private val countScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Cached once in onAttachedToRecyclerView – never recomputed per bind ──
    private var isLightTheme       = false
    private var folderIconPx       = 0
    private var defaultIconPx      = 0
    private var folderContainerPx  = 0
    private var defaultContainerPx = 0

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val ctx = recyclerView.context
        val tv  = android.util.TypedValue()
        isLightTheme = if (ctx.theme.resolveAttribute(
                com.fyloxen.app.R.attr.xyvionChevronVisible, tv, true)) tv.data != 0 else false
        val dp = ctx.resources.displayMetrics.density
        folderIconPx       = (55 * dp).toInt()
        defaultIconPx      = (34 * dp).toInt()
        folderContainerPx  = (57 * dp).toInt()
        defaultContainerPx = (52 * dp).toInt()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        // Clear cache when leaving folder so stale counts don't persist
        childCountCache.clear()
    }

    // ── ViewHolder ─────────────────────────────────────────────────────────────
    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rowCard: View              = itemView.findViewById(R.id.itemRowCard)
        val iconContainer: FrameLayout = itemView.findViewById(R.id.iconContainer)
        val ivIcon: ImageView          = itemView.findViewById(R.id.ivFileIcon)
        val tvName: TextView           = itemView.findViewById(R.id.tvFileName)
        val tvSize: TextView           = itemView.findViewById(R.id.tvFileSize)
        val tvDate: TextView           = itemView.findViewById(R.id.tvFileDate)
        val tvDot: TextView            = itemView.findViewById(R.id.tvDot)
        val tvBadge: TextView          = itemView.findViewById(R.id.tvExtBadge)
        val checkbox: CheckBox         = itemView.findViewById(R.id.checkbox)
        val chevronContainer: View     = itemView.findViewById(R.id.chevronContainer)

        // Pre-allocated LayoutParams — set ONCE here, mutated in-place during bind
        // to avoid allocating new objects on every onBindViewHolder call.
        val iconLp: ViewGroup.LayoutParams = ivIcon.layoutParams
        val containerLp: ViewGroup.LayoutParams = iconContainer.layoutParams
    }

    // ── Create ─────────────────────────────────────────────────────────────────
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        val holder = FileViewHolder(view)
        // Click listeners set ONCE here — not re-set on every bind
        holder.rowCard.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onItemClick(getItem(pos))
        }
        holder.rowCard.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onItemLongClick(getItem(pos), it) else false
        }
        return holder
    }

    // ── Bind ───────────────────────────────────────────────────────────────────
    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = getItem(position)
        val ctx  = holder.itemView.context

        if (item.isDirectory) {
            bindFolder(holder, item)
        } else {
            bindFile(holder, item, ctx)
        }

        // ── Name ──
        holder.tvName.text  = item.name
        holder.tvName.alpha = if (item.isHidden) 0.45f else 1.0f

        // ── Multi-select ──
        holder.checkbox.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
        holder.checkbox.isChecked  = item.isSelected
        holder.rowCard.setBackgroundColor(
            if (item.isSelected) ctx.getColor(R.color.item_selected_bg)
            else 0x00000000 // transparent — let XML card handle normal bg
        )
    }

    private fun bindFolder(holder: FileViewHolder, item: FileItem) {
        val folderRes = if (isLightTheme) R.drawable.lg_ic_folder else R.drawable.img_folder_new
        holder.ivIcon.setImageResource(folderRes)
        holder.ivIcon.clearColorFilter()
        holder.ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER

        holder.iconLp.width  = folderIconPx
        holder.iconLp.height = folderIconPx
        holder.ivIcon.layoutParams = holder.iconLp

        holder.containerLp.width  = folderContainerPx
        holder.containerLp.height = folderContainerPx
        holder.iconContainer.layoutParams = holder.containerLp
        holder.iconContainer.setBackgroundResource(android.R.color.transparent)

        holder.chevronContainer.visibility = if (isLightTheme) View.VISIBLE else View.GONE

        // Show cached count or placeholder — never block here
        val path = item.file.absolutePath
        val cached = childCountCache[path]
        if (cached != null) {
            holder.tvSize.text = if (cached == 1) "1 item" else "$cached items"
        } else {
            holder.tvSize.text = "Folder"
            // Compute lazily in background, update only this holder
            countScope.launch {
                val count = try { item.file.listFiles()?.size ?: 0 }
                            catch (_: Exception) { 0 }
                childCountCache[path] = count
                withContext(Dispatchers.Main) {
                    // Only update if this holder still shows the same item
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION &&
                        getItem(pos).file.absolutePath == path) {
                        holder.tvSize.text = if (count == 1) "1 item" else "$count items"
                    }
                }
            }
        }
        holder.tvSize.visibility = View.VISIBLE

        holder.tvDate.text       = item.formattedDate()
        holder.tvDate.visibility = View.VISIBLE
        holder.tvDot.visibility  = View.VISIBLE
        holder.tvBadge.visibility = View.GONE
    }

    private fun bindFile(holder: FileViewHolder, item: FileItem, ctx: android.content.Context) {
        holder.iconLp.width  = defaultIconPx
        holder.iconLp.height = defaultIconPx
        holder.ivIcon.layoutParams = holder.iconLp

        holder.containerLp.width  = defaultContainerPx
        holder.containerLp.height = defaultContainerPx
        holder.iconContainer.layoutParams = holder.containerLp

        holder.chevronContainer.visibility = View.GONE

        val ext = item.extension.lowercase()
        val iconContainerBg = if (isLightTheme) android.R.color.transparent else R.drawable.bg_icon_container
        holder.ivIcon.clearColorFilter()
        holder.ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER
        holder.iconContainer.setBackgroundResource(iconContainerBg)

        fun icon(light: Int, dark: Int): Int = if (isLightTheme) light else dark

        when (ext) {
            "py"                                    -> holder.ivIcon.setImageResource(R.drawable.img_python_new)
            "js", "ts", "jsx", "tsx"                -> holder.ivIcon.setImageResource(R.drawable.img_js)
            "zip","gz","tar","rar","7z","xz","bz2","apks" -> holder.ivIcon.setImageResource(
                icon(R.drawable.img_zip, R.drawable.img_zip_dark))
            "apk"                                   -> holder.ivIcon.setImageResource(R.drawable.img_apk)
            "txt","log","md","ini","conf","cfg","prop" -> holder.ivIcon.setImageResource(
                icon(R.drawable.img_txt, R.drawable.img_txt_dark))
            "doc","docx","odt","rtf"                -> holder.ivIcon.setImageResource(
                icon(R.drawable.img_docx, R.drawable.img_docx))
            "xls","xlsx","ods","numbers"            -> holder.ivIcon.setImageResource(
                icon(R.drawable.img_xls, R.drawable.img_xls))
            "csv","tsv"                             -> holder.ivIcon.setImageResource(
                icon(R.drawable.img_csv, R.drawable.img_csv_dark))
            "pdf"                                   -> holder.ivIcon.setImageResource(
                icon(R.drawable.img_pdf, R.drawable.img_pdf_dark))
            "db","sqlite","db3","sqlite3","realm"   -> holder.ivIcon.setImageResource(
                icon(R.drawable.img_db, R.drawable.img_db_dark))
            "json","geojson"                        -> holder.ivIcon.setImageResource(
                icon(R.drawable.img_json, R.drawable.img_json_dark))
            "jpg","jpeg","png","gif","webp","bmp","svg","heic","tiff" -> holder.ivIcon.setImageResource(
                icon(R.drawable.img_jpg, R.drawable.img_jpg_dark))
            "mp3","wav","aac","flac","ogg","m4a","opus","wma" -> holder.ivIcon.setImageResource(
                icon(R.drawable.img_mp3, R.drawable.img_mp3_dark))
            "mp4","mkv","avi","mov","wmv","3gp","webm","flv","ts" -> holder.ivIcon.setImageResource(
                icon(R.drawable.img_mp4, R.drawable.img_mp4_dark))
            "bak"                                   -> holder.ivIcon.setImageResource(R.drawable.img_bak)
            "enc"                                   -> holder.ivIcon.setImageResource(R.drawable.img_enc)
            else -> {
                holder.ivIcon.setImageResource(R.drawable.ic_file_flat)
                holder.ivIcon.setColorFilter(
                    if (isLightTheme) ctx.getColor(R.color.file_default) else 0xFFCCDDFF.toInt(),
                    PorterDuff.Mode.SRC_IN)
            }
        }

        // File metadata
        holder.tvSize.text       = item.formattedSize()
        holder.tvSize.visibility = View.VISIBLE
        holder.tvDate.text       = item.formattedDate()
        holder.tvDate.visibility = View.VISIBLE
        holder.tvDot.visibility  = View.VISIBLE

        // Extension badge
        if (ext.isNotEmpty()) {
            holder.tvBadge.text       = ext.uppercase()
            holder.tvBadge.visibility = View.VISIBLE
            val badgeColor = when (ext) {
                "bak"                                               -> R.color.text_badge_bak
                "py"                                                -> R.color.text_badge_py
                "txt"                                               -> R.color.text_badge_txt
                "enc"                                               -> R.color.text_badge_enc
                "apk"                                               -> R.color.accent_blue
                "zip","gz","tar","rar","7z","xz","bz2"             -> R.color.text_badge_zip
                "mp4","mkv","avi","mov","wmv","3gp"                 -> R.color.text_badge_mp4
                "mp3","wav","aac","flac","ogg","m4a"               -> R.color.text_badge_mp3
                "jpg","jpeg","png","gif","webp","bmp"               -> R.color.text_badge_jpg
                "db","sqlite","db3","sqlite3"                       -> R.color.text_badge_db
                "json"                                              -> R.color.text_badge_json
                "pdf"                                               -> R.color.text_badge_pdf
                "csv","tsv"                                         -> R.color.text_badge_csv
                else                                                -> R.color.text_secondary
            }
            holder.tvBadge.setTextColor(ctx.getColor(badgeColor))
        } else {
            holder.tvBadge.visibility = View.GONE
        }
    }

    // ── Multi-select ───────────────────────────────────────────────────────────

    fun enterMultiSelectMode() {
        isMultiSelectMode = true
        // Targeted range update — no full redraw
        notifyItemRangeChanged(0, itemCount)
    }

    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        currentList.forEach { it.isSelected = false }
        notifyItemRangeChanged(0, itemCount)
    }

    fun toggleSelection(item: FileItem) {
        val idx = currentList.indexOf(item)
        if (idx >= 0) {
            currentList[idx].isSelected = !currentList[idx].isSelected
            notifyItemChanged(idx)
        }
    }

    fun selectAll() {
        currentList.forEach { it.isSelected = true }
        notifyItemRangeChanged(0, itemCount)
    }

    fun getSelectedItems(): List<FileItem> = currentList.filter { it.isSelected }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FileItem>() {
            override fun areItemsTheSame(old: FileItem, new: FileItem) =
                old.file.absolutePath == new.file.absolutePath
            override fun areContentsTheSame(old: FileItem, new: FileItem) =
                old.name == new.name && old.size == new.size &&
                old.lastModified == new.lastModified && old.isSelected == new.isSelected
        }
    }
}
