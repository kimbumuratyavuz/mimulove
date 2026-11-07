package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class User(
    @DocumentId val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val pairCode: String = "",
    val coupleId: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)

fun DocumentSnapshot.toUserModel(): User? {
    if (!exists()) return null
    return User(
        userId = getString("userId")?.takeIf { it.isNotBlank() } ?: id,
        displayName = getString("displayName") ?: "",
        email = getString("email") ?: "",
        pairCode = getString("pairCode") ?: "",
        coupleId = getString("coupleId") ?: "",
        createdAt = runCatching { getDate("createdAt") }.getOrNull(),
        updatedAt = runCatching { getDate("updatedAt") }.getOrNull()
    )
}
