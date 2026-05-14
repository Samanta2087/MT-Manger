package com.mtmanager.lite.ui.editor

import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    val onSelectionChanged: (hasSelection: Boolean) -> Unit = {}
) : RecyclerView.Adapter<LineAdapter.LineVH>() {

    var fontSize: Float = 13f
    var isReadOnly: Boolean = false
    var fileExtension: String = ""
    var isSelectAllActive: Boolean = false

    // Track last focused EditText for symbol bar insertion
    var lastFocusedEdit: SelectionEditText? = null
    var lastFocusedLine: Int = 0

    private lateinit var rv: RecyclerView

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        rv = recyclerView
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────
    inner class LineVH(val b: ItemEditorLineBinding) : RecyclerView.ViewHolder(b.root) {
        var isBinding = false
        var watcher: TextWatcher? = null
        var highlightJob: Job? = null      // cancel stale highlight on rebind

        fun removeWatcher() {
            watcher?.let { b.etLine.removeTextChangedListener(it) }
            watcher = null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        LineVH(ItemEditorLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    // ── Bind ──────────────────────────────────────────────────────────────────
    override fun onBindViewHolder(holder: LineVH, position: Int) {
        holder.highlightJob?.cancel()
        holder.isBinding = true
        holder.removeWatcher()

        val lineText = lines[position]

        holder.b.tvLineNum.text     = (position + 1).toString()
        holder.b.tvLineNum.textSize = fontSize
        holder.b.etLine.textSize    = fontSize
        holder.b.etLine.isEnabled   = !isReadOnly && !isSelectAllActive

        // Visual select-all highlight
        if (isSelectAllActive) {
            holder.b.root.setBackgroundColor(0x33264F78)  // semi-transparent blue
            holder.b.activeLineHighlight.visibility = View.GONE
            holder.b.etLine.setText(lineText)
            holder.b.etLine.setSelection(0, lineText.length)
        } else {
            holder.b.root.background = null
            holder.b.activeLineHighlight.visibility = View.GONE
            holder.b.etLine.setText(lineText)
        }

        // No InputFilter — newlines are handled in afterTextChanged (soft-keyboard Enter)
        holder.b.etLine.filters = arrayOf()

        holder.isBinding = false

        // ── Async syntax highlight ─────────────────────────────────────────────
        applySyntaxHighlight(holder, lineText)

        // ── TextWatcher: sync model, re-highlight, handle soft-keyboard Enter ────────
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (holder.isBinding) return
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val raw = s?.toString() ?: ""

                // Soft keyboard Enter → \n in text → split the line
                val nlIdx = raw.indexOf('\n')
                if (nlIdx >= 0) {
                    val before = raw.substring(0, nlIdx)
                    val after  = raw.substring(nlIdx + 1)
                    holder.isBinding = true
                    s?.replace(0, s.length, before)   // fix this view's text
                    holder.isBinding = false
                    lines[pos] = before
                    lines.add(pos + 1, after)
                    notifyItemChanged(pos)
                    notifyItemInserted(pos + 1)
                    onChanged()
                    rv.postDelayed({
                        rv.scrollToPosition(pos + 1)
                        (rv.findViewHolderForAdapterPosition(pos + 1) as? LineVH)
                            ?.b?.etLine?.apply { requestFocus(); setSelection(0) }
                    }, 50)
                    return
                }

                lines[pos] = raw
                onChanged()
                applySyntaxHighlight(holder, raw)
            }
        }
        holder.watcher = watcher
        holder.b.etLine.addTextChangedListener(watcher)

        // ── Focus tracking + active line highlight ─────────────────────────────
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

        // ── Selection tracking via SelectionEditText callback ─────────────────
        //    The system floating toolbar is already suppressed inside SelectionEditText.
        holder.b.etLine.onSelectionChangedListener = { hasSelection ->
            onSelectionChanged(hasSelection)
        }

        // ── Key listener: Enter = split, Backspace at col 0 = merge ──────────
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
    }

    // ── Syntax highlight (async, cancellable per ViewHolder) ─────────────────
    private fun applySyntaxHighlight(holder: LineVH, text: String) {
        if (fileExtension.isEmpty()) return
        holder.highlightJob?.cancel()
        holder.highlightJob = CoroutineScope(Dispatchers.Default).launch {
            val spannable = SyntaxHighlighter.highlightLine(text, fileExtension)
            withContext(Dispatchers.Main) {
                // Guard: bail if ViewHolder was rebound to different line while we computed
                val editable = holder.b.etLine.editableText
                if (editable == null || editable.toString() != text) return@withContext

                // Remove old color spans, then inject new ones directly.
                // NOTE: removeSpan/setSpan do NOT trigger afterTextChanged — safe, no loop.
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

    // ── Recycle: cancel any in-flight highlight, clean listeners ─────────────
    override fun onViewRecycled(holder: LineVH) {
        // If this ViewHolder held the active selection, dismiss the popup before
        // clearing the listener — otherwise the popup stays open permanently.
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
