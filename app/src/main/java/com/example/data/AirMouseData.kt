package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val sensitivity: Float = 1.0f,
    val smoothing: Float = 0.3f,
    val deadZone: Float = 0.05f,
    val acceleration: Float = 1.2f,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
    val scrollSpeed: Float = 1.0f,
    val vibrationFeedback: Boolean = true,
    val soundFeedback: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val themeMode: Int = 0, // 0=System Default, 1=Light, 2=Dark
    val useDynamicColors: Boolean = false
)

@Entity(tableName = "shortcuts")
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val modifiers: Int, // Modifier flags (bitmask: Ctrl=0x01, Shift=0x02, Alt=0x04, GUI=0x08)
    val keyCodes: String // Comma-separated list of key scan codes (e.g. "6" for C, "25" for V)
)

@Entity(tableName = "connection_history")
data class ConnectionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deviceName: String,
    val deviceAddress: String,
    val connectedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "gestures")
data class GestureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val points: String, // JSON string of GesturePoint list
    val actionType: String, // "keyboard", "media", "mouse"
    val actionData: String, // Key code, media action, etc.
    val modifiers: Int = 0, // Keyboard modifiers
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Pointer settings remembered per paired host. When a device is connected its
 * row overrides the global pointer settings; app-level settings (theme,
 * vibration, keep-awake, ...) stay global in [SettingsEntity].
 */
@Entity(tableName = "device_settings")
data class DeviceSettingsEntity(
    @PrimaryKey val deviceAddress: String,
    val sensitivity: Float = 1.0f,
    val smoothing: Float = 0.3f,
    val deadZone: Float = 0.05f,
    val acceleration: Float = 1.2f,
    val scrollSpeed: Float = 1.0f,
    val invertX: Boolean = false,
    val invertY: Boolean = false
)

@Dao
interface AirMouseDao {
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: SettingsEntity)

    @Query("SELECT * FROM shortcuts ORDER BY id ASC")
    fun getAllShortcutsFlow(): Flow<List<ShortcutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: ShortcutEntity)

    @Query("DELETE FROM shortcuts WHERE id = :id")
    suspend fun deleteShortcut(id: Int)

    @Query("SELECT * FROM connection_history ORDER BY connectedAt DESC LIMIT 10")
    fun getRecentConnectionsFlow(): Flow<List<ConnectionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: ConnectionHistoryEntity)

    @Query("DELETE FROM connection_history WHERE deviceAddress = :address")
    suspend fun deleteConnectionHistoryByAddress(address: String)

    /**
     * Records a connection, keeping only the most recent entry per device:
     * any previous rows for the same host are removed first, so the history
     * shows each device exactly once (at its latest connect time).
     */
    @Transaction
    suspend fun upsertConnection(connection: ConnectionHistoryEntity) {
        deleteConnectionHistoryByAddress(connection.deviceAddress)
        insertConnection(connection)
    }

    /** Keep only the most recent row per device (cleanup for pre-fix data). */
    @Query(
        "DELETE FROM connection_history WHERE id NOT IN (" +
            "SELECT c1.id FROM connection_history c1 " +
            "WHERE c1.connectedAt = (SELECT MAX(c2.connectedAt) FROM connection_history c2 " +
            "WHERE c2.deviceAddress = c1.deviceAddress))"
    )
    suspend fun dedupeConnectionHistory()

    @Query("DELETE FROM connection_history")
    suspend fun clearConnectionHistory()

    @Query("SELECT * FROM gestures ORDER BY createdAt DESC")
    fun getAllGesturesFlow(): Flow<List<GestureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGesture(gesture: GestureEntity)

    @Query("DELETE FROM gestures WHERE id = :id")
    suspend fun deleteGesture(id: Int)

    @Query("SELECT * FROM device_settings")
    fun getAllDeviceSettingsFlow(): Flow<List<DeviceSettingsEntity>>

    @Query("SELECT * FROM device_settings WHERE deviceAddress = :address LIMIT 1")
    fun getDeviceSettingsFlow(address: String): Flow<DeviceSettingsEntity?>

    @Query("SELECT * FROM device_settings WHERE deviceAddress = :address LIMIT 1")
    suspend fun getDeviceSettingsDirect(address: String): DeviceSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateDeviceSettings(deviceSettings: DeviceSettingsEntity)

    @Query("DELETE FROM device_settings WHERE deviceAddress = :address")
    suspend fun deleteDeviceSettings(address: String)
}

