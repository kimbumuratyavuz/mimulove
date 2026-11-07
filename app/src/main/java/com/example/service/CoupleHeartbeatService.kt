package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.Couple
import com.example.data.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CoupleHeartbeatService : Service() {

    private var userListener: ListenerRegistration? = null
    private var coupleListener: ListenerRegistration? = null

    private var lastKnownHeartTimestamp: Long = 0L
    private var lastKnownNoteTimestamp: Long = 0L
    private var currentCoupleId: String = ""
    private var myUserId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CoupleNotificationHelper.createNotificationChannels(this)
        try {
            val notification = CoupleNotificationHelper.buildServiceNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    CoupleNotificationHelper.SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(CoupleNotificationHelper.SERVICE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("CoupleHeartbeatService", "startForeground in onCreate failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = CoupleNotificationHelper.buildServiceNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    CoupleNotificationHelper.SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(CoupleNotificationHelper.SERVICE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("CoupleHeartbeatService", "startForeground in onStartCommand failed: ${e.message}")
        }

        val uid = intent?.getStringExtra(EXTRA_USER_ID) ?: Firebase.auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            myUserId = uid
            startFirestoreListeners(uid)
        }

        return START_STICKY
    }

    private fun startFirestoreListeners(uid: String) {
        val dbId = getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(dbId)

        userListener?.remove()
        userListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val user = snapshot.toObject(User::class.java) ?: return@addSnapshotListener
                val newCoupleId = user.coupleId

                if (newCoupleId.isBlank()) {
                    currentCoupleId = ""
                    coupleListener?.remove()
                } else if (newCoupleId != currentCoupleId) {
                    val isFirstAttachment = currentCoupleId.isEmpty()
                    currentCoupleId = newCoupleId
                    // If service was already running and coupleId just got populated by partner
                    if (!isFirstAttachment) {
                        CoupleNotificationHelper.showPairRequestNotification(
                            this@CoupleHeartbeatService,
                            partnerName = "Sevdiğin"
                        )
                    }
                    listenToCouple(firestore, newCoupleId)
                }
            }
    }

    private fun listenToCouple(firestore: FirebaseFirestore, coupleId: String) {
        coupleListener?.remove()
        coupleListener = firestore.collection("couples").document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val couple = snapshot.toObject(Couple::class.java) ?: return@addSnapshotListener

                if (!couple.isMatched) {
                    currentCoupleId = ""
                    coupleListener?.remove()
                    return@addSnapshotListener
                }

                // Detect partner's heart
                val heartSender = couple.lastHeartSenderId
                val heartTime = couple.lastHeartTimestamp

                if (heartSender.isNotBlank() && heartSender != myUserId && heartTime > lastKnownHeartTimestamp) {
                    val isFirstSnapshot = (lastKnownHeartTimestamp == 0L)
                    lastKnownHeartTimestamp = heartTime
                    // Only alert if this is an actual event after service attached (or within the last 15 seconds)
                    val isRecent = System.currentTimeMillis() - heartTime < 15_000
                    if (!isFirstSnapshot || isRecent) {
                        val senderName = if (heartSender == couple.user1Id) couple.user1Name else couple.user2Name
                        CoupleNotificationHelper.showHeartReceivedNotification(
                            this,
                            senderName = senderName.ifBlank { "Aşkın" },
                            noteText = couple.lastNoteText,
                            timestamp = heartTime
                        )
                    }
                }

                // Detect partner's note if updated separately
                val noteSender = couple.lastNoteSenderId
                val noteTime = couple.lastNoteTimestamp
                if (noteSender.isNotBlank() && noteSender != myUserId && noteTime > lastKnownNoteTimestamp) {
                    lastKnownNoteTimestamp = noteTime
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
        coupleListener?.remove()
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"

        fun start(context: Context, userId: String) {
            try {
                val intent = Intent(context, CoupleHeartbeatService::class.java).apply {
                    putExtra(EXTRA_USER_ID, userId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("CoupleHeartbeatService", "Service start failed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CoupleHeartbeatService::class.java)
            context.stopService(intent)
        }
    }
}
