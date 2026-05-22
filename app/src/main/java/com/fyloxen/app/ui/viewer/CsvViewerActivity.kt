package com.fyloxen.app.ui.viewer

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyloxen.app.databinding.ActivityCsvViewerBinding
import com.fyloxen.app.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * CSV / TSV viewer — Google Sheets style.
 *
 * Big-file fix (v2):
 *  - Parsing: lineSequence().take(ROW_LIMIT) truly stops reading after the limit —
 *    the rest of a huge file is never touched.
 *  - Rendering: RecyclerView adapter — only visible rows exist in RAM, no matter how
 *    many rows are loaded (previous code inflated ALL rows at once → OOM / freeze).
 *  - Coroutine: tied to lifecycleScope — auto-cancelled if Activity is destroyed.
 *  - Loading spinner visible during parse.
 */
class CsvViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH    = "extra_file_path"
        private const val ROW_LIMIT  = 5_000   // hard cap: show at most this many rows
        private const val COL_SAMPLE = 100     // rows sampled for column-width estimation
    }

    private lateinit var binding: ActivityCsvViewerBinding
    private lateinit var colWidths: IntArray
    private var rowNumWidth = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCsvViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        if (path == null) { finish(); return }
        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            binding.tvRowCount.text = "Cannot read file"
            return
        }

        binding.tvCsvTitle.text = file.name
        binding.btnCsvBack.setOnClickListener { finish() }
        binding.tvRowCount.text = "Loading…"
        binding.progressBar.visibility = View.VISIBLE

        val delimiter = if (file.extension.lowercase() == "tsv") '\t' else ','

        // Sync frozen column header with the content scroll
        binding.hsvContent.viewTreeObserver.addOnScrollChangedListener {
            binding.hsvHeader.scrollTo(binding.hsvContent.scrollX, 0)
        }

        lifecycleScope.launch {
            // ── Parse off main thread ─────────────────────────────────────────
            val result = withContext(Dispatchers.IO) {
                runCatching { parseCsv(file, delimiter) }
            }

            binding.progressBar.visibility = View.GONE

            result.onFailure { err ->
                binding.tvRowCount.text = "Error: ${err.message}"
                return@launch
            }

            val rows = result.getOrThrow()
            if (rows.isEmpty()) {
                binding.tvRowCount.text = "Empty file"
                return@launch
            }

            colWidths   = computeColWidths(rows)
            val capped  = rows.size >= ROW_LIMIT
            binding.tvRowCount.text = buildString {
                append("${rows.size}")
                if (capped) append("+")
                append(" rows · ${colWidths.size} cols")
                if (capped) append(" (capped at $ROW_LIMIT)")
            }

            // ── Build frozen header ───────────────────────────────────────────
            buildHeaderRow(colWidths.size)

            // ── Attach virtualized row adapter ────────────────────────────────
            binding.rvRows.apply {
                layoutManager = LinearLayoutManager(this@CsvViewerActivity)
                adapter       = CsvRowAdapter(rows, colWidths, rowNumWidth,
                                              resources.displayMetrics.density)
                setItemViewCacheSize(40)
            }
        }
    }

    // ── CSV Parsing ───────────────────────────────────────────────────────────

    /**
     * Reads at most [ROW_LIMIT] rows using lineSequence().take() so the reader
     * stops immediately after the limit — no full-file scan for huge files.
     */
    private fun parseCsv(file: File, delimiter: Char): List<List<String>> =
        BufferedReader(FileReader(file)).use { br ->
            br.lineSequence()
              .filter { it.isNotBlank() }
              .take(ROW_LIMIT)
              .map    { parseLine(it, delimiter) }
              .toList()
        }

    private fun parseLine(line: String, delimiter: Char): List<String> {
        val fields  = mutableListOf<String>()
        val sb      = StringBuilder()
        var inQuote = false
        for (c in line) when {
            c == '"'                  -> inQuote = !inQuote
            c == delimiter && !inQuote -> { fields.add(sb.toString()); sb.clear() }
            else                      -> sb.append(c)
        }
        fields.add(sb.toString())
        return fields
    }

    private fun computeColWidths(rows: List<List<String>>): IntArray {
        val dp      = resources.displayMetrics.density
        val minPx   = (80  * dp).toInt()
        val maxPx   = (200 * dp).toInt()
        val padPx   = (28  * dp).toInt()
        val numCols = rows.maxOfOrNull { it.size } ?: 0
        val widths  = IntArray(numCols) { minPx }
        val paint   = Paint().apply { textSize = 13f * dp }
        val sample  = if (rows.size > COL_SAMPLE) rows.take(COL_SAMPLE) else rows
        for (row in sample) {
            for ((col, cell) in row.withIndex()) {
                val w = (paint.measureText(cell) + padPx).toInt().coerceAtMost(maxPx)
                if (w > widths[col]) widths[col] = w
            }
        }
        rowNumWidth = (48 * dp).toInt()
        return widths
    }

    // ── Frozen column-letter header ───────────────────────────────────────────

    private fun buildHeaderRow(numCols: Int) {
        val dp        = resources.displayMetrics.density
        val container = binding.headerRow
        container.removeAllViews()

        container.addView(makeCell(this, "", rowNumWidth,
            0xFFEEEEEE.toInt(), 0xFF666666.toInt(), bold = false, 11f, dp = dp))

        for (col in 0 until numCols) {
            container.addView(makeDivider(this, dp, horizontal = false))
            container.addView(makeCell(this, columnLabel(col), colWidths[col],
                0xFFEEEEEE.toInt(), 0xFF444444.toInt(),
                bold = false, 11.5f, gravity = Gravity.CENTER, dp = dp))
        }
    }

    /** 0→A, 1→B … 25→Z, 26→AA … */
    private fun columnLabel(col: Int): String {
        var n = col
        val sb = StringBuilder()
        do { sb.insert(0, 'A' + n % 26); n = n / 26 - 1 } while (n >= 0)
        return sb.toString()
    }
}

