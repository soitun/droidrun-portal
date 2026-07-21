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
    fun clear_replacesEntireFieldAtomically() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returnsMany listOf(
            snapshot("old", selectionStart = 3),
            snapshot("new", selectionStart = 3),
        )
        every { connection.setSelection(0, 3) } returns true
        every { connection.commitText("new", 1) } returns true
        val editor = editor { connection }

        assertEquals(TextInputResult.Verified, editor.inputText("new", clear = true))

        verify(exactly = 1) { connection.setSelection(0, 3) }
        verify(exactly = 1) { connection.commitText("new", 1) }
        verify(exactly = 0) { connection.deleteSurroundingText(any(), any()) }
    }

    @Test
    fun clear_rejectsExtractedWindowThatDoesNotStartAtFieldBeginning() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returns
            snapshot("old", startOffset = 7, selectionStart = 3)
        val editor = editor { connection }

        assertEquals(TextInputResult.Rejected, editor.inputText("new", clear = true))

        verify(exactly = 0) { connection.setSelection(any(), any()) }
        verify(exactly = 0) { connection.commitText(any(), any()) }
    }

    @Test
    fun clear_rejectsPartialChangeReport() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returns
            snapshot("old", partialStartOffset = 0, selectionStart = 3)
        val editor = editor { connection }

        assertEquals(TextInputResult.Rejected, editor.inputText("new", clear = true))

        verify(exactly = 0) { connection.setSelection(any(), any()) }
        verify(exactly = 0) { connection.commitText(any(), any()) }
    }

    @Test
    fun append_replacesSelectionAndIgnoresZeroWidthSentinels() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returnsMany listOf(
            snapshot("\u200BabcXYZ", selectionStart = 4, selectionEnd = 7),
            snapshot("\uFEFFabc9911656022", selectionStart = 13),
        )
        every { connection.setSelection(4, 7) } returns true
        every { connection.commitText("9911656022", 1) } returns true
        val editor = editor { connection }

        assertEquals(
            TextInputResult.Verified,
            editor.inputText("9911656022", clear = false),
        )
        verify(exactly = 1) { connection.commitText("9911656022", 1) }
    }

    @Test
    fun acceptedAppendWithStalePostState_isNotRetried() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returnsMany listOf(
            snapshot("hello", selectionStart = 5),
            snapshot("hello", selectionStart = 5),
        )
        every { connection.setSelection(5, 5) } returns true
        every { connection.commitText(" world", 1) } returns true
        val editor = editor { connection }

        assertEquals(
            TextInputResult.AcceptedUnverified,
            editor.inputText(" world", clear = false),
        )
        verify(exactly = 1) { connection.commitText(" world", 1) }
    }

    @Test
    fun acceptedAppendIsNotVerifiedByPreexistingSubstring() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returnsMany listOf(
            snapshot("1", selectionStart = 1),
            snapshot("1", selectionStart = 1),
        )
        every { connection.setSelection(1, 1) } returns true
        every { connection.commitText("1", 1) } returns true
        val editor = editor { connection }

        assertEquals(
            TextInputResult.AcceptedUnverified,
            editor.inputText("1", clear = false),
        )
        verify(exactly = 1) { connection.commitText("1", 1) }
    }

    @Test
    fun acceptedAppendWithoutReadablePostState_isNotRetried() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returns snapshot("", selectionStart = 0)
        every { connection.setSelection(0, 0) } returns true
        every { connection.commitText("hello", 1) } returns true
        val connections = ArrayDeque<InputConnection?>(listOf(connection, null))
        val editor = editor { connections.removeFirstOrNull() }

        assertEquals(
            TextInputResult.AcceptedUnverified,
            editor.inputText("hello", clear = false),
        )
        verify(exactly = 1) { connection.commitText("hello", 1) }
    }

    @Test
    fun clear_reacquiresConnectionAndRetriesAfterImeRestart() {
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
    fun rejectedOperationsRetryOnceThenFail() {
        val connection = mockk<InputConnection>(relaxed = true)
        every { connection.getExtractedText(any(), 0) } returns snapshot("old", selectionStart = 3)
        every { connection.setSelection(0, 3) } returns true
        every { connection.commitText("new", 1) } returns false
        val editor = editor { connection }

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
        partialStartOffset: Int = -1,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ): ExtractedText {
        return ExtractedText().apply {
            this.text = text
            this.startOffset = startOffset
            this.partialStartOffset = partialStartOffset
            this.partialEndOffset = partialStartOffset
            this.selectionStart = selectionStart
            this.selectionEnd = selectionEnd
        }
    }
}
