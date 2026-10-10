package com.safeher.app.data.offline

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "safest_routes")
data class SafestRouteEntity(
    @PrimaryKey
    val id: String,
    val journeyId: String,
    val userId: String,
    val routeId: String,
    val routeName: String,
    val originLat: Double,
    val originLng: Double,
    val destLat: Double,
    val destLng: Double,
    val destinationAddress: String,
    val transportMode: String,
    val compositeScore: Double,
    val riskLabel: String,
    val distance: String,
    val duration: String,
    val markedSafeAt: Long
)

@Dao
interface SafestRouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSafestRoute(route: SafestRouteEntity)

    @Query("SELECT * FROM safest_routes ORDER BY markedSafeAt DESC")
    suspend fun getAllSafestRoutes(): List<SafestRouteEntity>

    @Query("SELECT * FROM safest_routes WHERE routeId = :routeId LIMIT 1")
    suspend fun getSafestRouteById(routeId: String): SafestRouteEntity?
}
