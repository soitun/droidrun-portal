package com.mobilerun.portal.model

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementNodeTest {

    @Test
    fun addChild_rejectsSelfReference() {
        val node = element("node")

        node.addChild(node)

        assertTrue(node.children.isEmpty())
        assertNull(node.parent)
    }

    @Test
    fun addChild_rejectsAncestorCycle() {
        val root = element("root")
        val child = element("child")

        root.addChild(child)
        child.addChild(root)

        assertEquals(listOf(child), root.children)
        assertTrue(child.children.isEmpty())
        assertNull(root.parent)
        assertSame(root, child.parent)
    }

    @Test
    fun graphHelpersTerminateWhenCycleAlreadyExists() {
        val root = element("root")
        val child = element("child")

        root.children.add(child)
        child.parent = root
        child.children.add(root)
        root.parent = child

        assertEquals(2, root.calculateNestingLevel())
        assertSame(root, root.getRootAncestor())
        assertEquals(listOf(child), root.getAllDescendants())
        assertEquals(listOf(child, root), root.getPathFromRoot())
    }

    private fun element(id: String): ElementNode {
        return ElementNode(
            nodeInfo = mockk<AccessibilityNodeInfo>(relaxed = true),
            rect = mockk<Rect>(relaxed = true),
            text = id,
            className = "TextView",
            windowLayer = 0,
            creationTime = 0L,
            id = id,
        )
    }
}
