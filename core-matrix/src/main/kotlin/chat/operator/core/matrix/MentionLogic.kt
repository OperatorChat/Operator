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
package chat.operator.core.matrix

/**
 * Pure helpers for @mentions. A Matrix message can name people three ways:
 * the `m.mentions` list of user ids, a "pill" link in the formatted body
 * (`<a href="https://matrix.to/#/@id">Name</a>`), and the plain "@Name" in
 * the text that bridges such as WhatsApp send. All three are read.
 */
object MentionLogic {
    private val pillPattern = Regex("""<a\s+[^>]*href=["']https?://matrix\.to/#/(@[^"'/?]+)[^"']*["'][^>]*>([^<]*)</a>""", RegexOption.IGNORE_CASE)
    private val tokenPattern = Regex("""(?<=^|\s)@([\p{L}\p{N}_.'-]{2,}(?:\s[\p{Lu}][\p{L}\p{N}'-]+)?)""")

    /** User ids linked as pills in [html]. */
    fun userIdsIn(html: String?): Set<String> =
        html?.let { h -> pillPattern.findAll(h).map { it.groupValues[1] }.toSet() } ?: emptySet()

    /** The people linked as pills in [html]: user id to display name. */
    fun pills(html: String?): Map<String, String> =
        html?.let { h -> pillPattern.findAll(h).associate { it.groupValues[1] to it.groupValues[2].trim().removePrefix("@") } } ?: emptyMap()

    /** Display names shown by pills in [html]. */
    fun pillNames(html: String?): List<String> =
        html?.let { h -> pillPattern.findAll(h).map { it.groupValues[2].trim().removePrefix("@") }.filter { it.isNotEmpty() }.toList() } ?: emptyList()

    /** Does this message mention the signed-in user ([myId], shown in the room as [myName])? */
    fun mentionsMe(body: String?, html: String?, mentionedUserIds: Collection<String>?, myId: String?, myName: String?): Boolean {
        if (myId != null && (mentionedUserIds?.contains(myId) == true || myId in userIdsIn(html))) return true
        if (!myName.isNullOrBlank() && body != null) {
            val at = Regex("""(?<=^|\s)@${Regex.escape(myName)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
            if (at.containsMatchIn(body)) return true
        }
        return false
    }

    /** Ranges in [body] to highlight as mentions: "@Name" tokens and pill names that appear in the text. */
    fun highlightRanges(body: String, html: String?): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        tokenPattern.findAll(body).forEach { ranges += it.range }
        pillNames(html).forEach { name ->
            var from = 0
            while (true) {
                val i = body.indexOf(name, from, ignoreCase = true)
                if (i < 0) break
                val start = if (i > 0 && body[i - 1] == '@') i - 1 else i
                val range = start until i + name.length
                if (ranges.none { it.first <= range.first && it.last >= range.last }) ranges += range
                from = i + name.length
            }
        }
        return ranges.sortedBy { it.first }
    }

    /** Turns "@Name" in [html] into pills for each (name → user id) pair, for an outgoing message. */
    fun pillify(html: String, mentions: Map<String, String>): String {
        var out = html
        mentions.forEach { (name, id) ->
            val escaped = escapeHtml(name)
            out = out.replace(Regex("""(?<![\p{L}\p{N}])@${Regex.escape(escaped)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)) {
                "<a href=\"https://matrix.to/#/$id\">$escaped</a>"
            }
        }
        return out
    }

    /** The members whose "@Name" appears in [body]. */
    fun mentionedMembers(body: String, members: List<RoomMember>): Map<String, String> =
        members.filter { m ->
            m.name.isNotBlank() && Regex("""(?<![\p{L}\p{N}])@${Regex.escape(m.name)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        }.associate { it.name to it.userId }

    private fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
