package com.mobilerun.portal.input

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class InputConnectionTextEditorTest {
    @Test
    fun clear_replacesExtractedRangeAtomically() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returnsMany listOf(
            snapshot("old", startOffset = 7, selectionStart = 3),
            snapshot("new", startOffset = 7, selectionStart = 3),
        )
        every { connection.setSelection(7, 10) } returns true
        every { connection.commitText("new", 1) } returns true
        val editor = editor({ connection })

        assertEquals(TextInputResult.Verified, editor.inputText("new", clear = true))

        verify(exactly = 1) { connection.setSelection(7, 10) }
        verify(exactly = 1) { connection.commitText("new", 1) }
        verify(exactly = 0) { connection.deleteSurroundingText(any(), any()) }
    }

    @Test
    fun append_preservesSelectionAndIgnoresZeroWidthSentinel() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returnsMany listOf(
            snapshot("\u200B", selectionStart = 1),
            snapshot("\u200B9911656022", selectionStart = 11),
        )
        every { connection.setSelection(1, 1) } returns true
        every { connection.commitText("9911656022", 1) } returns true
        val editor = editor({ connection })

        assertEquals(
            TextInputResult.Verified,
            editor.inputText("9911656022", clear = false),
        )
    }

    @Test
    fun imeRestart_reacquiresConnectionAndRetries() {
        val stale = mockk<InputConnection>(relaxed = true)
        val fresh = mockk<InputConnection>(relaxed = true)
        every { stale.getExtractedText(any(), 0) } returns snapshot("old", selectionStart = 3)
        every { stale.setSelection(0, 3) } returns true
        every { stale.commitText("new", 1) } returns true
        every { fresh.getExtractedText(any(), 0) } returnsMany listOf(
            snapshot("old", selectionStart = 3),
            snapshot("old", selectionStart = 3),
            snapshot("\uFEFFnew", selectionStart = 4),
        )
        every { fresh.setSelection(0, 3) } returns true
        every { fresh.commitText("new", 1) } returns true

        val connections = ArrayDeque(listOf(stale, fresh, fresh, fresh))
        var generation = 1L
        var sleeps = 0
        val editor = InputConnectionTextEditor(
            connectionProvider = { connections.removeFirstOrNull() },
            generationProvider = { generation },
            sleep = {
                sleeps++
                if (sleeps == 1) generation++
            },
        )

        assertEquals(TextInputResult.Verified, editor.inputText("new", clear = true))
        verify(exactly = 1) { stale.commitText("new", 1) }
        verify(exactly = 1) { fresh.commitText("new", 1) }
    }

    @Test
    fun acceptedAppendWithoutReadablePostState_isNotRetried() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returns snapshot("", selectionStart = 0)
        every { connection.setSelection(0, 0) } returns true
        every { connection.commitText("hello", 1) } returns true
        val connections = ArrayDeque<InputConnection?>(listOf(connection, null))
        val editor = editor({ connections.removeFirstOrNull() })

        assertEquals(
            TextInputResult.AcceptedUnverified,
            editor.inputText("hello", clear = false),
        )
        verify(exactly = 1) { connection.commitText("hello", 1) }
    }

    @Test
    fun rejectedOperationsRetryOnceThenFail() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returns snapshot("old", selectionStart = 3)
        every { connection.setSelection(0, 3) } returns true
        every { connection.commitText("new", 1) } returns false
        val editor = editor({ connection })

        assertEquals(TextInputResult.Rejected, editor.inputText("new", clear = true))
        verify(exactly = 2) { connection.commitText("new", 1) }
    }

    private fun editor(connectionProvider: () -> InputConnection?): InputConnectionTextEditor {
        return InputConnectionTextEditor(
            connectionProvider = connectionProvider,
            generationProvider = { 1L },
            sleep = {},
        )
    }

    private fun snapshot(
        text: String,
        startOffset: Int = 0,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ): ExtractedText {
        return ExtractedText().apply {
            this.text = text
            this.startOffset = startOffset
            this.selectionStart = selectionStart
            this.selectionEnd = selectionEnd
        }
    }
}
