package com.mtmanager.lite.adapter

import android.content.res.TypedArray
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtmanager.lite.R
import com.mtmanager.lite.model.FileItem
import com.mtmanager.lite.utils.FileUtils

class FileAdapter(
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem, View) -> Boolean
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(DIFF_CALLBACK) {

    var isMultiSelectMode = false
        private set

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
        val occupancyContainer: View   = itemView.findViewById(R.id.occupancyContainer)
        val occupancyFill: View        = itemView.findViewById(R.id.occupancyFill)
        val tvOccupancyPct: TextView   = itemView.findViewById(R.id.tvOccupancyPct)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = getItem(position)
        val ctx  = holder.itemView.context

        // ── Resolve theme attr: is chevron visible (light glass = true) ──
        val showChevron = resolveThemeBool(ctx, R.attr.xyvionChevronVisible)
        val isLight     = resolveThemeBool(ctx, R.attr.xyvionChevronVisible)

        // ──────────────────────────────────────────
        // Icon & Container — Use real asset images where available
        // ──────────────────────────────────────────
        val dp = ctx.resources.displayMetrics.density
        val folderIconPx  = (55 * dp).toInt()
        val defaultIconPx = (36 * dp).toInt()

        val folderContainerPx  = (57 * dp).toInt()
        val defaultContainerPx = (44 * dp).toInt()

        val isLightTheme = resolveThemeBool(ctx, R.attr.xyvionChevronVisible)
        val iconContainerBg = if (isLightTheme) android.R.color.transparent else R.drawable.bg_icon_container

        if (item.isDirectory) {
            // Theme-aware folder icon
            val folderRes = if (isLightTheme) R.drawable.lg_ic_folder else R.drawable.img_folder_new
            holder.ivIcon.setImageResource(folderRes)
            holder.ivIcon.clearColorFilter()
            holder.ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER
            holder.ivIcon.layoutParams = holder.ivIcon.layoutParams.apply {
                width  = folderIconPx
                height = folderIconPx
            }
            holder.iconContainer.layoutParams = holder.iconContainer.layoutParams.apply {
                width  = folderContainerPx
                height = folderContainerPx
            }
            holder.iconContainer.setBackgroundResource(android.R.color.transparent)

            // Show chevron for folders in light theme
            holder.chevronContainer.visibility = if (showChevron) View.VISIBLE else View.GONE

            // Show item count for folders
            val childCount = try {
                item.file.listFiles()?.size ?: 0
            } catch (_: Exception) { 0 }
            holder.tvSize.text = "$childCount items"
            holder.tvSize.visibility = View.VISIBLE

            // Date visible for folders
            holder.tvDate.text = item.formattedDate()
            holder.tvDate.visibility = View.VISIBLE
            holder.tvDot.visibility = View.VISIBLE

            // ── Occupancy indicator for folders ─────────────────────────────
            if (isLightTheme && childCount > 0) {
                holder.occupancyContainer.visibility = View.VISIBLE
                val pct = (childCount * 100 / 25).coerceIn(1, 100)
                holder.tvOccupancyPct.text = "$pct%"
                holder.occupancyFill.post {
                    val parent = holder.occupancyFill.parent as? android.view.View
                    val totalWidth = parent?.width ?: 0
                    val fillWidth = (totalWidth * pct / 100).coerceAtLeast(4)
                    val lp = holder.occupancyFill.layoutParams
                    lp.width = fillWidth
                    holder.occupancyFill.layoutParams = lp
                }
            } else {
                holder.occupancyContainer.visibility = View.GONE
            }
        } else {
            // Reset size for file icons (guards against RecyclerView reuse)
            holder.ivIcon.layoutParams = holder.ivIcon.layoutParams.apply {
                width  = defaultIconPx
                height = defaultIconPx
            }
            holder.iconContainer.layoutParams = holder.iconContainer.layoutParams.apply {
                width  = defaultContainerPx
                height = defaultContainerPx
            }
            // Hide chevron for files
            holder.chevronContainer.visibility = View.GONE

            val ext = item.extension.lowercase()

            // ── Theme-aware icon picker ─────────────────────────────────────
            // drawable-night/ won't auto-trigger with manual ThemeManager,
            // so we pick the right resource explicitly based on isLightTheme.
            fun icon(@androidx.annotation.DrawableRes light: Int,
                     @androidx.annotation.DrawableRes dark: Int): Int =
                if (isLightTheme) light else dark

            holder.ivIcon.clearColorFilter()   // reset any leftover tint
            holder.ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER
            holder.iconContainer.setBackgroundResource(iconContainerBg)

            when (ext) {
                // ── Code / Scripts ──
                "py"                               -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_python_new, R.drawable.img_python_new))
                "js", "ts", "jsx", "tsx"           -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_js, R.drawable.img_js))
                // ── Archives ──
                "zip","gz","tar","rar","7z","xz","bz2","apks" -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_zip, R.drawable.img_zip_dark))
                // ── APK ──
                "apk"                              -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_apk, R.drawable.img_apk))
                // ── Text ──
                "txt","log","md","ini","conf","cfg","prop"    -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_txt, R.drawable.img_txt_dark))
                // ── Word Docs ──
                "doc","docx","odt","rtf"           -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_docx, R.drawable.img_docx))
                // ── Spreadsheets ──
                "xls","xlsx","ods","numbers"       -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_xls, R.drawable.img_xls))
                // ── CSV ──
                "csv","tsv"                        -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_csv, R.drawable.img_csv_dark))
                // ── PDF ──
                "pdf"                              -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_pdf, R.drawable.img_pdf_dark))
                // ── Database ──
                "db","sqlite","db3","sqlite3","realm" -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_db, R.drawable.img_db_dark))
                // ── JSON ──
                "json","geojson"                   -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_json, R.drawable.img_json_dark))
                // ── Images ──
                "jpg","jpeg","png","gif","webp","bmp","svg","heic","tiff" ->
                    holder.ivIcon.setImageResource(
                    icon(R.drawable.img_jpg, R.drawable.img_jpg_dark))
                // ── Audio ──
                "mp3","wav","aac","flac","ogg","m4a","opus","wma" ->
                    holder.ivIcon.setImageResource(
                    icon(R.drawable.img_mp3, R.drawable.img_mp3_dark))
                // ── Video ──
                "mp4","mkv","avi","mov","wmv","3gp","webm","flv","ts" ->
                    holder.ivIcon.setImageResource(
                    icon(R.drawable.img_mp4, R.drawable.img_mp4_dark))
                // ── Backup / Enc ──
                "bak"                              -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_bak, R.drawable.img_bak))
                "enc"                              -> holder.ivIcon.setImageResource(
                    icon(R.drawable.img_enc, R.drawable.img_enc))
                // ── Default fallback ──
                else -> {
                    holder.ivIcon.setImageResource(R.drawable.ic_file_flat)
                    val tintColor = if (isLightTheme)
                        ctx.getColor(R.color.file_default)
                    else
                        0xFFCCDDFF.toInt()
                    holder.ivIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                }
            }


        }

        // ──────────────────────────────────────────
        // File Name
        // ──────────────────────────────────────────
        holder.tvName.text  = item.name
        holder.tvName.alpha = if (item.isHidden) 0.45f else 1.0f

        // ──────────────────────────────────────────
        // Metadata — size for files, item count for folders
        // ──────────────────────────────────────────
        if (!item.isDirectory) {
            holder.tvSize.text        = item.formattedSize()
            holder.tvSize.visibility  = View.VISIBLE
            holder.tvDate.text        = item.formattedDate()
            holder.tvDate.visibility  = View.VISIBLE
            holder.tvDot.visibility   = View.VISIBLE
            // Files don't have occupancy bars
            holder.occupancyContainer.visibility = View.GONE
        }

        // Extension Badge — shown in the row end area
        if (!item.isDirectory && item.extension.isNotEmpty()) {
            val ext = item.extension.lowercase()
            holder.tvBadge.text       = ext.uppercase()
            holder.tvBadge.visibility = View.VISIBLE

            val textColorRes = when (ext) {
                "bak"                               -> R.color.text_badge_bak
                "py"                                -> R.color.text_badge_py
                "txt"                               -> R.color.text_badge_txt
                "enc"                               -> R.color.text_badge_enc
                "apk"                               -> R.color.accent_blue
                "zip", "gz", "tar", "rar", "7z",
                "xz", "bz2"                                  -> R.color.text_badge_zip
                "mp4", "mkv", "avi", "mov", "wmv", "3gp"    -> R.color.text_badge_mp4
                "mp3", "wav", "aac", "flac", "ogg", "m4a"   -> R.color.text_badge_mp3
                "jpg", "jpeg", "png", "gif", "webp", "bmp"  -> R.color.text_badge_jpg
                "db", "sqlite", "db3", "sqlite3"             -> R.color.text_badge_db
                "json"                                       -> R.color.text_badge_json
                "pdf"                                        -> R.color.text_badge_pdf
                "csv", "tsv"                                 -> R.color.text_badge_csv
                else                                -> R.color.text_secondary
            }
            holder.tvBadge.setTextColor(ctx.getColor(textColorRes))
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        // ──────────────────────────────────────────
        // Multi-select state
        // ──────────────────────────────────────────
        holder.checkbox.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
        holder.checkbox.isChecked  = item.isSelected
        if (item.isSelected) {
            holder.rowCard.setBackgroundColor(ctx.getColor(R.color.item_selected_bg))
        }
        // Don't override glass card bg when not selected — let XML handle it

        // ──────────────────────────────────────────
        // Click handlers — use rowCard for click target
        // ──────────────────────────────────────────
        holder.rowCard.setOnClickListener     { onItemClick(item) }
        holder.rowCard.setOnLongClickListener { onItemLongClick(item, it) }
    }

    /** Resolve a boolean theme attribute */
    private fun resolveThemeBool(ctx: android.content.Context, attr: Int): Boolean {
        val tv = TypedValue()
        return if (ctx.theme.resolveAttribute(attr, tv, true)) tv.data != 0 else false
    }

    fun enterMultiSelectMode() {
        isMultiSelectMode = true
        notifyDataSetChanged()
    }

    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        currentList.forEach { it.isSelected = false }
        notifyDataSetChanged()
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
        notifyDataSetChanged()
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
