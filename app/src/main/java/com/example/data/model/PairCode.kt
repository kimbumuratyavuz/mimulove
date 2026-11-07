package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
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

fun DocumentSnapshot.toPairCodeModel(): PairCode? {
    if (!exists()) return null
    return PairCode(
        code = getString("code")?.takeIf { it.isNotBlank() } ?: id,
        userId = getString("userId") ?: "",
        userName = getString("userName") ?: "",
        createdAt = runCatching { getDate("createdAt") }.getOrNull()
    )
}
