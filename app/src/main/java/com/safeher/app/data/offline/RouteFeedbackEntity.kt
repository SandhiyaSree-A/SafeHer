package com.safeher.app.data.offline

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "route_feedback")
data class RouteFeedbackEntity(
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
    val wasSafe: Boolean,
    val reasonsString: String,
    val customReason: String,
    val submittedAt: Long
)

@Dao
interface RouteFeedbackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: RouteFeedbackEntity)

    @Query("SELECT * FROM route_feedback ORDER BY submittedAt DESC")
    suspend fun getAllFeedback(): List<RouteFeedbackEntity>

    @Query("SELECT * FROM route_feedback WHERE journeyId = :journeyId")
    suspend fun getFeedbackByJourneyId(journeyId: String): List<RouteFeedbackEntity>
}
