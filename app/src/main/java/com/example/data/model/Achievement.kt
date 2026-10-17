package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Achievement(
    @DocumentId val id: String = "",
    val badgeId: String = "",
    val title: String = "",
    val description: String = "",
    val isUnlocked: Boolean = false,
    @ServerTimestamp val unlockedAt: Timestamp? = null
)
