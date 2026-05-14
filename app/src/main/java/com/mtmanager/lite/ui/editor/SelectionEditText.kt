package com.mtmanager.lite.ui.editor

import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText

/**
 * EditText that:
 *  1. Fires [onSelectionChangedListener] whenever the selection range changes.
 *  2. Suppresses the system floating text-selection toolbar.
 *  3. Hides the soft keyboard when text is selected (long-press) so it doesn't
 *     pop up unexpectedly. Keyboard shows normally when the user taps to type.
 */
class SelectionEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val imm by lazy {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    /** Called with `true` when text is selected, `false` when selection is cleared. */
    var onSelectionChangedListener: ((hasSelection: Boolean) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val hasSelection = selStart != selEnd
        onSelectionChangedListener?.invoke(hasSelection)

        if (hasSelection && windowToken != null) {
            // Long-press selected text — hide keyboard so it doesn't pop up.
            // The keyboard will come back naturally when the user taps to type.
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    // ── Suppress system floating toolbar ─────────────────────────────────────
    // Returns true  → action mode starts, selection stays alive  ✓
    // menu?.clear() → no menu items → floating toolbar is invisible ✓
    // onPrepareActionMode returns false → TextClassifier can't add items back ✓

    private val suppressCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu?.clear()   // remove all items so the floating toolbar has nothing to show
            return true     // MUST be true — false would cancel selection
        }
        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu?.clear()   // prevent TextClassifier from injecting items back
            return false
        }
        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
        override fun onDestroyActionMode(mode: ActionMode?) {}
    }

    init {
        customSelectionActionModeCallback = suppressCallback
        customInsertionActionModeCallback = suppressCallback
    }
}
