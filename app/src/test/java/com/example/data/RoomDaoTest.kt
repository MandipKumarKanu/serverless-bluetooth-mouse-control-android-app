package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the Room DAO layer directly against an in-memory database.
 * Covers CRUD plus the flow emissions the ViewModel depends on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AirMouseDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.airMouseDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun settings_roundTrip() = runBlocking {
        dao.updateSettings(
            SettingsEntity(
                sensitivity = 2.5f,
                smoothing = 0.7f,
                deadZone = 0.1f,
                invertX = true,
                invertY = false,
                scrollSpeed = 1.8f,
                vibrationFeedback = false
            )
        )

        val loaded = dao.getSettingsDirect()
        assertEquals(2.5f, loaded?.sensitivity ?: 0f, 0.001f)
        assertEquals(0.7f, loaded?.smoothing ?: 0f, 0.001f)
        assertEquals(0.1f, loaded?.deadZone ?: 0f, 0.001f)
        assertEquals(1.8f, loaded?.scrollSpeed ?: 0f, 0.001f)
        assertTrue(loaded?.invertX == true)
        assertTrue(loaded?.invertY == false)
        assertTrue(loaded?.vibrationFeedback == false)
    }

    @Test
    fun settings_flow_emitsUpdates() = runBlocking {
        dao.updateSettings(SettingsEntity(sensitivity = 1.8f))
        val emitted = dao.getSettingsFlow().first()
        assertEquals(1.8f, emitted?.sensitivity ?: 0f, 0.001f)
    }

    @Test
    fun shortcuts_addThenDelete() = runBlocking {
        dao.insertShortcut(ShortcutEntity(name = "Copy Macro", modifiers = 0x01, keyCodes = "6"))

        val list = dao.getAllShortcutsFlow().first()
        assertEquals(1, list.size)
        assertEquals("Copy Macro", list[0].name)
        assertEquals(0x01, list[0].modifiers)
        assertEquals("6", list[0].keyCodes)

        dao.deleteShortcut(list[0].id)
        assertTrue(dao.getAllShortcutsFlow().first().isEmpty())
    }

    @Test
    fun connectionHistory_isLimitedAndClearable() = runBlocking {
        // Insert more than the query limit (10) to verify the LIMIT applies
        for (i in 1..12) {
            dao.insertConnection(
                ConnectionHistoryEntity(
                    deviceName = "PC $i",
                    deviceAddress = "AA:BB:CC:DD:EE:0$i"
                )
            )
        }

        val recent = dao.getRecentConnectionsFlow().first()
        assertEquals(10, recent.size)

        dao.clearConnectionHistory()
        assertTrue(dao.getRecentConnectionsFlow().first().isEmpty())
    }

    @Test
    fun gestures_addThenDelete() = runBlocking {
        dao.insertGesture(
            GestureEntity(
                name = "Circle",
                points = "[{\"x\":1.0,\"y\":2.0,\"timestamp\":100}]",
                actionType = "keyboard",
                actionData = "copy",
                modifiers = 0
            )
        )

        val list = dao.getAllGesturesFlow().first()
        assertEquals(1, list.size)
        assertEquals("Circle", list[0].name)
        assertEquals("keyboard", list[0].actionType)
        assertEquals("copy", list[0].actionData)

        dao.deleteGesture(list[0].id)
        assertTrue(dao.getAllGesturesFlow().first().isEmpty())
    }
}
