/*
 * Operator: chat for keypad phones.
 * Copyright (C) 2026 Spanorak (https://www.reddit.com/user/Spanorak)
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. It is distributed WITHOUT ANY WARRANTY; see
 * the GNU General Public License (LICENSE) for details.
 */
package chat.operator.app.ui

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import chat.operator.app.R

/**
 * One title, one explanation, one text field, one button. Used for the Beeper
 * email, the emailed code and the recovery key. The field takes focus as soon
 * as the screen opens so the keypad's own input method works straight away
 * (SPEC §5.4); centre on the field submits, as does the button.
 */
abstract class FormScreen(host: ScreenHost) : Screen(host) {

    protected abstract val titleRes: Int
    protected abstract val explanationRes: Int
    protected abstract val hintRes: Int
    protected abstract val buttonRes: Int
    /** Label for the left soft key; defaults to the button's text, overridden when that is too long for the chip. */
    protected open val softKeyRes: Int get() = buttonRes
    protected open val inputType: Int = InputType.TYPE_CLASS_TEXT
    protected open val initialValue: String? = null

    protected lateinit var field: EditText
    private lateinit var button: Button
    private lateinit var status: TextView
    private var busy = false

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_form, parent).also { v ->
            v.findViewById<TextView>(R.id.title).setText(titleRes)
            v.findViewById<TextView>(R.id.explanation).setText(explanationRes)
            field = v.findViewById(R.id.field)
            field.setHint(hintRes)
            field.inputType = inputType
            field.imeOptions = EditorInfo.IME_ACTION_DONE
            initialValue?.let { field.setText(it) }
            field.setOnEditorActionListener { _, _, _ -> submitIfIdle(); true }
            button = v.findViewById(R.id.button)
            button.setText(buttonRes)
            button.setOnClickListener { submitIfIdle() }
            status = v.findViewById(R.id.status)
        }

    override fun onShow() {
        host.setSoftKeys(host.string(softKeyRes), host.string(R.string.softkey_back))
        field.requestFocus()
        field.setSelection(field.text.length)
    }

    override fun onMenu(): Boolean {
        submitIfIdle()
        return true
    }

    private fun submitIfIdle() {
        if (busy) return
        onSubmit(field.text.toString().trim())
    }

    protected abstract fun onSubmit(value: String)

    protected fun setBusy(message: String?) {
        busy = message != null
        button.isEnabled = !busy
        field.isEnabled = !busy
        status.text = message
        status.isVisible = !message.isNullOrEmpty()
    }

    protected fun showError(message: String) {
        busy = false
        button.isEnabled = true
        field.isEnabled = true
        status.text = message
        status.isVisible = true
        field.requestFocus()
    }
}
