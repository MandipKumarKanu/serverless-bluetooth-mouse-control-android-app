package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the production migration chain (v1 -> v6) actually produces a
 * usable database. The v1 schema is built by hand from the entity history
 * (settings table with `themeDark`, before useDynamicColors/themeMode).
 */
@RunWith(RobolectricTestRunner::class)
// Run on both a pre-Android-13 SDK and a modern one: the v4->v5 migration must
// not rely on SQLite features that only ship on Android 13+ (e.g. DROP COLUMN).
@Config(sdk = [28, 33])
class AppDatabaseMigrationTest {

    private val dbName = "migration_test.db"

    private fun createDatabaseAtVersion1(context: Context) {
        context.deleteDatabase(dbName)
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // settings schema as it existed at DB version 1
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `settings` (" +
                            "`id` INTEGER NOT NULL, " +
                            "`sensitivity` REAL NOT NULL, " +
                            "`smoothing` REAL NOT NULL, " +
                            "`deadZone` REAL NOT NULL, " +
                            "`acceleration` REAL NOT NULL, " +
                            "`invertX` INTEGER NOT NULL, " +
                            "`invertY` INTEGER NOT NULL, " +
                            "`scrollSpeed` REAL NOT NULL, " +
                            "`vibrationFeedback` INTEGER NOT NULL, " +
                            "`soundFeedback` INTEGER NOT NULL, " +
                            "`keepScreenAwake` INTEGER NOT NULL, " +
                            "`themeDark` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                    )
                    // Seed one row with themeDark=1 so the themeDark->themeMode
                    // conversion in MIGRATION_4_5 is actually exercised.
                    db.execSQL(
                        "INSERT INTO `settings` (`id`,`sensitivity`,`smoothing`,`deadZone`,`acceleration`," +
                            "`invertX`,`invertY`,`scrollSpeed`,`vibrationFeedback`,`soundFeedback`," +
                            "`keepScreenAwake`,`themeDark`) VALUES " +
                            "(1, 1.0, 0.3, 0.05, 1.2, 0, 0, 1.0, 1, 0, 1, 1)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `shortcuts` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`modifiers` INTEGER NOT NULL, " +
                            "`keyCodes` TEXT NOT NULL)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        // Open once to create the file, then close so Room can migrate it.
        factory.create(config).writableDatabase.close()
    }

    private fun columnNames(db: SupportSQLiteDatabase, table: String): Set<String> {
        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(1))
            }
        }
        return columns
    }

    private fun tableNames(db: SupportSQLiteDatabase): Set<String> {
        val tables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
        }
        return tables
    }

    @Test
    fun migrateFromV1ToV5() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createDatabaseAtVersion1(context)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .build()

        // Trigger the migration by opening the database. Room also validates
        // the final schema here, so a broken migration would throw.
        val sqlDb = db.openHelper.writableDatabase
        val settingsColumns = columnNames(sqlDb, "settings")

        // v5 settings schema: themeMode replaced themeDark, useDynamicColors exists
        assertTrue("themeMode column missing", "themeMode" in settingsColumns)
        assertTrue("useDynamicColors column missing", "useDynamicColors" in settingsColumns)
        assertFalse("themeDark should have been dropped", "themeDark" in settingsColumns)

        // Seeded row had themeDark=1 -> should now be themeMode=2 (Dark)
        sqlDb.query("SELECT themeMode FROM settings WHERE id = 1").use { cursor ->
            assertTrue("seeded row missing after migration", cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }

        // Tables created by later migrations exist
        val tables = tableNames(sqlDb)
        assertTrue("connection_history table missing", "connection_history" in tables)
        assertTrue("gestures table missing", "gestures" in tables)
        assertTrue("device_settings table missing", "device_settings" in tables)

        // Sanity: the DAO is fully usable after the migration chain
        runBlocking {
            db.airMouseDao().updateSettings(SettingsEntity(sensitivity = 2.0f))
            val loaded = db.airMouseDao().getSettingsDirect()
            assertEquals(2.0f, loaded?.sensitivity ?: 0f, 0.001f)
            assertTrue(loaded?.themeMode == 2)

            // The per-device profile table is usable after migration
            db.airMouseDao().updateDeviceSettings(
                DeviceSettingsEntity(
                    deviceAddress = "AA:BB:CC:DD:EE:FF",
                    sensitivity = 2.5f,
                    invertX = true
                )
            )
            val profile = db.airMouseDao().getDeviceSettingsDirect("AA:BB:CC:DD:EE:FF")
            assertEquals(2.5f, profile?.sensitivity ?: 0f, 0.001f)
            assertTrue(profile?.invertX == true)
        }

        db.close()
    }
}
