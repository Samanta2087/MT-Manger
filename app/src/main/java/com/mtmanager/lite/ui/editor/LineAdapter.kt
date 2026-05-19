package com.mtmanager.lite.ui.editor

import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.mtmanager.lite.databinding.ItemEditorLineBinding
import com.mtmanager.lite.utils.SyntaxHighlighter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LineAdapter(
    val lines: MutableList<String>,
    private val onChanged: () -> Unit,
    private val onCursorMoved: (line: Int, col: Int) -> Unit,
    val onSelectionChanged: (hasSelection: Boolean) -> Unit = {},
    val onLongPress: (() -> Unit)? = null
) : RecyclerView.Adapter<LineAdapter.LineVH>() {

    var fontSize: Float = 13f
    var isReadOnly: Boolean = false
    var fileExtension: String = ""
    var isSelectAllActive: Boolean = false

    var lastFocusedEdit: SelectionEditText? = null
    var lastFocusedLine: Int = 0

    private lateinit var rv: RecyclerView

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        rv = recyclerView
    }

    inner class LineVH(val b: ItemEditorLineBinding) : RecyclerView.ViewHolder(b.root) {
        var isBinding = false
        var watcher: TextWatcher? = null
        var highlightJob: Job? = null

        fun removeWatcher() {
            watcher?.let { b.etLine.removeTextChangedListener(it) }
            watcher = null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        LineVH(ItemEditorLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: LineVH, position: Int) {
        holder.highlightJob?.cancel()
        holder.isBinding = true
        holder.removeWatcher()

        val lineText = lines[position]

        holder.b.tvLineNum.text     = (position + 1).toString()
        holder.b.tvLineNum.textSize = fontSize
        holder.b.etLine.textSize    = fontSize
        holder.b.etLine.isEnabled   = !isReadOnly

        if (isSelectAllActive) {
            holder.b.root.setBackgroundColor(0x33264F78)
            holder.b.activeLineHighlight.visibility = View.GONE
            holder.b.etLine.setText(lineText)
            holder.b.etLine.setSelection(0, lineText.length)
        } else {
            holder.b.root.background = null
            holder.b.activeLineHighlight.visibility = View.GONE
            holder.b.etLine.setText(lineText)
        }

        holder.b.etLine.filters = arrayOf()
        holder.isBinding = false

        applySyntaxHighlight(holder, lineText)

        // ── Intercept system multi-line paste ──────────────────────────────────
        holder.b.etLine.onPaste = { pastedText ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) {
                false
            } else if (isSelectAllActive) {
                val newLines = pastedText.split("\n").toMutableList()
                lines.clear()
                lines.addAll(if (newLines.isEmpty()) mutableListOf("") else newLines)
                isSelectAllActive = false
                notifyDataSetChanged()
                onChanged()
                true
            } else {
                val et = holder.b.etLine
                val selStart = et.selectionStart.coerceAtLeast(0).coerceAtMost(lineText.length)
                val selEnd = et.selectionEnd.coerceAtLeast(selStart).coerceAtMost(lineText.length)
                insertMultiLine(pos, pastedText, selStart, selEnd)
                true
            }
        }

        // ── TextWatcher ────────────────────────────────────────────────────────
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (holder.isBinding) return
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val raw = s?.toString() ?: ""

                // Handle newlines from soft-keyboard Enter or IME that
                // somehow bypassed onPaste (shouldn't happen for paste, but
                // Enter key still comes through here)
                val nlIdx = raw.indexOf('\n')
                if (nlIdx >= 0) {
                    val parts = raw.split("\n")
                    holder.isBinding = true
                    s?.replace(0, s.length, parts[0])
                    holder.isBinding = false
                    lines[pos] = parts[0]
                    for (i in (parts.size - 1) downTo 1) {
                        lines.add(pos + 1, parts[i])
                    }
                    notifyDataSetChanged()
                    onChanged()
                    val focusPos = (pos + parts.size - 1).coerceAtMost(lines.size - 1)
                    val cursorInLast = parts[parts.size - 1].length
                    rv.postDelayed({
                        rv.scrollToPosition(focusPos)
                        (rv.findViewHolderForAdapterPosition(focusPos) as? LineVH)
                            ?.b?.etLine?.apply { requestFocus(); setSelection(cursorInLast) }
                    }, 150)
                    return
                }

                lines[pos] = raw
                onChanged()
                applySyntaxHighlight(holder, raw)
            }
        }
        holder.watcher = watcher
        holder.b.etLine.addTextChangedListener(watcher)

        // ── Focus tracking ────────────────────────────────────────────────────
        holder.b.etLine.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                lastFocusedEdit = holder.b.etLine
                lastFocusedLine = holder.bindingAdapterPosition
                onCursorMoved(holder.bindingAdapterPosition, holder.b.etLine.selectionStart)
                holder.b.root.setBackgroundColor(holder.itemView.context.getColor(com.mtmanager.lite.R.color.editor_active_line))
                holder.b.activeLineHighlight.visibility = View.VISIBLE
            } else {
                holder.b.root.background = null
                holder.b.activeLineHighlight.visibility = View.GONE
                onSelectionChanged(false)
            }
        }

        holder.b.etLine.onSelectionChangedListener = { hasSelection ->
            onSelectionChanged(hasSelection)
        }

        // ── Key listener ───────────────────────────────────────────────────────
        holder.b.etLine.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnKeyListener false

            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    val cur  = holder.b.etLine.selectionStart
                    val text = lines[pos]
                    lines[pos] = text.substring(0, cur)
                    lines.add(pos + 1, text.substring(cur))
                    notifyItemChanged(pos)
                    notifyItemInserted(pos + 1)
                    onChanged()
                    rv.postDelayed({
                        rv.scrollToPosition(pos + 1)
                        (rv.findViewHolderForAdapterPosition(pos + 1) as? LineVH)
                            ?.b?.etLine?.apply { requestFocus(); setSelection(0) }
                    }, 50)
                    true
                }
                KeyEvent.KEYCODE_DEL -> {
                    if (pos > 0 && holder.b.etLine.selectionStart == 0
                        && holder.b.etLine.selectionEnd == 0) {
                        val prevLen = lines[pos - 1].length
                        lines[pos - 1] += lines[pos]
                        lines.removeAt(pos)
                        notifyItemChanged(pos - 1)
                        notifyItemRemoved(pos)
                        onChanged()
                        rv.post {
                            (rv.findViewHolderForAdapterPosition(pos - 1) as? LineVH)
                                ?.b?.etLine?.apply { requestFocus(); setSelection(prevLen) }
                        }
                        true
                    } else false
                }
                else -> false
            }
        }

        // ── Long-press on EditText → show action bar (so Paste is accessible) ──
        holder.b.etLine.setOnLongClickListener {
            onLongPress?.invoke()
            false   // let the system handle selection too
        }
    }

    // ── Model-level multi-line insert (used by onPaste callback and popup Paste button) ──
    fun insertMultiLine(pos: Int, text: String, selStart: Int, selEnd: Int) {
        val currentLine = lines[pos]
        val before = currentLine.substring(0, selStart.coerceAtMost(currentLine.length))
        val after = currentLine.substring(selEnd.coerceAtMost(currentLine.length))
        val pasteLines = text.split("\n")

        lines[pos] = before + pasteLines[0]
        for (i in (pasteLines.size - 1) downTo 1) {
            lines.add(pos + 1, pasteLines[i] + if (i == pasteLines.size - 1) after else "")
        }
        notifyDataSetChanged()
        onChanged()

        val focusPos = (pos + pasteLines.size - 1).coerceAtMost(lines.size - 1)
        val cursorInLast = (pasteLines[pasteLines.size - 1] + after).length
        rv.postDelayed({
            rv.scrollToPosition(focusPos)
            (rv.findViewHolderForAdapterPosition(focusPos) as? LineVH)
                ?.b?.etLine?.apply { requestFocus(); setSelection(cursorInLast) }
        }, 150)
    }

    private fun applySyntaxHighlight(holder: LineVH, text: String) {
        if (fileExtension.isEmpty()) return
        holder.highlightJob?.cancel()
        holder.highlightJob = CoroutineScope(Dispatchers.Default).launch {
            val spannable = SyntaxHighlighter.highlightLine(text, fileExtension)
            withContext(Dispatchers.Main) {
                val editable = holder.b.etLine.editableText
                if (editable == null || editable.toString() != text) return@withContext

                editable.getSpans(0, editable.length, android.text.style.ForegroundColorSpan::class.java)
                    .forEach { editable.removeSpan(it) }

                spannable.getSpans(0, spannable.length, android.text.style.ForegroundColorSpan::class.java)
                    .forEach { span ->
                        val s = spannable.getSpanStart(span)
                        val e = spannable.getSpanEnd(span)
                        if (s >= 0 && e <= editable.length && s < e) {
                            editable.setSpan(
                                android.text.style.ForegroundColorSpan(
                                    (span as android.text.style.ForegroundColorSpan).foregroundColor
                                ),
                                s, e, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
            }
        }
    }

    override fun onViewRecycled(holder: LineVH) {
        if (holder.b.etLine === lastFocusedEdit) {
            onSelectionChanged(false)
            lastFocusedEdit = null
        }
        holder.highlightJob?.cancel()
        holder.highlightJob = null
        holder.removeWatcher()
        holder.b.etLine.setOnKeyListener(null)
        holder.b.etLine.onFocusChangeListener = null
        holder.b.etLine.onSelectionChangedListener = null
        holder.b.etLine.onPaste = null
        holder.b.etLine.setOnLongClickListener(null)
        super.onViewRecycled(holder)
    }

    override fun getItemCount() = lines.size

    fun getContent() = lines.joinToString("\n")

    fun selectAll() {
        isSelectAllActive = true
        notifyDataSetChanged()
    }

    fun clearSelectAll() {
        if (!isSelectAllActive) return
        isSelectAllActive = false
        notifyDataSetChanged()
    }
}