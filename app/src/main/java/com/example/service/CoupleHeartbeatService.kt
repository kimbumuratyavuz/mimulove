package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.Couple
import com.example.data.model.toCoupleModel
import com.example.data.model.toUserModel
import com.example.ui.overlay.HeartBurstOverlayActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CoupleHeartbeatService : Service() {

    private var userListener: ListenerRegistration? = null
    private var coupleDirectListener: ListenerRegistration? = null
    private var coupleUser1Listener: ListenerRegistration? = null
    private var coupleUser2Listener: ListenerRegistration? = null

    private var currentCoupleId: String = ""
    private var myUserId: String = ""

    private val prefs by lazy {
        getSharedPreferences("mimu_heartbeat_service_prefs", Context.MODE_PRIVATE)
    }

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
            if (myUserId != uid || coupleUser1Listener == null) {
                myUserId = uid
                startFirestoreListeners(uid)
            }
        }

        return START_STICKY
    }

    private fun startFirestoreListeners(uid: String) {
        val dbId = getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(dbId)

        userListener?.remove()
        coupleUser1Listener?.remove()
        coupleUser2Listener?.remove()

        userListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val user = snapshot.toUserModel() ?: return@addSnapshotListener
                val newCoupleId = user.coupleId

                if (newCoupleId.isBlank()) {
                    currentCoupleId = ""
                    coupleDirectListener?.remove()
                } else if (newCoupleId != currentCoupleId) {
                    currentCoupleId = newCoupleId
                    listenToDirectCouple(firestore, newCoupleId)
                }
            }

        coupleUser1Listener = firestore.collection("couples")
            .whereEqualTo("user1Id", uid)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                snap.documents.mapNotNull { it.toCoupleModel() }.forEach { couple ->
                    handleCoupleSnapshot(couple)
                }
            }

        coupleUser2Listener = firestore.collection("couples")
            .whereEqualTo("user2Id", uid)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                snap.documents.mapNotNull { it.toCoupleModel() }.forEach { couple ->
                    handleCoupleSnapshot(couple)
                }
            }
    }

    private fun listenToDirectCouple(firestore: FirebaseFirestore, coupleId: String) {
        coupleDirectListener?.remove()
        coupleDirectListener = firestore.collection("couples").document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val couple = snapshot.toCoupleModel() ?: return@addSnapshotListener
                handleCoupleSnapshot(couple)
            }
    }

    private fun handleCoupleSnapshot(couple: Couple) {
        if (myUserId.isBlank()) return

        // Case 1: Incoming Pending Pair Request for this user
        if (couple.isPending) {
            if (couple.pendingRequesterId.isNotBlank() && couple.pendingRequesterId != myUserId) {
                val pendingKey = "${couple.coupleId}_${couple.anniversaryDate}"
                val lastNotifiedPending = prefs.getString("last_notified_pending", "")
                if (lastNotifiedPending != pendingKey) {
                    prefs.edit().putString("last_notified_pending", pendingKey).apply()
                    val partnerName = couple.pendingRequesterName.ifBlank {
                        if (couple.user1Id == myUserId) couple.user2Name else couple.user1Name
                    }.ifBlank { "Sevdiğin" }
                    CoupleNotificationHelper.showPairRequestNotification(
                        this,
                        partnerName = partnerName
                    )
                }
            }
            return
        }

        if (!couple.isMatched) {
            return
        }

        val myUserName = (if (couple.user1Id == myUserId) couple.user1Name else couple.user2Name)
            .ifBlank { "Aşkım" }

        // Case 2: Partner just accepted our pending request
        val matchKey = "${couple.coupleId}_${couple.anniversaryDate}_matched"
        val lastNotifiedMatch = prefs.getString("last_notified_match", "")
        if (lastNotifiedMatch != matchKey) {
            val wasPendingFromMe = couple.pendingRequesterId == myUserId &&
                (System.currentTimeMillis() - couple.lastHeartTimestamp < 30_000L)
            prefs.edit().putString("last_notified_match", matchKey).apply()
            if (wasPendingFromMe && couple.lastHeartSenderId != myUserId) {
                val partnerName = (if (couple.user1Id == myUserId) couple.user2Name else couple.user1Name)
                    .ifBlank { "Sevdiğin" }
                CoupleNotificationHelper.showPairAcceptedNotification(
                    this,
                    partnerName = partnerName
                )
            }
        }

        // Case 3: Partner sent a Heart
        val heartTimeKey = "last_heart_${couple.coupleId}"
        val lastKnownHeartTimestamp = prefs.getLong(heartTimeKey, 0L)
        val heartSender = couple.lastHeartSenderId
        val heartTime = couple.lastHeartTimestamp

        if (heartTime > 0L && heartTime > lastKnownHeartTimestamp) {
            prefs.edit().putLong(heartTimeKey, heartTime).apply()
            val isInitialAttachOldEvent = (lastKnownHeartTimestamp == 0L) &&
                (System.currentTimeMillis() - heartTime > 30_000L)

            if (heartSender.isNotBlank() && heartSender != myUserId && !isInitialAttachOldEvent) {
                val senderName = couple.lastHeartSenderName.ifBlank {
                    if (heartSender == couple.user1Id) couple.user1Name else couple.user2Name
                }.ifBlank { "Aşkın" }
                val attachedNote = if (couple.lastNoteTimestamp == heartTime) couple.lastNoteText else ""

                // Show status bar notification
                CoupleNotificationHelper.showHeartReceivedNotification(
                    context = this,
                    coupleId = couple.coupleId,
                    myUserId = myUserId,
                    myUserName = myUserName,
                    senderName = senderName,
                    noteText = attachedNote,
                    timestamp = heartTime
                )

                // If app is closed/in background AND screen is ON, pop up the giant exploding heart overlay!
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isScreenOn = powerManager?.isInteractive ?: true
                if (isScreenOn && !MainActivity.isInForeground) {
                    HeartBurstOverlayActivity.launch(
                        context = this,
                        coupleId = couple.coupleId,
                        myUserId = myUserId,
                        myUserName = myUserName,
                        senderName = senderName,
                        noteText = attachedNote
                    )
                }
            }
        }

        // Case 4: Partner sent a standalone Love Note
        val noteTimeKey = "last_note_${couple.coupleId}"
        val lastKnownNoteTimestamp = prefs.getLong(noteTimeKey, 0L)
        val noteSender = couple.lastNoteSenderId
        val noteTime = couple.lastNoteTimestamp

        if (noteTime > 0L && noteTime > lastKnownNoteTimestamp) {
            prefs.edit().putLong(noteTimeKey, noteTime).apply()
            val isInitialAttachOldEvent = (lastKnownNoteTimestamp == 0L) &&
                (System.currentTimeMillis() - noteTime > 30_000L)

            if (noteSender.isNotBlank() &&
                noteSender != myUserId &&
                !isInitialAttachOldEvent &&
                noteTime != heartTime &&
                couple.lastNoteText.isNotBlank()
            ) {
                val senderName = couple.lastNoteSenderName.ifBlank {
                    if (noteSender == couple.user1Id) couple.user1Name else couple.user2Name
                }.ifBlank { "Aşkın" }
                CoupleNotificationHelper.showLoveNoteReceivedNotification(
                    this,
                    senderName = senderName,
                    noteText = couple.lastNoteText,
                    timestamp = noteTime
                )

                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isScreenOn = powerManager?.isInteractive ?: true
                if (isScreenOn && !MainActivity.isInForeground) {
                    HeartBurstOverlayActivity.launch(
                        context = this,
                        coupleId = couple.coupleId,
                        myUserId = myUserId,
                        myUserName = myUserName,
                        senderName = senderName,
                        noteText = couple.lastNoteText
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
        coupleDirectListener?.remove()
        coupleUser1Listener?.remove()
        coupleUser2Listener?.remove()
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
