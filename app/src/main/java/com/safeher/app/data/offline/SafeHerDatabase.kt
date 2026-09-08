package com.safeher.app.data.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PendingSync::class], version = 1, exportSchema = false)
abstract class SafeHerDatabase : RoomDatabase() {
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        @Volatile private var instance: SafeHerDatabase? = null

        fun get(context: Context): SafeHerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SafeHerDatabase::class.java, "safeher_offline.db")
                .build().also { instance = it }
        }
    }
}