package com.fyloxen.app.ui.editor

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fyloxen.app.databinding.ItemEditorLineBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreamingLineAdapter(
    private val engine: LargeFileEditorEngine,
    private val scope: CoroutineScope,
    var isReadOnly: Boolean = false,
    var fontSize: Float = 13f,
    private val onChanged: () -> Unit,
    private val onCursorMoved: (lineIdx: Int, col: Int) -> Unit,
    private val onSelectionChanged: (hasSelection: Boolean) -> Unit
) : RecyclerView.Adapter<StreamingLineAdapter.LineVH>() {

    var lastFocusedEdit: SelectionEditText? = null

    inner class LineVH(val b: ItemEditorLineBinding) : RecyclerView.ViewHolder(b.root) {
        /** The position this VH is currently bound to. Written only on the main thread. */
        @Volatile var boundPosition: Int = RecyclerView.NO_ID.toInt()
        var loadJob: Job? = null
        var watcher: TextWatcher? = null

        fun detachWatcher() {
            watcher?.let { b.etLine.removeTextChangedListener(it) }
            watcher = null
        }

        /** Cancel the in-flight load and reset the view to a safe blank state. */
        fun cancelLoad() {
            loadJob?.cancel()
            loadJob = null
            detachWatcher()
            // Clear listeners that reference the old position
            b.etLine.onPaste = null
            b.etLine.onSelectionChangedListener = null
            b.etLine.setOnFocusChangeListener(null)
            b.etLine.setOnClickListener(null)
            b.etLine.setOnLongClickListener(null)
        }
    }

    override fun getItemCount(): Int = engine.lineCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineVH {
        val b = ItemEditorLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LineVH(b)
    }

    override fun onBindViewHolder(holder: LineVH, position: Int) {
        // ── 1. Cancel any previous load for this VH BEFORE updating position ──
        holder.cancelLoad()

        // ── 2. Stamp the new position atomically ──────────────────────────────
        holder.boundPosition = position

        // ── 3. Static parts ───────────────────────────────────────────────────
        holder.b.tvLineNum.text     = (position + 1).toString()
        holder.b.tvLineNum.textSize = fontSize
        holder.b.etLine.textSize    = fontSize
        holder.b.etLine.isEnabled   = !isReadOnly
        holder.b.etLine.setText("…")

        // ── 4. Async load — snapshot 'position' into local val for closure ────
        val targetPos = position
        holder.loadJob = scope.launch {
            // Read on IO thread; engine.getLine is @Synchronized so safe
            val content = withContext(Dispatchers.IO) {
                try { engine.getLine(targetPos) } catch (e: Exception) { "" }
            }
            // Only apply if the job wasn't cancelled AND the VH still shows this position
            if (isActive && holder.boundPosition == targetPos) {
                holder.b.etLine.setText(content)
                attachWatcher(holder, targetPos)
            }
        }
    }

    private fun attachWatcher(holder: LineVH, position: Int) {
        // Guard: only attach if VH still shows this position (fast-scroll safety)
        if (holder.boundPosition != position) return

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Check position again inside the callback — VH may have been recycled
                if (holder.boundPosition == position) {
                    engine.setLine(position, s?.toString() ?: "")
                    onChanged()
                }
            }
        }
        holder.watcher = watcher
        holder.b.etLine.addTextChangedListener(watcher)

        holder.b.etLine.onPaste = { pastedText ->
            if (holder.boundPosition == position) {
                val parts = pastedText.split("\n")
                for ((idx, part) in parts.withIndex()) {
                    engine.setLine(position + idx, part)
                }
                holder.b.etLine.removeTextChangedListener(watcher)
                holder.b.etLine.setText(parts[0])
                holder.b.etLine.addTextChangedListener(watcher)
                onChanged()
            }
            true
        }

        holder.b.etLine.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && holder.boundPosition == position) {
                lastFocusedEdit = holder.b.etLine
                onCursorMoved(position, holder.b.etLine.selectionStart)
            }
        }
        holder.b.etLine.setOnClickListener {
            if (holder.boundPosition == position)
                onCursorMoved(position, holder.b.etLine.selectionStart)
        }
        holder.b.etLine.setOnLongClickListener {
            if (holder.boundPosition == position) {
                holder.b.etLine.postDelayed({
                    if (holder.boundPosition == position) {
                        val hasSel = holder.b.etLine.selectionStart != holder.b.etLine.selectionEnd
                        onSelectionChanged(hasSel)
                    }
                }, 150)
            }
            false
        }
        holder.b.etLine.onSelectionChangedListener = { hasSelection ->
            if (holder.boundPosition == position) onSelectionChanged(hasSelection)
        }
    }

    override fun onViewRecycled(holder: LineVH) {
        // Mark as unbound first so any in-flight callback sees an invalid position
        holder.boundPosition = RecyclerView.NO_ID.toInt()
        holder.cancelLoad()
    }

    /** Called when VH goes off-screen. Same as recycled — kill load immediately. */
    override fun onViewDetachedFromWindow(holder: LineVH) {
        holder.loadJob?.cancel()
    }

    fun getContent(): String = buildString {
        for (i in 0 until engine.lineCount) {
            if (i > 0) append('\n')
            append(engine.getLine(i))
        }
    }
}