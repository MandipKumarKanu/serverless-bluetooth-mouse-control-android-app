package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsEntity
import com.example.data.ShortcutEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AirMouse", appName)
    }

    @Test
    fun `verify SettingsEntity defaults`() {
        val settings = SettingsEntity()
        assertEquals(1.0f, settings.sensitivity, 0.001f)
        assertEquals(0.3f, settings.smoothing, 0.001f)
        assertEquals(0.05f, settings.deadZone, 0.001f)
        assertTrue(settings.vibrationFeedback)
        assertTrue(settings.keepScreenAwake)
    }

    @Test
    fun `verify ShortcutEntity fields`() {
        val shortcut = ShortcutEntity(
            name = "Copy Macro",
            modifiers = 0x01, // Ctrl
            keyCodes = "6" // C
        )
        assertEquals("Copy Macro", shortcut.name)
        assertEquals(0x01, shortcut.modifiers)
        assertEquals("6", shortcut.keyCodes)
    }
}
