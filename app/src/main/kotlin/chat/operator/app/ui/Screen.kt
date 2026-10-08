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
import androidx.annotation.LayoutRes
import androidx.lifecycle.LifecycleCoroutineScope

/** What a screen may ask of its host: navigation, soft-key labels, coroutines. */
interface ScreenHost {
    val activity: MainActivity
    val scope: LifecycleCoroutineScope
    fun push(screen: Screen)
    fun pop()
    fun replaceAll(screen: Screen)
    fun setSoftKeys(left: CharSequence?, right: CharSequence?)
    fun string(resId: Int, vararg args: Any): String = activity.getString(resId, *args)
}

/**
 * One full-screen view in the stack (SPEC §6). Every screen owns its soft-key
 * labels, keeps a predictable focus order, and restores focus after updates.
 */
abstract class Screen(protected val host: ScreenHost) {
    lateinit var view: View
        private set

    val isCreated: Boolean get() = ::view.isInitialized

    /** An overlay (the options sheet) is shown on top of the screen beneath it, which stays visible. */
    open val isOverlay: Boolean = false

    fun create(parent: ViewGroup): View {
        view = onCreateView(LayoutInflater.from(parent.context), parent)
        return view
    }

    protected abstract fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View

    protected fun inflate(@LayoutRes layout: Int, parent: ViewGroup): View =
        LayoutInflater.from(parent.context).inflate(layout, parent, false)

    /** Called whenever the screen becomes the visible one (first show and after a pop). */
    open fun onShow() {}

    /** Called when the screen is covered or removed. */
    open fun onHide() {}

    /** Called when the activity returns to the foreground while this screen is showing. */
    open fun onResume() {}

    /** Left soft key (MENU). Return true if handled. */
    open fun onMenu(): Boolean = false

    /** Right soft key / BACK. Return true if handled (otherwise the host pops). */
    open fun onBack(): Boolean = false

    /** Green call key. */
    open fun onCall(): Boolean = false

    /** Any other key, before the framework handles it. Return true to consume. */
    open fun onKey(keyCode: Int): Boolean = false

    protected fun <T : View> find(id: Int): T = view.findViewById(id)
}
