package com.safeher.app.data.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_syncs")
data class PendingSync(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)