@Database(
    entities = [
        SettingsEntity::class,
        ShortcutEntity::class,
        ConnectionHistoryEntity::class,
        GestureEntity::class,
        DeviceSettingsEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun airMouseDao(): AirMouseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN useDynamicColors INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS connection_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        deviceName TEXT NOT NULL,
                        deviceAddress TEXT NOT NULL,
                        connectedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS gestures (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        points TEXT NOT NULL,
                        actionType TEXT NOT NULL,
                        actionData TEXT NOT NULL,
                        modifiers INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rename themeDark to themeMode and convert: false(0) -> 0(System), true(2) -> 2(Dark).
                // This is a table rebuild instead of ALTER TABLE ... DROP COLUMN because
                // DROP COLUMN requires SQLite >= 3.35 (Android 13+); minSdk is 24, so on
                // Android 7-12 the DROP would throw and crash every upgrade from schema v4.
                db.execSQL("ALTER TABLE settings RENAME TO settings_old")
                db.execSQL(
                    "CREATE TABLE settings (" +
                        "id INTEGER NOT NULL, " +
                        "sensitivity REAL NOT NULL, " +
                        "smoothing REAL NOT NULL, " +
                        "deadZone REAL NOT NULL, " +
                        "acceleration REAL NOT NULL, " +
                        "invertX INTEGER NOT NULL, " +
                        "invertY INTEGER NOT NULL, " +
                        "scrollSpeed REAL NOT NULL, " +
                        "vibrationFeedback INTEGER NOT NULL, " +
                        "soundFeedback INTEGER NOT NULL, " +
                        "keepScreenAwake INTEGER NOT NULL, " +
                        "themeMode INTEGER NOT NULL DEFAULT 0, " +
                        "useDynamicColors INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(id))"
                )
                db.execSQL(
                    "INSERT INTO settings (id, sensitivity, smoothing, deadZone, acceleration, invertX, invertY, " +
                        "scrollSpeed, vibrationFeedback, soundFeedback, keepScreenAwake, themeMode, useDynamicColors) " +
                        "SELECT id, sensitivity, smoothing, deadZone, acceleration, invertX, invertY, " +
                        "scrollSpeed, vibrationFeedback, soundFeedback, keepScreenAwake, " +
                        "CASE WHEN themeDark = 1 THEN 2 ELSE 0 END, useDynamicColors FROM settings_old"
                )
                db.execSQL("DROP TABLE settings_old")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Per-device pointer settings profiles
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `device_settings` (" +
                        "`deviceAddress` TEXT NOT NULL, " +
                        "`sensitivity` REAL NOT NULL, " +
                        "`smoothing` REAL NOT NULL, " +
                        "`deadZone` REAL NOT NULL, " +
                        "`acceleration` REAL NOT NULL, " +
                        "`scrollSpeed` REAL NOT NULL, " +
                        "`invertX` INTEGER NOT NULL, " +
                        "`invertY` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`deviceAddress`))"
                )
            }
        }

        /**
         * Test hook: closes and clears the cached database instance so each
         * test starts from a clean state (Robolectric recreates the app file
         * system per test, so a stale singleton would point at a deleted file).
         */
        @androidx.annotation.VisibleForTesting
        fun resetForTesting() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "air_mouse_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pre-populate with default data
                        scope.launch(Dispatchers.IO) {
                            val dao = INSTANCE?.airMouseDao()
                            dao?.updateSettings(SettingsEntity())

                            // Insert standard useful shortcuts
                            dao?.insertShortcut(ShortcutEntity(name = "Copy (Ctrl+C)", modifiers = 0x01, keyCodes = "6"))
                            dao?.insertShortcut(ShortcutEntity(name = "Paste (Ctrl+V)", modifiers = 0x01, keyCodes = "25"))
                            dao?.insertShortcut(ShortcutEntity(name = "Undo (Ctrl+Z)", modifiers = 0x01, keyCodes = "29"))
                            dao?.insertShortcut(ShortcutEntity(name = "Switch App (Alt+Tab)", modifiers = 0x04, keyCodes = "43"))
                            dao?.insertShortcut(ShortcutEntity(name = "Show Desktop (Win+D)", modifiers = 0x08, keyCodes = "7"))
                            dao?.insertShortcut(ShortcutEntity(name = "Lock Device (Win+L)", modifiers = 0x08, keyCodes = "15"))
                            dao?.insertShortcut(ShortcutEntity(name = "Task Manager (Ctrl+Alt+Del)", modifiers = 0x05, keyCodes = "76"))
                            // System keys. (No Sleep shortcut: the HID System Sleep
                            // usage 0x82 can't be sent in the keyboard report, and the
                            // old "68" was really F12.)
                            dao?.insertShortcut(ShortcutEntity(name = "Browser Back", modifiers = 0, keyCodes = "161"))
                            dao?.insertShortcut(ShortcutEntity(name = "Browser Forward", modifiers = 0, keyCodes = "162"))
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
