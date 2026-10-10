package com.safeher.app.data.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PendingSync::class,
        SafestRouteEntity::class,
        RouteFeedbackEntity::class,
        SavedOfflineRouteEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SafeHerDatabase : RoomDatabase() {
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun safestRouteDao(): SafestRouteDao
    abstract fun routeFeedbackDao(): RouteFeedbackDao
    abstract fun savedOfflineRouteDao(): SavedOfflineRouteDao

    companion object {
        @Volatile private var instance: SafeHerDatabase? = null

        fun get(context: Context): SafeHerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SafeHerDatabase::class.java, "safeher_offline.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}