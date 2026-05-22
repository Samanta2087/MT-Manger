package com.fyloxen.app.ui.editor

import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText

class SelectionEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val imm by lazy {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    var onSelectionChangedListener: ((hasSelection: Boolean) -> Unit)? = null

    /**
     * Called when multi-line text is pasted (from IME, Ctrl+V, or context menu).
     * Return true if handled at model level — the text will NOT be written to the EditText.
     */
    var onPaste: ((pastedText: String) -> Boolean)? = null

override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val hasSelection = selStart != selEnd
        onSelectionChangedListener?.invoke(hasSelection)

        if (hasSelection && !isInSelectAllMode && windowToken != null) {
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    var isInSelectAllMode: Boolean = false

    // Intercept system paste (context menu)
    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip ?: return super.onTextContextMenuItem(id)
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(context).toString()
                if (text.contains('\n')) {
                    val handled = onPaste?.invoke(text) ?: false
                    if (handled) return true
                }
            }
        }
        return super.onTextContextMenuItem(id)
    }

    // Intercept IME paste (Gboard clipboard, Samsung clipboard, etc.)
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        return PasteInterceptConnection(ic, this)
    }

    private val suppressCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return true
        }
        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu?.clear()
            return true
        }
        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
        override fun onDestroyActionMode(mode: ActionMode?) {}
    }

    init {
        customSelectionActionModeCallback = suppressCallback
    }
}

/**
 * Wraps InputConnection to intercept commitText containing newlines.
 * This catches Gboard/IME paste buttons that bypass onTextContextMenuItem.
 */
class PasteInterceptConnection(
    delegate: InputConnection,
    private val editText: SelectionEditText
) : InputConnectionWrapper(delegate, false) {

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (text != null && text.contains('\n')) {
            val handled = editText.onPaste?.invoke(text.toString()) ?: false
            if (handled) return true
        }
        return super.commitText(text, newCursorPosition)
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (text != null && text.contains('\n')) {
            val handled = editText.onPaste?.invoke(text.toString()) ?: false
            if (handled) return true
        }
        return super.setComposingText(text, newCursorPosition)
    }
}