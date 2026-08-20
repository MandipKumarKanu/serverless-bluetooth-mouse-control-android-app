package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for Bluetooth connection history.
 *
 * Wraps [AirMouseDao] so the ViewModel never touches the DAO directly
 * for connection-history operations.
 */
class ConnectionHistoryRepository(private val dao: AirMouseDao) {

    /** Observable list of recent connections (newest first, max 10). */
    val recentConnections: Flow<List<ConnectionHistoryEntity>> = dao.getRecentConnectionsFlow()

    /** Record a connection, keeping only the most recent entry per device. */
    suspend fun upsertConnection(connection: ConnectionHistoryEntity) =
        dao.upsertConnection(connection)

    /** Remove duplicate rows from pre-fix data (one-time cleanup). */
    suspend fun dedupeConnectionHistory() = dao.dedupeConnectionHistory()

    /** Clear all connection history. */
    suspend fun clear() = dao.clearConnectionHistory()
}
