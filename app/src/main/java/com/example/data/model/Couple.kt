package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class Couple(
    @DocumentId val coupleId: String = "",
    val user1Id: String = "",
    val user2Id: String = "",
    val user1Name: String = "",
    val user2Name: String = "",
    val anniversaryDate: Long = 0L,
    val isMatched: Boolean = false,
    val lastHeartSenderId: String = "",
    val lastHeartSenderName: String = "",
    val lastHeartTimestamp: Long = 0L,
    val lastHeartCount: Long = 0L,
    val lastNoteText: String = "",
    val lastNoteSenderId: String = "",
    val lastNoteSenderName: String = "",
    val lastNoteTimestamp: Long = 0L,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)
