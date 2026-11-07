package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Couple
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

    /**
     * Purges any legacy/old couple documents from Firebase so users start clean
     * and never have leftover duplicate pairings.
     */
    private suspend fun purgeLegacyCouplesForUser(uid: String, existingCoupleId: String) {
        // Explicitly delete known old couple document if accessible
        runCatching {
            val legacyRef = db.collection("couples")
                .document("HGIJb0QBltR7ZnIavXGzU5UfJDH2_YpkkCvLFr4VbsGvcMj6KcgaewqA3")
            val snap = legacyRef.get().await()
            if (snap.exists() && (snap.getLong("schemaVersion") ?: 0L) < Couple.CURRENT_SCHEMA_VERSION) {
                legacyRef.delete().await()
            }
        }

        // Delete any legacy couples where user is user1Id or user2Id
        val asUser1 = runCatching {
            db.collection("couples").whereEqualTo("user1Id", uid).get().await()
        }.getOrNull()
        asUser1?.documents?.forEach { doc ->
            if ((doc.getLong("schemaVersion") ?: 0L) < Couple.CURRENT_SCHEMA_VERSION) {
                runCatching { doc.reference.delete().await() }
            }
        }

        val asUser2 = runCatching {
            db.collection("couples").whereEqualTo("user2Id", uid).get().await()
        }.getOrNull()
        asUser2?.documents?.forEach { doc ->
            if ((doc.getLong("schemaVersion") ?: 0L) < Couple.CURRENT_SCHEMA_VERSION) {
                runCatching { doc.reference.delete().await() }
            }
        }

        // Validate user's current coupleId; if it points to a deleted or legacy doc, clear it
        if (existingCoupleId.isNotBlank()) {
            val cDoc = runCatching {
                db.collection("couples").document(existingCoupleId).get().await()
            }.getOrNull()
            val validCouple = cDoc?.toCoupleModel()
            if (validCouple == null || (!validCouple.isMatched && !validCouple.isPending)) {
                runCatching {
                    db.collection("users").document(uid)
                        .update("coupleId", "", "updatedAt", FieldValue.serverTimestamp())
                        .await()
                }
            }
        }
    }

    /**
     * Returns the user's current active (matched or pending) couple, if any.
     */
    private suspend fun findActiveCoupleForUser(uid: String, coupleIdHint: String = ""): Couple? {
        if (coupleIdHint.isNotBlank()) {
            val direct = runCatching {
                db.collection("couples").document(coupleIdHint).get().await()
            }.getOrNull()?.toCoupleModel()
            if (direct != null && (direct.isMatched || direct.isPending)) {
                return direct
            }
        }

        val list1 = runCatching {
            db.collection("couples").whereEqualTo("user1Id", uid).get().await()
        }.getOrNull()?.documents?.mapNotNull { it.toCoupleModel() } ?: emptyList()

        val list2 = runCatching {
            db.collection("couples").whereEqualTo("user2Id", uid).get().await()
        }.getOrNull()?.documents?.mapNotNull { it.toCoupleModel() } ?: emptyList()

        return (list1 + list2)
            .filter { it.isMatched || it.isPending }
            .sortedWith(
                compareByDescending<Couple> { it.isMatched }
                    .thenByDescending { it.updatedAt?.time ?: it.anniversaryDate }
            )
            .firstOrNull()
    }

    suspend fun getOrCreateUser(uid: String, displayName: String, email: String): User {
        val docRef = db.collection("users").document(uid)
        val snapshot = docRef.get().await()
        val safeName = displayName.ifBlank { "Aşkım" }

        if (snapshot.exists()) {
            val existing = snapshot.toUserModel()
            if (existing != null) {
                // Clean up any pre-v2 legacy couple matches from Firebase
                purgeLegacyCouplesForUser(uid, existing.coupleId)

                val finalCode = existing.pairCode.ifBlank { generateUniqueCode() }
                val finalName = existing.displayName.ifBlank { safeName }

                // Re-read active couple after purge
                val activeCouple = findActiveCoupleForUser(uid)
                val resolvedCoupleId = activeCouple?.coupleId ?: ""

                if (existing.pairCode.isBlank() ||
                    existing.displayName.isBlank() ||
                    existing.coupleId != resolvedCoupleId
                ) {
                    runCatching {
                        docRef.set(
                            mapOf(
                                "userId" to uid,
                                "displayName" to finalName,
                                "email" to existing.email.ifBlank { email },
                                "pairCode" to finalCode,
                                "coupleId" to resolvedCoupleId,
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
                    pairCode = finalCode,
                    coupleId = resolvedCoupleId
                )
            }
        }

        // Purge any legacy couples even for a recreated user doc
        purgeLegacyCouplesForUser(uid, "")

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
     * Observes the single active or pending Couple document where this user is user1Id or user2Id.
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
     * Blocks pairing if EITHER the current user OR the partner is already matched with someone else
     * and hasn't ended their existing pairing.
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

            // 1. Ensure currentUser does NOT already have an active match
            val myActiveCouple = findActiveCoupleForUser(currentUser.userId, currentUser.coupleId)
            if (myActiveCouple != null && myActiveCouple.isMatched) {
                return Result.failure(
                    IllegalStateException(
                        "Zaten aktif bir eşleşmeniz bulunuyor. Başka bir eş kodu ile eşleşmek için önce mevcut eşleşmenizi sonlandırmalısınız."
                    )
                )
            }

            // 2. Look up partner code in pair_codes, with fallback to users collection
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

            // Deterministic coupleId between these two users
            val coupleId = listOf(currentUser.userId, partnerUserId).sorted().joinToString("_")

            // If currentUser has a pending request with a DIFFERENT user, block until cancelled
            if (myActiveCouple != null && myActiveCouple.isPending && myActiveCouple.coupleId != coupleId) {
                return Result.failure(
                    IllegalStateException(
                        "Başka bir kullanıcıya gönderilmiş bekleyen bir eşleşme isteğiniz var. Önce mevcut isteği iptal etmelisiniz."
                    )
                )
            }

            // 3. Check partner's user profile to verify partner is NOT already matched with someone else
            val partnerSnap = runCatching {
                db.collection("users").document(partnerUserId).get().await()
            }.getOrNull()
            val partnerUser = partnerSnap?.toUserModel()
            if (partnerUser != null) {
                if (partnerUser.displayName.isNotBlank()) {
                    partnerUserName = partnerUser.displayName
                }
                if (partnerUser.coupleId.isNotBlank() && partnerUser.coupleId != coupleId) {
                    // Verify if partner's other couple is legacy or truly active
                    val partnerOtherCoupleDoc = runCatching {
                        db.collection("couples").document(partnerUser.coupleId).get().await()
                    }.getOrNull()
                    val isLegacy = partnerOtherCoupleDoc != null &&
                        partnerOtherCoupleDoc.exists() &&
                        (partnerOtherCoupleDoc.getLong("schemaVersion") ?: 0L) < Couple.CURRENT_SCHEMA_VERSION

                    if (!isLegacy) {
                        return Result.failure(
                            IllegalStateException(
                                "${partnerUser.displayName.ifBlank { "Bu kullanıcı" }} şu anda başka biriyle eşleşmiş durumda. Eşleşmeyi sonlandırmadan yeni bir eşleşme yapılamaz."
                            )
                        )
                    }
                }
            }

            val safeMyName = currentUser.displayName.ifBlank { "Aşkım" }
            val safePartnerName = partnerUserName.ifBlank { "Sevdiğin" }

            val coupleDocRef = db.collection("couples").document(coupleId)
            val now = System.currentTimeMillis()

            // Check if a couple doc between these two users already exists
            val existingCoupleSnap = runCatching { coupleDocRef.get().await() }.getOrNull()
            val existingCouple = existingCoupleSnap?.toCoupleModel()

            if (existingCouple != null) {
                if (existingCouple.isMatched) {
                    return Result.failure(IllegalStateException("Bu kullanıcı ile zaten aktif bir eşleşmeniz var."))
                }
                if (existingCouple.isPending && existingCouple.pendingRequesterId == partnerUserId) {
                    // Partner had already sent us a request -> approve it!
                    return acceptPairRequest(existingCouple, currentUser)
                }
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
                schemaVersion = Couple.CURRENT_SCHEMA_VERSION,
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
                "schemaVersion" to Couple.CURRENT_SCHEMA_VERSION,
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
     * Ensures neither user has another active match so hearts only ever go to one partner.
     */
    suspend fun acceptPairRequest(couple: Couple, currentUser: User): Result<Couple> {
        return try {
            // Verify currentUser has no other active match
            val myOtherActive = findActiveCoupleForUser(currentUser.userId, currentUser.coupleId)
            if (myOtherActive != null && myOtherActive.coupleId != couple.coupleId && myOtherActive.isMatched) {
                return Result.failure(
                    IllegalStateException(
                        "Zaten aktif bir eşleşmeniz bulunuyor. Önce mevcut eşleşmenizi sonlandırmalısınız."
                    )
                )
            }

            val partnerId = if (couple.user1Id == currentUser.userId) couple.user2Id else couple.user1Id
            if (partnerId.isNotBlank()) {
                val partnerSnap = runCatching {
                    db.collection("users").document(partnerId).get().await()
                }.getOrNull()
                val partnerUser = partnerSnap?.toUserModel()
                if (partnerUser != null &&
                    partnerUser.coupleId.isNotBlank() &&
                    partnerUser.coupleId != couple.coupleId
                ) {
                    return Result.failure(
                        IllegalStateException(
                            "Karşı taraf başka biriyle eşleşmiş durumda. Önce mevcut eşleşmesini sonlandırmalıdır."
                        )
                    )
                }
            }

            val now = System.currentTimeMillis()
            val safeMyName = currentUser.displayName.ifBlank { "Aşkım" }
            val anniversary = if (couple.anniversaryDate > 0L) couple.anniversaryDate else now

            val updates = mapOf(
                "isMatched" to true,
                "matched" to true,
                "status" to Couple.STATUS_MATCHED,
                "schemaVersion" to Couple.CURRENT_SCHEMA_VERSION,
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

            // Remove any other non-matched/stale couple docs for this user so only 1 couple doc exists
            val stale1 = runCatching {
                db.collection("couples").whereEqualTo("user1Id", currentUser.userId).get().await()
            }.getOrNull()?.documents ?: emptyList()
            val stale2 = runCatching {
                db.collection("couples").whereEqualTo("user2Id", currentUser.userId).get().await()
            }.getOrNull()?.documents ?: emptyList()
            (stale1 + stale2).forEach { doc ->
                if (doc.id != couple.coupleId) {
                    runCatching { doc.reference.delete().await() }
                }
            }

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
                schemaVersion = Couple.CURRENT_SCHEMA_VERSION,
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
     * Rejects an incoming pairing request or cancels an outgoing pending request,
     * and deletes the pending document from Firestore.
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
                db.collection("couples").document(couple.coupleId).delete().await()
            }

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
        // Set isMatched = false first so live listeners catch it, then delete the couple document
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

        runCatching {
            db.collection("couples").document(coupleId).delete().await()
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
