package com.mobilerun.portal.input

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

internal sealed class TextInputResult {
    data object Verified : TextInputResult()
    data object Rejected : TextInputResult()
    data object AcceptedUnverified : TextInputResult()
    data object InputSessionChanged : TextInputResult()
}

internal class InputConnectionTextEditor(
    private val connectionProvider: () -> InputConnection?,
    private val generationProvider: () -> Long,
    private val sleep: (Long) -> Unit,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
) {
    companion object {
        private const val DEFAULT_RETRY_DELAY_MS = 100L
        private const val MAX_ATTEMPTS = 2
        private const val ZERO_WIDTH_SPACE = '\u200B'
        private const val ZERO_WIDTH_NO_BREAK_SPACE = '\uFEFF'
    }

    private data class Snapshot(
        val text: String,
        val startOffset: Int,
        val partialStartOffset: Int,
        val selectionStart: Int,
        val selectionEnd: Int,
    ) {
        val isCompleteReport: Boolean
            get() = partialStartOffset == -1

        val coversEntireField: Boolean
            get() = startOffset == 0 && isCompleteReport
    }

    fun inputText(text: String, clear: Boolean): TextInputResult {
        val inputSessionGeneration = generationProvider()
        var clearAcceptedWithoutVerification = false

        repeat(MAX_ATTEMPTS) { attempt ->
            if (!isSameInputSession(inputSessionGeneration)) {
                return TextInputResult.InputSessionChanged
            }

            val connection = connectionProvider()
            if (connection == null) {
                if (attempt < MAX_ATTEMPTS - 1) {
                    sleep(retryDelayMs)
                    return@repeat
                }
                return if (clearAcceptedWithoutVerification) {
                    TextInputResult.AcceptedUnverified
                } else {
                    TextInputResult.Rejected
                }
            }

            val before = readSnapshot(connection)
            if (before == null && !clear) {
                if (!isSameInputSession(inputSessionGeneration)) {
                    return TextInputResult.InputSessionChanged
                }
                val accepted = connection.commitText(text, 1)
                if (!isSameInputSession(inputSessionGeneration)) {
                    return TextInputResult.InputSessionChanged
                }
                if (accepted) {
                    return TextInputResult.AcceptedUnverified
                }
                if (attempt < MAX_ATTEMPTS - 1) {
                    sleep(retryDelayMs)
                    return@repeat
                }
                return TextInputResult.Rejected
            }

            if (before == null || (clear && !before.coversEntireField)) {
                if (attempt < MAX_ATTEMPTS - 1) {
                    sleep(retryDelayMs)
                    return@repeat
                }
                return if (clearAcceptedWithoutVerification) {
                    TextInputResult.AcceptedUnverified
                } else {
                    TextInputResult.Rejected
                }
            }

            if (!isSameInputSession(inputSessionGeneration)) {
                return TextInputResult.InputSessionChanged
            }
            if (!connection.finishComposingText()) {
                if (!isSameInputSession(inputSessionGeneration)) {
                    return TextInputResult.InputSessionChanged
                }
                if (attempt < MAX_ATTEMPTS - 1) {
                    sleep(retryDelayMs)
                    return@repeat
                }
                return resultAfterRejectedOperation(clearAcceptedWithoutVerification)
            }
            if (!isSameInputSession(inputSessionGeneration)) {
                return TextInputResult.InputSessionChanged
            }
            val selection = selectionFor(before, clear)
            val expected = expectedText(before, text, clear)
            if (!connection.setSelection(selection.first, selection.second)) {
                if (!isSameInputSession(inputSessionGeneration)) {
                    return TextInputResult.InputSessionChanged
                }
                if (attempt < MAX_ATTEMPTS - 1) {
                    sleep(retryDelayMs)
                    return@repeat
                }
                return resultAfterRejectedOperation(clearAcceptedWithoutVerification)
            }

            if (!isSameInputSession(inputSessionGeneration)) {
                return TextInputResult.InputSessionChanged
            }
            if (!connection.commitText(text, 1)) {
                if (!isSameInputSession(inputSessionGeneration)) {
                    return TextInputResult.InputSessionChanged
                }
                if (attempt < MAX_ATTEMPTS - 1) {
                    sleep(retryDelayMs)
                    return@repeat
                }
                return resultAfterRejectedOperation(clearAcceptedWithoutVerification)
            }
            if (clear) {
                clearAcceptedWithoutVerification = true
            }

            sleep(retryDelayMs)
            if (!isSameInputSession(inputSessionGeneration)) {
                return TextInputResult.InputSessionChanged
            }
            val afterConnection = connectionProvider()
            val after = afterConnection?.let(::readSnapshot)
            if (!isSameInputSession(inputSessionGeneration)) {
                return TextInputResult.InputSessionChanged
            }
            if (after != null &&
                canVerify(before, after, clear) &&
                matches(after.text, expected, text)
            ) {
                return TextInputResult.Verified
            }

            if (!clear) {
                return TextInputResult.AcceptedUnverified
            }

            if (attempt < MAX_ATTEMPTS - 1) {
                return@repeat
            }
            return if (after == null || !after.coversEntireField) {
                TextInputResult.AcceptedUnverified
            } else {
                TextInputResult.Rejected
            }
        }

        return if (clearAcceptedWithoutVerification) {
            TextInputResult.AcceptedUnverified
        } else {
            TextInputResult.Rejected
        }
    }

    private fun isSameInputSession(generation: Long): Boolean {
        return generationProvider() == generation
    }

    private fun resultAfterRejectedOperation(clearAccepted: Boolean): TextInputResult {
        return if (clearAccepted) {
            TextInputResult.AcceptedUnverified
        } else {
            TextInputResult.Rejected
        }
    }

    private fun readSnapshot(connection: InputConnection): Snapshot? {
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        val value = extracted.text?.toString() ?: ""
        val relativeStart = extracted.selectionStart
        val relativeEnd = extracted.selectionEnd
        if (relativeStart < 0 || relativeEnd < 0 || relativeStart > value.length || relativeEnd > value.length) {
            return null
        }
        return Snapshot(
            text = value,
            startOffset = extracted.startOffset,
            partialStartOffset = extracted.partialStartOffset,
            selectionStart = relativeStart,
            selectionEnd = relativeEnd,
        )
    }

    private fun selectionFor(snapshot: Snapshot, clear: Boolean): Pair<Int, Int> {
        val relativeStart = if (clear) 0 else minOf(snapshot.selectionStart, snapshot.selectionEnd)
        val relativeEnd = if (clear) snapshot.text.length else maxOf(snapshot.selectionStart, snapshot.selectionEnd)
        return Pair(snapshot.startOffset + relativeStart, snapshot.startOffset + relativeEnd)
    }

    private fun expectedText(snapshot: Snapshot, text: String, clear: Boolean): String {
        if (clear) return text

        val selectionStart = minOf(snapshot.selectionStart, snapshot.selectionEnd)
        val selectionEnd = maxOf(snapshot.selectionStart, snapshot.selectionEnd)
        return snapshot.text.replaceRange(selectionStart, selectionEnd, text)
    }

    private fun canVerify(
        before: Snapshot,
        after: Snapshot,
        clear: Boolean,
    ): Boolean {
        if (clear) return after.coversEntireField
        return before.isCompleteReport &&
            after.isCompleteReport &&
            before.startOffset == after.startOffset
    }

    private fun matches(actual: String, expected: String, requested: String): Boolean {
        if (requested.any(::isIgnoredSentinel)) {
            return actual == expected
        }
        return normalize(actual) == normalize(expected)
    }

    private fun normalize(value: String): String {
        return value.filterNot(::isIgnoredSentinel)
    }

    private fun isIgnoredSentinel(value: Char): Boolean {
        return value == ZERO_WIDTH_SPACE || value == ZERO_WIDTH_NO_BREAK_SPACE
    }
}
