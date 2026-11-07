package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class PairCode(
    @DocumentId val code: String = "",
    val userId: String = "",
    val userName: String = "",
    @ServerTimestamp val createdAt: Date? = null
)
