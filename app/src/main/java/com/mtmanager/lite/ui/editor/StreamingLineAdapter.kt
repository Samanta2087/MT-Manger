package com.mtmanager.lite.ui.editor

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.mtmanager.lite.databinding.ItemEditorLineBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RecyclerView adapter that renders lines from [LargeFileEditorEngine] on demand.
 *
 * - Each ViewHolder launches a coroutine to fetch its line from the engine (IO thread).
 * - The coroutine is cancelled when the ViewHolder is recycled (prevents stale writes).
 * - Content changes are written back to the engine's patchMap via a TextWatcher.
 */
class StreamingLineAdapter(
    private val engine: LargeFileEditorEngine,
    private val scope: CoroutineScope,
    var isReadOnly: Boolean = false,
    var fontSize: Float = 13f,
    private val onChanged: () -> Unit,
    private val onCursorMoved: (lineIdx: Int, col: Int) -> Unit,
    private val onSelectionChanged: (hasSelection: Boolean) -> Unit
) : RecyclerView.Adapter<StreamingLineAdapter.LineVH>() {

    var lastFocusedEdit: EditText? = null

    inner class LineVH(val b: ItemEditorLineBinding) : RecyclerView.ViewHolder(b.root) {
        var lineNum: Int = -1
        var loadJob: Job? = null
        var watcher: TextWatcher? = null

        fun detachWatcher() {
            watcher?.let { b.etLine.removeTextChangedListener(it) }
            watcher = null
        }
    }

    override fun getItemCount(): Int = engine.lineCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineVH {
        val b = ItemEditorLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LineVH(b)
    }

    override fun onBindViewHolder(holder: LineVH, position: Int) {
        // Cancel any previous load / watcher for this ViewHolder
        holder.loadJob?.cancel()
        holder.detachWatcher()
        holder.lineNum = position

        holder.b.tvLineNum.text     = (position + 1).toString()
        holder.b.tvLineNum.textSize = fontSize
        holder.b.etLine.textSize    = fontSize
        holder.b.etLine.isEnabled   = !isReadOnly
        holder.b.etLine.setText("…")   // placeholder while loading

        holder.loadJob = scope.launch {
            val content = withContext(Dispatchers.IO) { engine.getLine(position) }
            // Only apply if this ViewHolder hasn't been recycled to a different position
            if (holder.lineNum == position) {
                holder.b.etLine.setText(content)
                attachWatcher(holder, position)
            }
        }
    }

    private fun attachWatcher(holder: LineVH, position: Int) {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (holder.lineNum == position) {
                    engine.setLine(position, s?.toString() ?: "")
                    onChanged()
                }
            }
        }
        holder.watcher = watcher
        holder.b.etLine.addTextChangedListener(watcher)

        holder.b.etLine.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                lastFocusedEdit = holder.b.etLine
                onCursorMoved(position, holder.b.etLine.selectionStart)
            }
        }
        holder.b.etLine.setOnClickListener {
            onCursorMoved(position, holder.b.etLine.selectionStart)
        }
        holder.b.etLine.setOnLongClickListener {
            // Notify after selection established (short delay)
            holder.b.etLine.postDelayed({
                val hasSel = holder.b.etLine.selectionStart != holder.b.etLine.selectionEnd
                onSelectionChanged(hasSel)
            }, 150)
            false
        }
    }

    override fun onViewRecycled(holder: LineVH) {
        holder.loadJob?.cancel()
        holder.loadJob = null
        holder.detachWatcher()
    }

    /** Retrieve the text currently shown in a specific on-screen ViewHolder (for copy/paste). */
    fun getContent(): String = buildString {
        for (i in 0 until engine.lineCount) {
            if (i > 0) append('\n')
            append(engine.getLine(i))
        }
    }
}
