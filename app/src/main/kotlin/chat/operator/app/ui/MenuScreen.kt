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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import chat.operator.app.R

/**
 * One row of a [MenuScreen]: a title, an optional second line, an optional
 * glyph on a tinted square, and what selecting it does. [newGroup] starts a
 * fresh rounded group before this row.
 */
data class MenuItem(
    val title: CharSequence,
    val detail: CharSequence? = null,
    @DrawableRes val icon: Int? = null,
    @ColorRes val iconTint: Int? = null,
    val newGroup: Boolean = false,
    val onSelect: (() -> Unit)? = null,
) {
    constructor(title: CharSequence, detail: CharSequence?, onSelect: (() -> Unit)?) :
        this(title, detail, null, null, false, onSelect)
}

/**
 * A titled list of rows (settings, diagnostics, about), laid out as rounded
 * groups. Rows are rebuilt by [refresh]; focus stays on the same row index so
 * a live-updating screen never loses it (SPEC §6).
 */
abstract class MenuScreen(host: ScreenHost) : Screen(host) {

    protected abstract val titleRes: Int
    protected abstract fun items(): List<MenuItem>

    /** Rows sit in rounded surface groups; the options sheet switches this off. */
    protected open val grouped: Boolean = true

    protected lateinit var container: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_menu, parent).also { v ->
            v.findViewById<TextView>(R.id.title).setText(titleRes)
            container = v.findViewById(R.id.items)
        }

    override fun onShow() {
        host.setSoftKeys(host.string(R.string.softkey_select), host.string(R.string.softkey_back))
        refresh()
        if (container.focusedChild == null) firstRow()?.requestFocus()
    }

    private fun rows(): List<View> = buildList {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is LinearLayout && child.id == R.id.group) for (j in 0 until child.childCount) add(child.getChildAt(j)) else add(child)
        }
    }

    private fun firstRow(): View? = rows().firstOrNull()

    /** Moves focus to the row at [index] (counting across groups), if it exists. */
    protected fun focusRow(index: Int) {
        rows().getOrNull(index)?.requestFocus()
    }

    /** Rebuilds the rows from [items], keeping focus on the same position. */
    protected fun refresh() {
        val before = rows()
        val focused = before.indexOfFirst { it.hasFocus() }
        val inflater = LayoutInflater.from(container.context)
        val items = items()
        container.removeAllViews()
        var group: LinearLayout? = null
        items.forEachIndexed { index, item ->
            if (group == null || (item.newGroup && grouped)) {
                group = LinearLayout(container.context).apply {
                    id = R.id.group
                    orientation = LinearLayout.VERTICAL
                    if (grouped) {
                        setBackgroundResource(R.drawable.group_card)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        lp.topMargin = if (index == 0) 0 else resources().getDimensionPixelSize(R.dimen.gap)
                        layoutParams = lp
                    }
                }
                container.addView(group)
            }
            val row = inflater.inflate(R.layout.row_menu, group, false)
            row.findViewById<TextView>(R.id.title).text = item.title
            row.findViewById<TextView>(R.id.detail).apply {
                text = item.detail
                isVisible = !item.detail.isNullOrEmpty()
            }
            row.findViewById<ImageView>(R.id.icon).apply {
                isVisible = item.icon != null
                item.icon?.let { setImageResource(it) }
                item.iconTint?.let { background = background.mutate().also { bg -> bg.setTint(ContextCompat.getColor(context, it)) } }
            }
            row.findViewById<View>(R.id.chevron).isVisible = item.onSelect != null && grouped
            row.setOnClickListener { item.onSelect?.invoke() }
            row.isFocusable = true
            group!!.addView(row)
        }
        // The last row of each group needs no hairline.
        for (i in 0 until container.childCount) {
            val g = container.getChildAt(i) as? LinearLayout ?: continue
            g.getChildAt(g.childCount - 1)?.findViewById<View>(R.id.divider)?.isVisible = false
        }
        val after = rows()
        if (focused in after.indices) after[focused].requestFocus()
    }

    private fun resources() = container.resources

    override fun onMenu(): Boolean {
        // Whatever has focus (a row, or one cell of a grid) is what Select activates.
        container.findFocus()?.performClick()
        return true
    }
}
