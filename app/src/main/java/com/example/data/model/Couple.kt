package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
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
    @get:PropertyName("isMatched")
    val isMatched: Boolean = false,
    val status: String = "",
    val pendingRequesterId: String = "",
    val pendingRequesterName: String = "",
    val schemaVersion: Long = CURRENT_SCHEMA_VERSION,
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
) {
    val isPending: Boolean
        get() = status == STATUS_PENDING && !isMatched

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_MATCHED = "MATCHED"
        const val STATUS_REJECTED = "REJECTED"
        const val STATUS_UNLINKED = "UNLINKED"
        const val CURRENT_SCHEMA_VERSION = 2L
    }
}

fun DocumentSnapshot.toCoupleModel(): Couple? {
    if (!exists()) return null
    val docVersion = getLong("schemaVersion") ?: 0L
    // Legacy documents (created before schemaVersion 2) are ignored and purged automatically
    if (docVersion < Couple.CURRENT_SCHEMA_VERSION) {
        runCatching { reference.delete() }
        return null
    }

    val statusStr = getString("status") ?: ""
    val rawIsMatched = getBoolean("isMatched") ?: getBoolean("matched") ?: false
    val effectiveMatched = when (statusStr) {
        Couple.STATUS_PENDING, Couple.STATUS_REJECTED, Couple.STATUS_UNLINKED -> false
        Couple.STATUS_MATCHED -> true
        else -> rawIsMatched
    }
    val resolvedStatus = when {
        statusStr.isNotBlank() -> statusStr
        effectiveMatched -> Couple.STATUS_MATCHED
        else -> ""
    }
    val u1Id = getString("user1Id") ?: ""
    val u1Name = getString("user1Name") ?: ""

    return Couple(
        coupleId = id,
        user1Id = u1Id,
        user2Id = getString("user2Id") ?: "",
        user1Name = u1Name,
        user2Name = getString("user2Name") ?: "",
        anniversaryDate = getLong("anniversaryDate") ?: 0L,
        isMatched = effectiveMatched,
        status = resolvedStatus,
        pendingRequesterId = getString("pendingRequesterId")?.takeIf { it.isNotBlank() } ?: u1Id,
        pendingRequesterName = getString("pendingRequesterName")?.takeIf { it.isNotBlank() } ?: u1Name,
        schemaVersion = docVersion,
        lastHeartSenderId = getString("lastHeartSenderId") ?: "",
        lastHeartSenderName = getString("lastHeartSenderName") ?: "",
        lastHeartTimestamp = getLong("lastHeartTimestamp") ?: 0L,
        lastHeartCount = getLong("lastHeartCount") ?: 0L,
        lastNoteText = getString("lastNoteText") ?: "",
        lastNoteSenderId = getString("lastNoteSenderId") ?: "",
        lastNoteSenderName = getString("lastNoteSenderName") ?: "",
        lastNoteTimestamp = getLong("lastNoteTimestamp") ?: 0L,
        createdAt = runCatching { getDate("createdAt") }.getOrNull(),
        updatedAt = runCatching { getDate("updatedAt") }.getOrNull()
    )
}
