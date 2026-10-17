package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class DailyActivity(
    @DocumentId val id: String = "",
    val date: String = "",
    val steps: Long = 0L,
    val stepGoal: Long = 10000L,
    val distanceMeters: Double = 0.0,
    val calories: Double = 0.0,
    val activeMinutes: Long = 0L,
    val goalReached: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
