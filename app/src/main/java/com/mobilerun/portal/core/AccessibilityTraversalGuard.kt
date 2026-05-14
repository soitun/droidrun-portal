package com.mobilerun.portal.core

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityTraversalGuard {
    const val MAX_ACCESSIBILITY_TREE_DEPTH = 128
    const val MAX_ACCESSIBILITY_NODE_REPEATS_IN_PATH = 8
    private const val TEXT_PREFIX_LENGTH = 64

    fun isTooDeep(depth: Int): Boolean = depth > MAX_ACCESSIBILITY_TREE_DEPTH

    fun enterActivePath(nodeKey: String, activeNodeKeyCounts: MutableMap<String, Int>): Boolean {
        val currentCount = activeNodeKeyCounts[nodeKey] ?: 0
        if (currentCount >= MAX_ACCESSIBILITY_NODE_REPEATS_IN_PATH) {
            return false
        }
        activeNodeKeyCounts[nodeKey] = currentCount + 1
        return true
    }

    fun leaveActivePath(nodeKey: String, activeNodeKeyCounts: MutableMap<String, Int>) {
        val currentCount = activeNodeKeyCounts[nodeKey] ?: return
        if (currentCount <= 1) {
            activeNodeKeyCounts.remove(nodeKey)
        } else {
            activeNodeKeyCounts[nodeKey] = currentCount - 1
        }
    }

    fun createTraversalKey(node: AccessibilityNodeInfo, rect: Rect): String {
        return listOf(
            safeInt { node.windowId }?.toString().orEmpty(),
            "${rect.left},${rect.top},${rect.right},${rect.bottom}",
            safeString { node.className },
            safeString { node.viewIdResourceName },
            safeString { node.packageName },
            safeString { node.text }.take(TEXT_PREFIX_LENGTH),
            safeString { node.contentDescription }.take(TEXT_PREFIX_LENGTH),
            uniqueId(node),
        ).joinToString(separator = "|")
    }

    private fun uniqueId(node: AccessibilityNodeInfo): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            safeString { node.uniqueId }
        } else {
            ""
        }
    }

    private inline fun safeString(block: () -> CharSequence?): String {
        return try {
            block()?.toString().orEmpty()
        } catch (_: RuntimeException) {
            ""
        }
    }

    private inline fun safeInt(block: () -> Int): Int? {
        return try {
            block()
        } catch (_: RuntimeException) {
            null
        }
    }
}
