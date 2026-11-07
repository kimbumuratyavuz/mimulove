package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Couple
import com.example.data.model.PairCode
import com.example.data.model.User
import com.example.data.model.toCoupleModel
import com.example.data.model.toPairCodeModel
import com.example.data.model.toUserModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date

class CoupleRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    suspend fun getOrCreateUser(uid: String, displayName: String, email: String): User {
        val docRef = db.collection("users").document(uid)
        val snapshot = docRef.get().await()
        val safeName = displayName.ifBlank { "Aşkım" }

        if (snapshot.exists()) {
            val existing = snapshot.toUserModel()
            if (existing != null) {
                val finalCode = existing.pairCode.ifBlank { generateUniqueCode() }
                val finalName = existing.displayName.ifBlank { safeName }

                if (existing.pairCode.isBlank() || existing.displayName.isBlank()) {
                    runCatching {
                        docRef.set(
                            mapOf(
                                "userId" to uid,
                                "displayName" to finalName,
                                "email" to existing.email.ifBlank { email },
                                "pairCode" to finalCode,
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        ).await()
                    }
                }

                // Ensure pair_codes entry exists and points to this user
                if (finalCode.isNotBlank()) {
                    runCatching {
                        val pairCodeRef = db.collection("pair_codes").document(finalCode)
                        val codeDoc = pairCodeRef.get().await()
                        if (!codeDoc.exists()) {
                            pairCodeRef.set(
                                mapOf(
                                    "code" to finalCode,
                                    "userId" to uid,
                                    "userName" to finalName,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }.onFailure { e ->
                        android.util.Log.w("CoupleRepository", "pair_codes sync warning: ${e.message}")
                    }
                }

                return existing.copy(
                    userId = uid,
                    displayName = finalName,
                    pairCode = finalCode
                )
            }
        }

        // Generate 6-character random alphanumeric code
        val generatedCode = generateUniqueCode()
        val now = Date()
        val newUser = User(
            userId = uid,
            displayName = safeName,
            email = email,
            pairCode = generatedCode,
            coupleId = "",
            createdAt = now,
            updatedAt = now
        )

        docRef.set(
            mapOf(
                "userId" to uid,
                "displayName" to newUser.displayName,
                "email" to newUser.email,
                "pairCode" to generatedCode,
                "coupleId" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Also register in pair_codes for partner lookup
        runCatching {
            val pairCodeRef = db.collection("pair_codes").document(generatedCode)
            pairCodeRef.set(
                mapOf(
                    "code" to generatedCode,
                    "userId" to uid,
                    "userName" to newUser.displayName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }.onFailure { e ->
            android.util.Log.w("CoupleRepository", "pair_codes create warning: ${e.message}")
        }

        return newUser
    }

    fun observeUser(uid: String): Flow<User?> {
        return db.collection("users").document(uid)
            .snapshots()
            .map { it.toUserModel() }
    }

    fun observeCouple(coupleId: String): Flow<Couple?> {
        return db.collection("couples").document(coupleId)
            .snapshots()
            .map { it.toCoupleModel() }
    }

    /**
     * Observes any active or pending Couple document where this user is user1Id or user2Id
     * (plus optional direct coupleId document), so pairing works even if cross-user profile
     * updates are delayed or blocked by Firestore security rules.
     */
    fun observeCoupleForUser(uid: String, coupleIdHint: String = ""): Flow<Couple?> = callbackFlow {
        var user1Couples: Map<String, Couple> = emptyMap()
        var user2Couples: Map<String, Couple> = emptyMap()
        var directCouple: Couple? = null

        fun emitBestCouple() {
            val merged = mutableMapOf<String, Couple>()
            user1Couples.forEach { (k, v) -> merged[k] = v }
            user2Couples.forEach { (k, v) -> merged[k] = v }
            directCouple?.let { c ->
                if (c.coupleId.isNotBlank()) {
                    merged[c.coupleId] = c
                }
            }

            val best = merged.values
                .filter { it.isMatched || it.isPending }
                .sortedWith(
                    compareByDescending<Couple> { it.isMatched }
                        .thenByDescending { it.updatedAt?.time ?: it.anniversaryDate }
                )
                .firstOrNull()

            trySend(best)
        }

        val sub1 = db.collection("couples")
            .whereEqualTo("user1Id", uid)
            .addSnapshotListener { snap, err ->
                if (err == null && snap != null) {
                    user1Couples = snap.documents
                        .mapNotNull { it.toCoupleModel() }
                        .associateBy { it.coupleId }
                    emitBestCouple()
                }
            }

        val sub2 = db.collection("couples")
            .whereEqualTo("user2Id", uid)
            .addSnapshotListener { snap, err ->
                if (err == null && snap != null) {
                    user2Couples = snap.documents
                        .mapNotNull { it.toCoupleModel() }
                        .associateBy { it.coupleId }
                    emitBestCouple()
                }
            }

        val subDirect = if (coupleIdHint.isNotBlank()) {
            db.collection("couples").document(coupleIdHint)
                .addSnapshotListener { snap, err ->
                    if (err == null) {
                        directCouple = snap?.toCoupleModel()
                        emitBestCouple()
                    }
                }
        } else null

        awaitClose {
            sub1.remove()
            sub2.remove()
            subDirect?.remove()
        }
    }

    /**
     * Sends a pairing request to the partner with the given 6-character code.
     * If the partner has ALREADY sent a pending request to currentUser, this immediately approves it!
     */
    suspend fun pairWithCode(currentUser: User, partnerCode: String): Result<Couple> {
        return try {
            val cleanCode = partnerCode.trim().uppercase()
            if (cleanCode.length != 6) {
                return Result.failure(IllegalArgumentException("Eşleşme kodu 6 haneli olmalıdır."))
            }

            if (cleanCode == currentUser.pairCode.uppercase()) {
                return Result.failure(IllegalArgumentException("Kendi eşleşme kodunuzla eşleşemezsiniz."))
            }

            // 1. Look up partner code in pair_codes, with fallback to users collection
            var partnerUserId = ""
            var partnerUserName = ""

            val codeDoc = runCatching {
                db.collection("pair_codes").document(cleanCode).get().await()
            }.getOrNull()

            if (codeDoc != null && codeDoc.exists()) {
                val parsed = codeDoc.toPairCodeModel()
                if (parsed != null && parsed.userId.isNotBlank()) {
                    partnerUserId = parsed.userId
                    partnerUserName = parsed.userName
                }
            }

            // Fallback lookup in users collection if pair_codes didn't have it
            if (partnerUserId.isBlank()) {
                val userQuery = runCatching {
                    db.collection("users")
                        .whereEqualTo("pairCode", cleanCode)
                        .limit(1)
                        .get()
                        .await()
                }.getOrNull()

                val matchedUserDoc = userQuery?.documents?.firstOrNull()
                if (matchedUserDoc != null && matchedUserDoc.exists()) {
                    val u = matchedUserDoc.toUserModel()
                    if (u != null && u.userId.isNotBlank()) {
                        partnerUserId = u.userId
                        partnerUserName = u.displayName
                    }
                }
            }

            if (partnerUserId.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Bu koda ait kullanıcı bulunamadı. Lütfen kodu kontrol edin.")
                )
            }

            if (partnerUserId == currentUser.userId) {
                return Result.failure(
                    IllegalArgumentException("Kendi eşleşme kodunuzla eşleşemezsiniz.")
                )
            }

            // Refresh partner's displayName from users/{partnerUserId} if possible
            runCatching {
                val partnerSnap = db.collection("users").document(partnerUserId).get().await()
                val partnerUser = partnerSnap.toUserModel()
                if (partnerUser != null && partnerUser.displayName.isNotBlank()) {
                    partnerUserName = partnerUser.displayName
                }
            }

            val safeMyName = currentUser.displayName.ifBlank { "Aşkım" }
            val safePartnerName = partnerUserName.ifBlank { "Sevdiğin" }

            // Deterministic coupleId
            val coupleId = listOf(currentUser.userId, partnerUserId).sorted().joinToString("_")
            val coupleDocRef = db.collection("couples").document(coupleId)
            val now = System.currentTimeMillis()

            // Check if partner already created a pending request for us
            val existingCoupleSnap = runCatching { coupleDocRef.get().await() }.getOrNull()
            val existingCouple = existingCoupleSnap?.toCoupleModel()

            if (existingCouple != null &&
                existingCouple.isPending &&
                existingCouple.pendingRequesterId == partnerUserId
            ) {
                // Mutual code entry -> auto-approve!
                return acceptPairRequest(existingCouple, currentUser)
            }

            val pendingCouple = Couple(
                coupleId = coupleId,
                user1Id = currentUser.userId,
                user2Id = partnerUserId,
                user1Name = safeMyName,
                user2Name = safePartnerName,
                anniversaryDate = now,
                isMatched = false,
                status = Couple.STATUS_PENDING,
                pendingRequesterId = currentUser.userId,
                pendingRequesterName = safeMyName,
                lastHeartSenderId = "",
                lastHeartSenderName = "",
                lastHeartTimestamp = 0L,
                lastHeartCount = 0L,
                lastNoteText = "",
                lastNoteSenderId = "",
                lastNoteSenderName = "",
                lastNoteTimestamp = 0L,
                createdAt = Date(now),
                updatedAt = Date(now)
            )

            // Write explicit map so both "isMatched" and "matched" are consistent in Firestore
            val coupleMap = mapOf(
                "coupleId" to coupleId,
                "user1Id" to currentUser.userId,
                "user2Id" to partnerUserId,
                "user1Name" to safeMyName,
                "user2Name" to safePartnerName,
                "anniversaryDate" to now,
                "isMatched" to false,
                "matched" to false,
                "status" to Couple.STATUS_PENDING,
                "pendingRequesterId" to currentUser.userId,
                "pendingRequesterName" to safeMyName,
                "lastHeartSenderId" to "",
                "lastHeartSenderName" to "",
                "lastHeartTimestamp" to 0L,
                "lastHeartCount" to 0L,
                "lastNoteText" to "",
                "lastNoteSenderId" to "",
                "lastNoteSenderName" to "",
                "lastNoteTimestamp" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            coupleDocRef.set(coupleMap).await()

            // Update own user document
            runCatching {
                db.collection("users").document(currentUser.userId)
                    .set(
                        mapOf(
                            "coupleId" to coupleId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }

            // Best-effort update partner user document (will not fail pairing if rules block cross-user write)
            runCatching {
                db.collection("users").document(partnerUserId)
                    .update("coupleId", coupleId, "updatedAt", FieldValue.serverTimestamp())
                    .await()
            }

            Result.success(pendingCouple)
        } catch (e: Exception) {
            android.util.Log.e("CoupleRepository", "pairWithCode failed", e)
            Result.failure(e)
        }
    }

    /**
     * Approves an incoming pairing request and activates the couple match for both devices.
     */
    suspend fun acceptPairRequest(couple: Couple, currentUser: User): Result<Couple> {
        return try {
            val now = System.currentTimeMillis()
            val safeMyName = currentUser.displayName.ifBlank { "Aşkım" }
            val anniversary = if (couple.anniversaryDate > 0L) couple.anniversaryDate else now

            val updates = mapOf(
                "isMatched" to true,
                "matched" to true,
                "status" to Couple.STATUS_MATCHED,
                "anniversaryDate" to anniversary,
                "lastHeartSenderId" to currentUser.userId,
                "lastHeartSenderName" to safeMyName,
                "lastHeartTimestamp" to now,
                "lastHeartCount" to 1L,
                "lastNoteText" to "Biz artık biriz ❤️",
                "lastNoteSenderId" to currentUser.userId,
                "lastNoteSenderName" to safeMyName,
                "lastNoteTimestamp" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("couples").document(couple.coupleId)
                .set(updates, SetOptions.merge())
                .await()

            runCatching {
                db.collection("users").document(currentUser.userId)
                    .set(
                        mapOf(
                            "coupleId" to couple.coupleId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }

            val partnerId = if (couple.user1Id == currentUser.userId) couple.user2Id else couple.user1Id
            if (partnerId.isNotBlank()) {
                runCatching {
                    db.collection("users").document(partnerId)
                        .update("coupleId", couple.coupleId, "updatedAt", FieldValue.serverTimestamp())
                        .await()
                }
            }

            val matchedCouple = couple.copy(
                isMatched = true,
                status = Couple.STATUS_MATCHED,
                anniversaryDate = anniversary,
                lastHeartSenderId = currentUser.userId,
                lastHeartSenderName = safeMyName,
                lastHeartTimestamp = now,
                lastHeartCount = 1L,
                lastNoteText = "Biz artık biriz ❤️",
                lastNoteSenderId = currentUser.userId,
                lastNoteSenderName = safeMyName,
                lastNoteTimestamp = now,
                updatedAt = Date(now)
            )
            Result.success(matchedCouple)
        } catch (e: Exception) {
            android.util.Log.e("CoupleRepository", "acceptPairRequest failed", e)
            Result.failure(e)
        }
    }

    /**
     * Rejects an incoming pairing request or cancels an outgoing pending request.
     */
    suspend fun rejectOrCancelPairRequest(couple: Couple, currentUserId: String): Result<Unit> {
        return try {
            db.collection("couples").document(couple.coupleId)
                .set(
                    mapOf(
                        "isMatched" to false,
                        "matched" to false,
                        "status" to Couple.STATUS_REJECTED,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()

            runCatching {
                db.collection("users").document(currentUserId)
                    .update("coupleId", "", "updatedAt", FieldValue.serverTimestamp())
                    .await()
            }

            val partnerId = if (couple.user1Id == currentUserId) couple.user2Id else couple.user1Id
            if (partnerId.isNotBlank()) {
                runCatching {
                    db.collection("users").document(partnerId)
                        .update("coupleId", "", "updatedAt", FieldValue.serverTimestamp())
                        .await()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendHeart(
        coupleId: String,
        senderId: String,
        senderName: String,
        optionalNote: String = ""
    ) {
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>(
            "lastHeartSenderId" to senderId,
            "lastHeartSenderName" to senderName,
            "lastHeartTimestamp" to now,
            "lastHeartCount" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (optionalNote.isNotBlank()) {
            updates["lastNoteText"] = optionalNote
            updates["lastNoteSenderId"] = senderId
            updates["lastNoteSenderName"] = senderName
            updates["lastNoteTimestamp"] = now
        }

        db.collection("couples").document(coupleId).update(updates).await()
    }

    suspend fun sendLoveNote(
        coupleId: String,
        senderId: String,
        senderName: String,
        noteText: String
    ) {
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "lastNoteText" to noteText,
            "lastNoteSenderId" to senderId,
            "lastNoteSenderName" to senderName,
            "lastNoteTimestamp" to now,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("couples").document(coupleId).update(updates).await()
    }

    suspend fun updateAnniversary(coupleId: String, dateMillis: Long) {
        db.collection("couples").document(coupleId)
            .update(
                "anniversaryDate", dateMillis,
                "updatedAt", FieldValue.serverTimestamp()
            )
            .await()
    }

    suspend fun unlinkCouple(coupleId: String, user1Id: String, user2Id: String) {
        // Set isMatched = false and status = UNLINKED so both live listeners catch it immediately
        db.collection("couples").document(coupleId)
            .set(
                mapOf(
                    "isMatched" to false,
                    "matched" to false,
                    "status" to Couple.STATUS_UNLINKED,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()

        runCatching {
            db.collection("users").document(user1Id)
                .update("coupleId", "", "updatedAt", FieldValue.serverTimestamp())
                .await()
        }

        runCatching {
            db.collection("users").document(user2Id)
                .update("coupleId", "", "updatedAt", FieldValue.serverTimestamp())
                .await()
        }
    }

    private fun generateUniqueCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Excluded ambiguous chars: 0, O, 1, I
        val random = SecureRandom()
        return (1..6)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }
}
