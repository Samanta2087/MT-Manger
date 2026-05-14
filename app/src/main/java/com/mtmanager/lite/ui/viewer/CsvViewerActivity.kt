package com.mtmanager.lite.ui.viewer

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mtmanager.lite.databinding.ActivityCsvViewerBinding
import com.mtmanager.lite.utils.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CSV/TSV viewer that looks and feels like Google Sheets:
 *  - Green toolbar
 *  - Frozen column-letter header (A, B, C…) scrolls in sync horizontally
 *  - Row number column as first cell of every row
 *  - Light theme: white rows, gray alternating, thin grid lines
 *  - True 2-D scrolling: vertical (NestedScrollView) + horizontal (HorizontalScrollView)
 */
class CsvViewerActivity : AppCompatActivity() {

    companion object { const val EXTRA_FILE_PATH = "extra_file_path" }

    private lateinit var binding: ActivityCsvViewerBinding

    // Column widths computed from data, shared between header and rows
    private lateinit var colWidths: IntArray

    // Row-number column width (fixed)
    private var rowNumWidth = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCsvViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val file = File(intent.getStringExtra(EXTRA_FILE_PATH) ?: run { finish(); return })
        binding.tvCsvTitle.text = file.name
        binding.btnCsvBack.setOnClickListener { finish() }
        binding.tvRowCount.text = "Loading…"

        val delimiter = if (file.extension.lowercase() == "tsv") '\t' else ','

        // Sync header scroll with content scroll
        binding.nestedScroll.setOnScrollChangeListener { _, _, _, _, _ -> }
        binding.hsvContent.viewTreeObserver.addOnScrollChangedListener {
            binding.hsvHeader.scrollTo(binding.hsvContent.scrollX, 0)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val rows = parseCsv(file, delimiter)
            colWidths = computeColWidths(rows)
            withContext(Dispatchers.Main) {
                binding.tvRowCount.text = "${rows.size} rows · ${colWidths.size} cols"
                buildHeaderRow(colWidths.size)
                buildDataTable(rows)
            }
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private fun parseCsv(file: File, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        file.bufferedReader().use { br ->
            br.forEachLine { line ->
                if (line.isNotBlank()) {
                    rows.add(parseLine(line, delimiter))
                    if (rows.size >= 1000) return@forEachLine
                }
            }
        }
        return rows
    }

    private fun parseLine(line: String, delimiter: Char): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuote = false
        for (c in line) when {
            c == '"' -> inQuote = !inQuote
            c == delimiter && !inQuote -> { fields.add(sb.toString()); sb.clear() }
            else -> sb.append(c)
        }
        fields.add(sb.toString())
        return fields
    }

    private fun computeColWidths(rows: List<List<String>>): IntArray {
        val density = resources.displayMetrics.density
        val minPx = (80  * density).toInt()
        val maxPx = (200 * density).toInt()
        val padPx = (28  * density).toInt()
        val numCols = rows.maxOfOrNull { it.size } ?: 0
        val widths = IntArray(numCols) { minPx }
        val paint  = Paint().apply { textSize = 13f * density }
        val sample = if (rows.size > 100) rows.take(100) else rows
        for (row in sample) {
            for ((col, cell) in row.withIndex()) {
                val w = (paint.measureText(cell) + padPx).toInt().coerceAtMost(maxPx)
                if (w > widths[col]) widths[col] = w
            }
        }
        rowNumWidth = (48 * density).toInt()
        return widths
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /** Builds the frozen column-letter header (A, B, C…). */
    private fun buildHeaderRow(numCols: Int) {
        val density = resources.displayMetrics.density
        val container = binding.headerRow
        container.removeAllViews()

        // Row-number placeholder (blank, same width as row-number column)
        container.addView(makeCell(
            text = "",
            width = rowNumWidth,
            bgColor = 0xFFEEEEEE.toInt(),
            textColor = 0xFF666666.toInt(),
            bold = false,
            textSize = 11f
        ))

        for (col in 0 until numCols) {
            // Divider
            container.addView(makeDivider(density, horizontal = false))
            // Letter header: A, B, C … Z, AA, AB …
            container.addView(makeCell(
                text = columnLabel(col),
                width = colWidths[col],
                bgColor = 0xFFEEEEEE.toInt(),
                textColor = 0xFF444444.toInt(),
                bold = false,
                textSize = 11.5f,
                gravity = Gravity.CENTER
            ))
        }
    }

    /** Builds all data rows with row numbers + cells. */
    private fun buildDataTable(rows: List<List<String>>) {
        val density = resources.displayMetrics.density
        val table = binding.tableContainer
        table.removeAllViews()

        for ((rowIdx, row) in rows.withIndex()) {
            val isHeaderRow = rowIdx == 0

            // Row container
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(
                    when {
                        isHeaderRow  -> 0xFFF1F8E9.toInt()  // soft green tint for row 1
                        rowIdx % 2 == 0 -> 0xFFFFFFFF.toInt()
                        else          -> 0xFFF5F5F5.toInt()
                    }
                )
            }

            // Row number cell
            rowLayout.addView(makeCell(
                text = "$rowIdx",
                width = rowNumWidth,
                bgColor = 0xFFEEEEEE.toInt(),
                textColor = 0xFF888888.toInt(),
                bold = false,
                textSize = 11f,
                gravity = Gravity.CENTER
            ))

            for (col in colWidths.indices) {
                rowLayout.addView(makeDivider(density, horizontal = false))
                rowLayout.addView(makeCell(
                    text = row.getOrNull(col) ?: "",
                    width = colWidths[col],
                    bgColor = android.graphics.Color.TRANSPARENT,
                    textColor = if (isHeaderRow) 0xFF1A6B2D.toInt() else 0xFF212121.toInt(),
                    bold = isHeaderRow,
                    textSize = if (isHeaderRow) 12.5f else 12f
                ))
            }

            table.addView(rowLayout)
            // Horizontal row divider
            table.addView(makeDivider(density, horizontal = true))
        }
    }

    // ── View factories ────────────────────────────────────────────────────────

    private fun makeCell(
        text: String,
        width: Int,
        bgColor: Int,
        textColor: Int,
        bold: Boolean,
        textSize: Float,
        gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL
    ): TextView {
        val density = resources.displayMetrics.density
        val padH = (8 * density).toInt()
        val padV = (6 * density).toInt()
        return TextView(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(width,
                ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(padH, padV, padH, padV)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            this.gravity = gravity
            this.textSize = textSize
            setTextColor(textColor)
            if (bgColor != android.graphics.Color.TRANSPARENT)
                setBackgroundColor(bgColor)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun makeDivider(density: Float, horizontal: Boolean): View {
        return View(this).apply {
            layoutParams = if (horizontal) {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1)
            } else {
                LinearLayout.LayoutParams(1,
                    ViewGroup.LayoutParams.MATCH_PARENT)
            }
            setBackgroundColor(0xFFBDBDBD.toInt())
        }
    }

    /** Converts 0→A, 1→B … 25→Z, 26→AA … like spreadsheets. */
    private fun columnLabel(col: Int): String {
        var n = col
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }
}
