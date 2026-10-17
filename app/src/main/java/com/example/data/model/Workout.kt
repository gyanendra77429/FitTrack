package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Workout(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "walking", // walking, running, cycling, stretching
    val durationSeconds: Long = 0L,
    val steps: Long = 0L,
    val distanceMeters: Double = 0.0,
    val calories: Double = 0.0,
    val avgPaceSecondsPerKm: Double = 0.0,
    val date: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)
