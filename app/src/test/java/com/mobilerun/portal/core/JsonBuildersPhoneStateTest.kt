package com.mobilerun.portal.core

import android.view.accessibility.AccessibilityNodeInfo
import com.mobilerun.portal.model.PhoneState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonBuildersPhoneStateTest {
    @Test
    fun focusedElement_includesVerificationFlags() {
        val focused = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { focused.isPassword } returns true
        every { focused.isShowingHintText } returns false
        val state = PhoneState(
            focusedElement = focused,
            keyboardVisible = true,
            packageName = "com.example",
            appName = "Example",
            isEditable = true,
            activityName = ".MainActivity",
        )

        val json = JsonBuilders.phoneStateToJson(state).getJSONObject("focusedElement")

        assertTrue(json.getBoolean("isPassword"))
        assertFalse(json.getBoolean("isShowingHintText"))
    }
}
