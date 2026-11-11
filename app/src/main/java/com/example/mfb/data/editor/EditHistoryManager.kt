package com.example.mfb.data.editor

import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.abs

class EditHistoryManager(private val maxEntriesPerTab: Int = 100) {

    private class TabHistory(
        val undoStack: ArrayDeque<TextFieldValue> = ArrayDeque(),
        val redoStack: ArrayDeque<TextFieldValue> = ArrayDeque(),
        var lastEditTimestampMs: Long = 0L
    )

    private val histories = mutableMapOf<String, TabHistory>()

    fun clearTab(relativePath: String) {
        histories.remove(relativePath)
    }

    fun recordEdit(
        relativePath: String,
        previousValue: TextFieldValue,
        newValue: TextFieldValue,
        nowMs: Long = System.currentTimeMillis(),
        forceDiscrete: Boolean = false
    ) {
        if (previousValue.text == newValue.text) return
        val history = histories.getOrPut(relativePath) { TabHistory() }
        history.redoStack.clear()

        val isSingleCharDelta = abs(newValue.text.length - previousValue.text.length) == 1
        val isBoundaryChar = isSingleCharDelta && newValue.text.length > previousValue.text.length &&
            newValue.selection.start > 0 &&
            newValue.text.getOrNull(newValue.selection.start - 1)?.let { it == '\n' || it == ' ' } == true

        val shouldCoalesce = !forceDiscrete &&
            isSingleCharDelta &&
            !isBoundaryChar &&
            history.undoStack.isNotEmpty() &&
            (nowMs - history.lastEditTimestampMs) < 450L

        if (!shouldCoalesce) {
            history.undoStack.addLast(previousValue)
            if (history.undoStack.size > maxEntriesPerTab) {
                history.undoStack.removeFirst()
            }
        }
        history.lastEditTimestampMs = nowMs
    }

    fun canUndo(relativePath: String): Boolean {
        return histories[relativePath]?.undoStack?.isNotEmpty() == true
    }

    fun canRedo(relativePath: String): Boolean {
        return histories[relativePath]?.redoStack?.isNotEmpty() == true
    }

    fun undo(relativePath: String, currentValue: TextFieldValue): TextFieldValue? {
        val history = histories[relativePath] ?: return null
        if (history.undoStack.isEmpty()) return null
        val previous = history.undoStack.removeLast()
        history.redoStack.addLast(currentValue)
        if (history.redoStack.size > maxEntriesPerTab) {
            history.redoStack.removeFirst()
        }
        history.lastEditTimestampMs = 0L
        return previous
    }

    fun redo(relativePath: String, currentValue: TextFieldValue): TextFieldValue? {
        val history = histories[relativePath] ?: return null
        if (history.redoStack.isEmpty()) return null
        val next = history.redoStack.removeLast()
        history.undoStack.addLast(currentValue)
        if (history.undoStack.size > maxEntriesPerTab) {
            history.undoStack.removeFirst()
        }
        history.lastEditTimestampMs = 0L
        return next
    }
}
