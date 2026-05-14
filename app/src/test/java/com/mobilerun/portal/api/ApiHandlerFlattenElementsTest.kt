package com.mobilerun.portal.api

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.mobilerun.portal.core.StateRepository
import com.mobilerun.portal.model.ElementNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApiHandlerFlattenElementsTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun flattenElementsTerminatesWhenGraphAlreadyContainsCycle() {
        val root = element("root")
        val child = element("child")
        root.children.add(child)
        child.parent = root
        child.children.add(root)
        root.parent = child

        val result = flattenElements(listOf(root))

        assertEquals(listOf(root, child), result)
    }

    @Suppress("UNCHECKED_CAST")
    private fun flattenElements(elements: List<ElementNode>): List<ElementNode> {
        val handler = ApiHandler(
            stateRepo = StateRepository(service = null),
            getKeyboardIME = { null },
            getPackageManager = { mockk<PackageManager>(relaxed = true) },
            appVersionProvider = { "test" },
            context = mockk<Context>(relaxed = true),
        )
        val method = ApiHandler::class.java.getDeclaredMethod("flattenElements", List::class.java)
        method.isAccessible = true
        return method.invoke(handler, elements) as List<ElementNode>
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
