package com.example.mfb.data.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

object CodeEditingHelpers {

    private val VOID_HTML_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr", "!doctype"
    )

    private val HTML_OPEN_TAG_REGEX = Regex("""<([a-zA-Z][a-zA-Z0-9:-]*)(?:\s[^>]*)?>\s*$""")

    fun computeLineAndColumn(text: String, offset: Int): Pair<Int, Int> {
        val safeOffset = offset.coerceIn(0, text.length)
        var line = 1
        var lastLineStart = 0
        for (i in 0 until safeOffset) {
            if (text[i] == '\n') {
                line++
                lastLineStart = i + 1
            }
        }
        val column = (safeOffset - lastLineStart) + 1
        return line to column
    }

    fun getOffsetForLine(text: String, targetLine: Int): Int {
        if (targetLine <= 1 || text.isEmpty()) return 0
        var currentLine = 1
        for (i in text.indices) {
            if (currentLine == targetLine) return i
            if (text[i] == '\n') {
                currentLine++
            }
        }
        return text.length
    }

    fun getLineCount(text: String): Int {
        if (text.isEmpty()) return 1
        var lines = 1
        for (ch in text) {
            if (ch == '\n') lines++
        }
        return lines
    }

    fun findSearchMatches(text: String, query: String, matchCase: Boolean): List<IntRange> {
        if (query.isEmpty() || text.isEmpty()) return emptyList()
        val matches = mutableListOf<IntRange>()
        var index = text.indexOf(query, startIndex = 0, ignoreCase = !matchCase)
        while (index >= 0) {
            matches.add(index until (index + query.length))
            index = text.indexOf(query, startIndex = index + query.length.coerceAtLeast(1), ignoreCase = !matchCase)
        }
        return matches
    }

    /**
     * Applies automatic indentation when a single newline is inserted at the cursor.
     * Preserves leading whitespace of the previous line and adds one indent unit after '{'
     * or a non-void opening HTML tag.
     */
    fun applyAutoIndent(
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
        indentUnit: String
    ): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text
        val cursor = newValue.selection.start
        if (newText.length != oldText.length + 1) return newValue
        if (cursor <= 0 || cursor > newText.length) return newValue
        if (newText[cursor - 1] != '\n') return newValue

        // Verify the inserted character at cursor - 1 was indeed the single newline diff
        val beforeCursor = newText.substring(0, cursor - 1)
        val prevLineStart = beforeCursor.lastIndexOf('\n').let { if (it < 0) 0 else it + 1 }
        val prevLine = beforeCursor.substring(prevLineStart)
        val leadingWhitespace = prevLine.takeWhile { it == ' ' || it == '\t' }
        val trimmedPrev = prevLine.trimEnd()

        val shouldIncreaseIndent = when {
            trimmedPrev.endsWith("{") || trimmedPrev.endsWith("[") -> true
            trimmedPrev.endsWith("/>") -> false
            else -> {
                val match = HTML_OPEN_TAG_REGEX.find(trimmedPrev)
                val tagName = match?.groupValues?.getOrNull(1)?.lowercase()
                tagName != null && tagName !in VOID_HTML_TAGS
            }
        }

        val indentToInsert = if (shouldIncreaseIndent) leadingWhitespace + indentUnit else leadingWhitespace
        if (indentToInsert.isEmpty()) return newValue

        val updatedText = buildString(newText.length + indentToInsert.length) {
            append(newText, 0, cursor)
            append(indentToInsert)
            append(newText, cursor, newText.length)
        }
        val newCursor = cursor + indentToInsert.length
        return newValue.copy(
            text = updatedText,
            selection = TextRange(newCursor)
        )
    }

    /**
     * Inserts [indentUnit] at the current cursor or replaces selection.
     */
    fun insertIndent(value: TextFieldValue, indentUnit: String): TextFieldValue {
        val start = value.selection.min.coerceIn(0, value.text.length)
        val end = value.selection.max.coerceIn(0, value.text.length)
        val updated = value.text.replaceRange(start, end, indentUnit)
        val newCursor = start + indentUnit.length
        return value.copy(text = updated, selection = TextRange(newCursor))
    }

    /**
     * Finds matching bracket pair around cursor (checks cursor and cursor - 1).
     * Bounded scan window keeps typing O(1) even on large files.
     */
    fun findMatchingBracket(text: String, cursorOffset: Int): Pair<Int, Int>? {
        if (text.isEmpty()) return null
        val candidates = listOf(cursorOffset - 1, cursorOffset).filter { it in text.indices }
        for (idx in candidates) {
            val ch = text[idx]
            val matchIdx = when (ch) {
                '(' -> scanForward(text, idx, '(', ')')
                '{' -> scanForward(text, idx, '{', '}')
                '[' -> scanForward(text, idx, '[', ']')
                '<' -> scanForward(text, idx, '<', '>')
                ')' -> scanBackward(text, idx, '(', ')')
                '}' -> scanBackward(text, idx, '{', '}')
                ']' -> scanBackward(text, idx, '[', ']')
                '>' -> scanBackward(text, idx, '<', '>')
                else -> -1
            }
            if (matchIdx >= 0) {
                return if (idx < matchIdx) idx to matchIdx else matchIdx to idx
            }
        }
        return null
    }

    private const val MAX_BRACKET_SCAN = 15_000

    private fun scanForward(text: String, startIdx: Int, open: Char, close: Char): Int {
        var depth = 0
        val limit = minOf(text.length, startIdx + MAX_BRACKET_SCAN)
        for (i in startIdx until limit) {
            when (text[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun scanBackward(text: String, startIdx: Int, open: Char, close: Char): Int {
        var depth = 0
        val limit = maxOf(0, startIdx - MAX_BRACKET_SCAN)
        for (i in startIdx downTo limit) {
            when (text[i]) {
                close -> depth++
                open -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }
}
