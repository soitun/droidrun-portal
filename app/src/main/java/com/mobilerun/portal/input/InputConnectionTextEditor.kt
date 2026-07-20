package com.mobilerun.portal.input

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

internal sealed class TextInputResult {
    data object Verified : TextInputResult()
    data object Rejected : TextInputResult()
    data object AcceptedUnverified : TextInputResult()
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
        val selectionStart: Int,
        val selectionEnd: Int,
    )

    fun inputText(text: String, clear: Boolean): TextInputResult {
        var acceptedWithoutVerification = false

        repeat(MAX_ATTEMPTS) { attempt ->
            val connection = connectionProvider()
            val before = connection?.let(::readSnapshot)
            if (connection == null || before == null) {
                if (attempt < MAX_ATTEMPTS - 1) {
                    sleep(retryDelayMs)
                    return@repeat
                }
                return if (acceptedWithoutVerification) {
                    TextInputResult.AcceptedUnverified
                } else {
                    TextInputResult.Rejected
                }
            }

            val generation = generationProvider()
            val selection = selectionFor(before, clear)
            if (!connection.setSelection(selection.first, selection.second)) {
                if (attempt < MAX_ATTEMPTS - 1) {
                    sleep(retryDelayMs)
                    return@repeat
                }
                return TextInputResult.Rejected
            }

            if (!connection.commitText(text, 1)) {
                if (attempt < MAX_ATTEMPTS - 1) {
                    sleep(retryDelayMs)
                    return@repeat
                }
                return TextInputResult.Rejected
            }
            acceptedWithoutVerification = true

            sleep(retryDelayMs)
            val afterConnection = connectionProvider()
            val after = afterConnection?.let(::readSnapshot)
            if (after == null) {
                if (clear && attempt < MAX_ATTEMPTS - 1) {
                    return@repeat
                }
                return TextInputResult.AcceptedUnverified
            }

            if (matches(after.text, text, clear)) {
                return TextInputResult.Verified
            }

            val restarted = generationProvider() != generation || afterConnection !== connection
            if (attempt < MAX_ATTEMPTS - 1 && (clear || restarted || !contains(after.text, text))) {
                return@repeat
            }
            return TextInputResult.Rejected
        }

        return if (acceptedWithoutVerification) {
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
            selectionStart = relativeStart,
            selectionEnd = relativeEnd,
        )
    }

    private fun selectionFor(snapshot: Snapshot, clear: Boolean): Pair<Int, Int> {
        val relativeStart = if (clear) 0 else minOf(snapshot.selectionStart, snapshot.selectionEnd)
        val relativeEnd = if (clear) snapshot.text.length else maxOf(snapshot.selectionStart, snapshot.selectionEnd)
        return Pair(snapshot.startOffset + relativeStart, snapshot.startOffset + relativeEnd)
    }

    private fun matches(actual: String, expected: String, clear: Boolean): Boolean {
        val normalizedActual = normalize(actual)
        val normalizedExpected = normalize(expected)
        return if (clear) {
            normalizedActual == normalizedExpected
        } else {
            normalizedActual.contains(normalizedExpected)
        }
    }

    private fun contains(actual: String, expected: String): Boolean {
        return normalize(actual).contains(normalize(expected))
    }

    private fun normalize(value: String): String {
        return value.filterNot { it == ZERO_WIDTH_SPACE || it == ZERO_WIDTH_NO_BREAK_SPACE }
    }
}
