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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Patterns
import android.widget.Toast
import chat.operator.app.R

/**
 * Web links inside messages. Nothing opens by itself: links are listed in the
 * message's options and open in the phone's browser only when chosen (SPEC §5.3
 * keeps the chat free of surprises). One message may carry several.
 */
object Links {
    private val hrefPattern = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /** Every distinct link in [body], plus any hidden behind formatted text in [html]. */
    fun find(body: String, html: String?): List<String> {
        val found = LinkedHashSet<String>()
        val m = Patterns.WEB_URL.matcher(body)
        while (m.find()) {
            val raw = body.substring(m.start(), m.end()).trimEnd('.', ',', ')', '!', '?', ';', ':')
            if (!raw.contains('@')) found += withScheme(raw)
        }
        html?.let { h -> hrefPattern.findAll(h).forEach { r -> val u = r.groupValues[1]; if (u.startsWith("http", true)) found += u } }
        // matrix.to links are mentions of people (handled as "Message …" rows), not pages to open.
        return found.filterNot { it.contains("matrix.to/", ignoreCase = true) }
    }

    /** Ranges of links in [body], for underlining; no click handling, the D-pad stays on the row. */
    fun highlight(body: CharSequence, colour: Int): CharSequence {
        val text = body.toString()
        val m = Patterns.WEB_URL.matcher(text)
        var spannable: SpannableString? = null
        while (m.find()) {
            val piece = text.substring(m.start(), m.end())
            if (piece.contains('@') || piece.contains("matrix.to", ignoreCase = true)) continue
            val s = spannable ?: SpannableString(text).also { spannable = it }
            s.setSpan(UnderlineSpan(), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(colour), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable ?: body
    }

    /** "bbc.co.uk/news/…": the address without its scheme, kept short for a menu row. */
    fun label(url: String, max: Int = 34): String {
        val bare = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        return if (bare.length <= max) bare else bare.take(max - 1) + "…"
    }

    fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.chat_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private fun withScheme(raw: String): String =
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
}
