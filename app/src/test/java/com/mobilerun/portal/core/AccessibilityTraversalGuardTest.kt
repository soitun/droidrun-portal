package com.mobilerun.portal.core

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityTraversalGuardTest {

    @Test
    fun activePathAllowsBoundedRepeatedKeysAndResetsOnLeave() {
        val activePath = mutableMapOf<String, Int>()
        val key = "same-node-key"

        repeat(AccessibilityTraversalGuard.MAX_ACCESSIBILITY_NODE_REPEATS_IN_PATH) {
            assertTrue(AccessibilityTraversalGuard.enterActivePath(key, activePath))
        }
        assertTrue(!AccessibilityTraversalGuard.enterActivePath(key, activePath))

        repeat(AccessibilityTraversalGuard.MAX_ACCESSIBILITY_NODE_REPEATS_IN_PATH) {
            AccessibilityTraversalGuard.leaveActivePath(key, activePath)
        }
        assertTrue(AccessibilityTraversalGuard.enterActivePath(key, activePath))
    }

    @Test
    fun traversalKeyIncludesStableNodeFields() {
        val rect = Rect(0, 0, 10, 10)
        val first = node(viewId = "first")
        val second = node(viewId = "second")

        val firstKey = AccessibilityTraversalGuard.createTraversalKey(first, rect)
        val secondKey = AccessibilityTraversalGuard.createTraversalKey(second, rect)

        assertNotEquals(firstKey, secondKey)
    }

    private fun node(viewId: String): AccessibilityNodeInfo {
        return mockk(relaxed = true) {
            every { windowId } returns 1
            every { className } returns "android.widget.TextView"
            every { viewIdResourceName } returns viewId
            every { packageName } returns "com.example"
            every { text } returns "Text"
            every { contentDescription } returns null
        }
    }
}
