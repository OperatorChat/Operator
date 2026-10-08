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

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isInvisible
import chat.operator.app.R
import chat.operator.app.databinding.ViewSoftkeyBarBinding

/** The label bar for the left and right soft keys (SPEC §6). Never focusable. */
class SoftKeyBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val binding = ViewSoftkeyBarBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = HORIZONTAL
        isFocusable = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        gravity = android.view.Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.softkey_bar_background)
        // Room for the hairline drawn along the top, and a little air round the chips.
        val air = (4 * resources.displayMetrics.density).toInt()
        setPadding(0, resources.getDimensionPixelSize(R.dimen.softkey_hairline) + air, 0, air)
    }

    fun setLabels(left: CharSequence?, right: CharSequence?) {
        // Text size follows the theme's body size at call time (the text-size
        // setting changes it; the bar outlives the screens that set it).
        val tv = android.util.TypedValue()
        if (context.theme.resolveAttribute(R.attr.opTextBody, tv, true)) {
            val px = tv.getDimension(resources.displayMetrics)
            binding.leftLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px)
            binding.rightLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px)
        }
        binding.leftLabel.text = left
        binding.leftLabel.isInvisible = left.isNullOrEmpty()
        binding.rightLabel.text = right
        binding.rightLabel.isInvisible = right.isNullOrEmpty()
    }
}
