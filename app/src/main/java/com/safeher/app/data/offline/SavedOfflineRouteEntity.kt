package com.safeher.app.data.offline

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

@Entity(tableName = "saved_offline_routes")
data class SavedOfflineRouteEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val routeId: String,
    val routeName: String,
    val originLat: Double,
    val originLng: Double,
    val destLat: Double,
    val destLng: Double,
    val destinationAddress: String,
    val transportMode: String = "driving",
    val safeRatingCount: Int = 1,
    val isOfflineReady: Boolean = false,
    val compositeScore: Double = 0.0,
    val riskLabel: String = "Low Risk (Safest)",
    val distance: String = "",
    val duration: String = "",
    val lightingScore: Double = 0.0,
    val crowdDensity: String = "",
    val trafficCondition: String = "",
    val trafficScore: Double = 0.85,
    val humanPresenceScore: Double = 0.0,
    val activityDensityScore: Double = 0.0,
    val pedestrianScore: Double = 0.0,
    val confidenceScore: Double = 1.0,
    val sosHotspotScore: Double? = null,
    val sosHotspotCount: Int = 0,
    val viaRoute: String = "",
    val majorAreasCovered: String = "",
    val routePointsJson: String = "[]",
    val turnStepsJson: String = "[]",
    val firstRatedAt: Long = System.currentTimeMillis(),
    val lastRatedAt: Long = System.currentTimeMillis()
)

@Dao
interface SavedOfflineRouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: SavedOfflineRouteEntity)

    @Update
    suspend fun updateRoute(route: SavedOfflineRouteEntity)

    @Query("SELECT * FROM saved_offline_routes WHERE userId = :userId ORDER BY lastRatedAt DESC")
    suspend fun getAllSavedRoutes(userId: String): List<SavedOfflineRouteEntity>

    @Query("SELECT * FROM saved_offline_routes WHERE userId = :userId AND isOfflineReady = 1 ORDER BY lastRatedAt DESC")
    suspend fun getOfflineReadyRoutes(userId: String): List<SavedOfflineRouteEntity>

    @Query("SELECT * FROM saved_offline_routes WHERE userId = :userId AND LOWER(destinationAddress) = LOWER(:destAddress) LIMIT 1")
    suspend fun findMatchingRoute(userId: String, destAddress: String): SavedOfflineRouteEntity?

    @Query("""
        SELECT * FROM saved_offline_routes 
        WHERE userId = :userId 
        AND destLat BETWEEN :minDestLat AND :maxDestLat 
        AND destLng BETWEEN :minDestLng AND :maxDestLng 
        ORDER BY safeRatingCount DESC 
        LIMIT 1
    """)
    suspend fun findMatchingRouteByCoords(
        userId: String,
        minDestLat: Double,
        maxDestLat: Double,
        minDestLng: Double,
        maxDestLng: Double
    ): SavedOfflineRouteEntity?

    @Query("DELETE FROM saved_offline_routes WHERE id = :id")
    suspend fun deleteRoute(id: String)
}
