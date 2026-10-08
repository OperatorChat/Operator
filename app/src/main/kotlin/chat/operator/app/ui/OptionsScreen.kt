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
import android.widget.TextView
import chat.operator.app.R

/**
 * An options list, used instead of pop-up dialogs: dialogs open in touch mode
 * on this hardware and hide their selection until a key is pressed. It rises
 * as a sheet over the dimmed screen underneath, so you keep your bearings.
 * Selecting an item pops this screen first, then runs the action.
 */
class OptionsScreen(
    host: ScreenHost,
    private val title: CharSequence,
    private val options: List<Pair<CharSequence, () -> Unit>>,
    /** Second lines for some options, keyed by their index (a link's address, say). */
    private val details: Map<Int, CharSequence> = emptyMap(),
    /** A grid of short choices (emoji reactions) placed before the option at [GridChoices.before]. */
    private val grid: GridChoices? = null,
) : MenuScreen(host) {

    /** [cells] laid out [columns] wide; [selected] cells are drawn ringed (a reaction already sent). */
    class GridChoices(
        val before: Int,
        val cells: List<Pair<CharSequence, () -> Unit>>,
        val selected: Set<Int> = emptySet(),
        val columns: Int = 5,
    )
    override val titleRes = R.string.softkey_options
    override val grouped = false
    override val isOverlay = true

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_options, parent).also { v ->
            v.findViewById<TextView>(R.id.title).text = title
            container = v.findViewById(R.id.items)
        }

    override fun onShow() {
        super.onShow()
        grid?.let { insertGrid(it) }
        // A long list (message options) must not push the title off the top: cap the sheet.
        val sheet = find<View>(R.id.sheet)
        val scroll = find<View>(R.id.scroll)
        view.post {
            val limit = view.height - (40 * view.resources.displayMetrics.density).toInt()
            if (sheet.height > limit) {
                scroll.layoutParams = scroll.layoutParams.apply { height = scroll.height - (sheet.height - limit) }
                scroll.requestLayout()
            }
        }
        if (Appearance.motion(host.activity)) {
            val sheet = find<View>(R.id.sheet)
            sheet.translationY = 80f * sheet.resources.displayMetrics.density
            sheet.animate().translationY(0f).setDuration(160).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }

    /** Builds the grid as rows of equal cells and slots it among the option rows. */
    private fun insertGrid(g: GridChoices) {
        val group = container.getChildAt(0) as? android.view.ViewGroup ?: return
        if (group.findViewById<View>(R.id.grid) != null) return
        val ctx = group.context
        val density = ctx.resources.displayMetrics.density
        val gridView = android.widget.LinearLayout(ctx).apply {
            id = R.id.grid
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        }
        g.cells.chunked(g.columns).forEachIndexed { rowIndex, rowCells ->
            val row = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
            rowCells.forEachIndexed { colIndex, (label, action) ->
                val index = rowIndex * g.columns + colIndex
                val cell = TextView(ctx).apply {
                    text = label
                    gravity = android.view.Gravity.CENTER
                    textSize = 22f
                    includeFontPadding = false
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isClickable = true
                    isSelected = index in g.selected
                    setBackgroundResource(R.drawable.emoji_cell)
                    setOnClickListener { host.pop(); action() }
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, (46 * density).toInt(), 1f).apply {
                        setMargins((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
                    }
                }
                row.addView(cell)
            }
            // Pad a short last row so its cells keep the same width as the others.
            repeat(g.columns - rowCells.size) {
                row.addView(View(ctx).apply { layoutParams = android.widget.LinearLayout.LayoutParams(0, 1, 1f) })
            }
            gridView.addView(row)
        }
        // A hairline under the grid, matching the one the row above already draws.
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(R.attr.opHairline, tv, true)
        gridView.addView(View(ctx).apply {
            setBackgroundColor(tv.data)
            layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { topMargin = (4 * density).toInt() }
        })
        val at = g.before.coerceIn(0, group.childCount)
        group.addView(gridView, at)
        // Entering the grid from above or below lands on its first cell of that edge, not the nearest one.
        val firstRow = gridView.getChildAt(0) as? android.view.ViewGroup
        val lastRow = gridView.getChildAt(gridView.childCount - 2) as? android.view.ViewGroup
        val firstCell = firstRow?.getChildAt(0)?.also { if (it.id == View.NO_ID) it.id = View.generateViewId() }
        val lastRowFirst = lastRow?.getChildAt(0)?.also { if (it.id == View.NO_ID) it.id = View.generateViewId() }
        if (firstCell != null && at > 0) group.getChildAt(at - 1).nextFocusDownId = firstCell.id
        if (lastRowFirst != null && at + 1 < group.childCount) group.getChildAt(at + 1).nextFocusUpId = lastRowFirst.id
    }

    override fun items(): List<MenuItem> = options.mapIndexed { index, (label, action) ->
        MenuItem(label, details[index]) {
            host.pop()
            action()
        }
    }
}

/** A scrollable block of text with a title (licences, long explanations). */
class TextScreen(host: ScreenHost, private val title: CharSequence, private val text: CharSequence) : Screen(host) {
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_text, parent).also { v ->
            v.findViewById<TextView>(R.id.title).text = title
            v.findViewById<TextView>(R.id.text).text = text
        }

    override fun onShow() {
        host.setSoftKeys(null, host.string(R.string.softkey_back))
        find<View>(R.id.scroll).requestFocus()
    }
}
