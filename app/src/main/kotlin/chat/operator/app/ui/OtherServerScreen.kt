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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import chat.operator.app.R
import chat.operator.core.matrix.MatrixRepository
import kotlinx.coroutines.launch

/** Advanced sign-in: any Matrix server with a username and password (SPEC §5.1 step 3). */
class OtherServerScreen(
    host: ScreenHost,
    private val prefillServer: String? = null,
    private val explanation: String? = null,
    private val titleRes: Int = R.string.other_server_title,
) : Screen(host) {

    private lateinit var server: EditText
    private lateinit var user: EditText
    private lateinit var password: EditText
    private lateinit var button: Button
    private lateinit var status: TextView
    private var busy = false

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_other_server, parent).also { v ->
            v.findViewById<TextView>(R.id.title).setText(titleRes)
            server = v.findViewById(R.id.server)
            user = v.findViewById(R.id.user)
            password = v.findViewById(R.id.password)
            button = v.findViewById(R.id.button)
            status = v.findViewById(R.id.status)
            button.setOnClickListener { submit() }
            password.setOnEditorActionListener { _, _, _ -> submit(); true }
            prefillServer?.let { server.setText(it) }
            v.findViewById<TextView>(R.id.explanation).apply {
                text = explanation
                isVisible = !explanation.isNullOrEmpty()
            }
        }

    override fun onShow() {
        host.setSoftKeys(host.string(R.string.other_server_button), host.string(R.string.softkey_back))
        (if (server.text.isNullOrEmpty()) server else user).requestFocus()
    }

    override fun onMenu(): Boolean {
        submit()
        return true
    }

    private fun submit() {
        if (busy) return
        val s = server.text.toString().trim()
        val u = user.text.toString().trim()
        val p = password.text.toString()
        if (s.isEmpty() || u.isEmpty() || p.isEmpty()) {
            status.text = host.string(R.string.other_server_incomplete)
            status.isVisible = true
            return
        }
        busy = true
        button.isEnabled = false
        status.text = host.string(R.string.other_server_signing_in)
        status.isVisible = true
        host.scope.launch {
            MatrixRepository.login(s, u, p, tokenLogin = false)
                .onSuccess { host.replaceAll(RecoveryKeyScreen(host)) }
                .onFailure { e ->
                    busy = false
                    button.isEnabled = true
                    val msg = e.message.orEmpty()
                    status.text = when {
                        msg.contains("m.login.sso", ignoreCase = true) || msg.contains("oidc", ignoreCase = true) ->
                            host.string(R.string.other_server_oidc)
                        else -> host.string(R.string.other_server_failed)
                    }
                    password.requestFocus()
                }
        }
    }
}
