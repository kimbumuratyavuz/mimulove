package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Couple
import com.example.data.model.PairCode
import com.example.data.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
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

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Google ile oturum açılmalıdır.")
    }

    suspend fun getOrCreateUser(uid: String, displayName: String, email: String): User {
        val docRef = db.collection("users").document(uid)
        val snapshot = docRef.get().await()

        if (snapshot.exists()) {
            val existing = snapshot.toObject(User::class.java)
            if (existing != null) {
                // Ensure pair_codes entry exists even if user document existed from earlier migration
                if (existing.pairCode.isNotBlank()) {
                    try {
                        val pairCodeRef = db.collection("pair_codes").document(existing.pairCode)
                        val codeDoc = pairCodeRef.get().await()
                        if (!codeDoc.exists()) {
                            pairCodeRef.set(
                                PairCode(
                                    code = existing.pairCode,
                                    userId = uid,
                                    userName = existing.displayName.ifBlank { displayName.ifBlank { "Aşkım" } },
                                    createdAt = Date()
                                )
                            ).await()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("CoupleRepository", "pair_codes sync error: ${e.message}")
                    }
                }
                return existing
            }
        }

        // Generate 6-character random alphanumeric code
        val generatedCode = generateUniqueCode()
        val newUser = User(
            userId = uid,
            displayName = displayName.ifBlank { "Aşkım" },
            email = email,
            pairCode = generatedCode,
            coupleId = "",
            createdAt = Date(),
            updatedAt = Date()
        )

        docRef.set(newUser).await()

        // Also register in pair_codes for partner lookup
        val pairCodeRef = db.collection("pair_codes").document(generatedCode)
        val pairCodeEntity = PairCode(
            code = generatedCode,
            userId = uid,
            userName = newUser.displayName,
            createdAt = Date()
        )
        pairCodeRef.set(pairCodeEntity).await()

        return newUser
    }

    fun observeUser(uid: String): Flow<User?> {
        return db.collection("users").document(uid)
            .snapshots()
            .map { it.toObject(User::class.java) }
    }

    fun observeCouple(coupleId: String): Flow<Couple?> {
        return db.collection("couples").document(coupleId)
            .snapshots()
            .map { it.toObject(Couple::class.java) }
    }

    suspend fun pairWithCode(currentUser: User, partnerCode: String): Result<Couple> {
        return try {
            val cleanCode = partnerCode.trim().uppercase()
            if (cleanCode.length != 6) {
                return Result.failure(IllegalArgumentException("Eşleşme kodu 6 haneli olmalıdır."))
            }

            if (cleanCode == currentUser.pairCode) {
                return Result.failure(IllegalArgumentException("Kendi eşleşme kodunuzla eşleşemezsiniz."))
            }

            // Look up partner code
            val codeDoc = db.collection("pair_codes").document(cleanCode).get().await()
            if (!codeDoc.exists()) {
                return Result.failure(IllegalArgumentException("Bu koda ait kullanıcı bulunamadı. Lütfen kodu kontrol edin."))
            }

            val partnerCodeEntity = codeDoc.toObject(PairCode::class.java)
                ?: return Result.failure(IllegalArgumentException("Kod bilgisi okunamadı."))

            val partnerUserId = partnerCodeEntity.userId
            val partnerUserName = partnerCodeEntity.userName

            // Deterministic coupleId
            val coupleId = listOf(currentUser.userId, partnerUserId).sorted().joinToString("_")

            val coupleDocRef = db.collection("couples").document(coupleId)
            val couple = Couple(
                coupleId = coupleId,
                user1Id = currentUser.userId,
                user2Id = partnerUserId,
                user1Name = currentUser.displayName,
                user2Name = partnerUserName,
                anniversaryDate = System.currentTimeMillis(),
                isMatched = true,
                lastHeartSenderId = currentUser.userId,
                lastHeartSenderName = currentUser.displayName,
                lastHeartTimestamp = System.currentTimeMillis(),
                lastHeartCount = 1L,
                lastNoteText = "Biz artık biriz ❤️",
                lastNoteSenderId = currentUser.userId,
                lastNoteSenderName = currentUser.displayName,
                lastNoteTimestamp = System.currentTimeMillis(),
                createdAt = Date(),
                updatedAt = Date()
            )

            // Save couple document
            coupleDocRef.set(couple).await()

            // Update both users with coupleId
            db.collection("users").document(currentUser.userId)
                .update("coupleId", coupleId, "updatedAt", FieldValue.serverTimestamp())
                .await()

            db.collection("users").document(partnerUserId)
                .update("coupleId", coupleId, "updatedAt", FieldValue.serverTimestamp())
                .await()

            Result.success(couple)
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
        // Set isMatched = false first so both live listeners catch it immediately
        db.collection("couples").document(coupleId)
            .update("isMatched", false, "updatedAt", FieldValue.serverTimestamp())
            .await()

        db.collection("users").document(user1Id)
            .update("coupleId", "", "updatedAt", FieldValue.serverTimestamp())
            .await()

        db.collection("users").document(user2Id)
            .update("coupleId", "", "updatedAt", FieldValue.serverTimestamp())
            .await()
    }

    private fun generateUniqueCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Excluded ambiguous chars: 0, O, 1, I
        val random = SecureRandom()
        return (1..6)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }
}