// ── Shared view factories (package-level so adapter can use them) ─────────────

internal fun makeCell(
    ctx: Context,
    text: String,
    width: Int,
    bgColor: Int,
    textColor: Int,
    bold: Boolean,
    textSize: Float,
    gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL,
    dp: Float = ctx.resources.displayMetrics.density
): TextView {
    val padH = (8 * dp).toInt()
    val padV = (6 * dp).toInt()
    return TextView(ctx).apply {
        this.text = text
        layoutParams = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        setPadding(padH, padV, padH, padV)
        maxLines      = 2
        ellipsize     = android.text.TextUtils.TruncateAt.END
        this.gravity  = gravity
        this.textSize = textSize
        setTextColor(textColor)
        if (bgColor != Color.TRANSPARENT) setBackgroundColor(bgColor)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
}

internal fun makeDivider(ctx: Context, dp: Float, horizontal: Boolean): View =
    View(ctx).apply {
        layoutParams = if (horizontal)
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        else
            LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(0xFFBDBDBD.toInt())
    }

// ── RecyclerView row adapter — only visible rows in memory ───────────────────

/**
 * Replaces the old approach of inflating ALL rows as LinearLayouts at once.
 * With 5000 rows × 20 cols, the old code created 100 000+ Views synchronously
 * on the main thread → freeze / OOM.  RecyclerView recycles ~15 visible rows.
 */
class CsvRowAdapter(
    private val rows:        List<List<String>>,
    private val colWidths:   IntArray,
    private val rowNumWidth: Int,
    private val dp:          Float
) : RecyclerView.Adapter<CsvRowAdapter.RowVH>() {

    inner class RowVH(val container: LinearLayout) : RecyclerView.ViewHolder(container)

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowVH {
        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return RowVH(row)
    }

    override fun onBindViewHolder(holder: RowVH, position: Int) {
        val dataRow     = rows[position]
        val isHeader    = position == 0
        val container   = holder.container
        val ctx         = container.context

        // ── Build or reuse child views ────────────────────────────────────────
        // Expected: [rowNum] + N × [divider, cell]
        val expected = 1 + colWidths.size * 2
        if (container.childCount != expected) {
            container.removeAllViews()

            // Row number
            container.addView(makeCell(ctx, "${position + 1}", rowNumWidth,
                0xFFEEEEEE.toInt(), 0xFF888888.toInt(), bold = false, 11f,
                gravity = Gravity.CENTER, dp = dp))

            for (col in colWidths.indices) {
                container.addView(makeDivider(ctx, dp, horizontal = false))
                container.addView(makeCell(ctx,
                    text      = dataRow.getOrNull(col) ?: "",
                    width     = colWidths[col],
                    bgColor   = Color.TRANSPARENT,
                    textColor = if (isHeader) 0xFF1A6B2D.toInt() else 0xFF212121.toInt(),
                    bold      = isHeader,
                    textSize  = if (isHeader) 12.5f else 12f,
                    dp        = dp
                ))
            }
        } else {
            // Fast-path: update text only — no View allocation
            (container.getChildAt(0) as? TextView)?.text = "${position + 1}"
            for (col in colWidths.indices) {
                val cellIdx = 1 + col * 2 + 1
                (container.getChildAt(cellIdx) as? TextView)?.apply {
                    text = dataRow.getOrNull(col) ?: ""
                    setTextColor(if (isHeader) 0xFF1A6B2D.toInt() else 0xFF212121.toInt())
                    setTypeface(null, if (isHeader) Typeface.BOLD else Typeface.NORMAL)
                }
            }
        }

        container.setBackgroundColor(when {
            isHeader         -> 0xFFF1F8E9.toInt()
            position % 2 == 0 -> 0xFFFFFFFF.toInt()
            else             -> 0xFFF5F5F5.toInt()
        })
    }
}
