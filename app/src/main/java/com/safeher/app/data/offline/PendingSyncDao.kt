package com.safeher.app.data.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingSyncDao {
    @Insert
    suspend fun insert(sync: PendingSync)

    @Query("SELECT * FROM pending_syncs WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsynced(): List<PendingSync>

    @Query("UPDATE pending_syncs SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
}