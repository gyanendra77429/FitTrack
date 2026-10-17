package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Fitness Enthusiast",
    val dailyStepGoal: Long = 10000L,
    val preferredUnit: String = "km", // "km" or "mi"
    val strideLengthMeters: Double = 0.76, // Standard average stride: 76cm
    val age: Long? = 28L,
    val heightCm: Double? = 175.0,
    val weightKg: Double? = 70.0,
    val stepRemindersEnabled: Boolean = true,
    val themePreference: String = "system", // "system", "light", "dark"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
