package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.overlay.HeartBurstOverlayActivity
import com.example.util.HeartSoundPlayer
import com.example.util.HeartVibrator

object CoupleNotificationHelper {

    private const val LEGACY_CHANNEL_HEARTS_ID = "mimu_heart_alerts"
    const val CHANNEL_HEARTS_ID = "mimu_heart_alerts_app_sound_v2"
    const val CHANNEL_HEARTS_NAME = "Mimu Kalp ve Aşk Bildirimleri"

    const val CHANNEL_SERVICE_ID = "mimu_service_status"
    const val CHANNEL_SERVICE_NAME = "Mimu Arka Plan Servis Durumu"

    const val ACTION_OPEN_HEART = "com.aistudio.mimuloveapp.ACTION_OPEN_HEART"
    const val ACTION_OPEN_PAIR = "com.aistudio.mimuloveapp.ACTION_OPEN_PAIR"
    const val ACTION_TRIGGER_SEND_HEART = "com.aistudio.mimuloveapp.ACTION_TRIGGER_SEND_HEART"

    const val EXTRA_SENDER_NAME = "extra_sender_name"
    const val EXTRA_NOTE_TEXT = "extra_note_text"
    const val EXTRA_TIMESTAMP = "extra_timestamp"

    const val SERVICE_NOTIFICATION_ID = 1001
    const val HEART_NOTIFICATION_ID = 2002
    const val PAIR_NOTIFICATION_ID = 3003
    const val NOTE_NOTIFICATION_ID = 4004

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Remove old channel that had default device notification sound attached
            runCatching {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_HEARTS_ID)
            }

            // High priority channel with NO system sound (so ONLY the app's custom sound plays)
            val heartChannel = NotificationChannel(
                CHANNEL_HEARTS_ID,
                CHANNEL_HEARTS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sevdiğinizden gelen anlık kalp atışları, aşk notları ve eşleşme bildirimleri"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            manager.createNotificationChannel(heartChannel)

            // Low priority channel for persistent background service
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                CHANNEL_SERVICE_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Uygulama kapalıyken kalp bildirimlerini yakalamak için arka plan servisi"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun buildServiceNotification(context: Context): Notification {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setContentTitle("Mimu LoveApp Aktif")
            .setContentText("Eşinden gelecek kalp atışları arka planda dinleniyor ❤️")
            .setSmallIcon(R.drawable.ic_widget_heart)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setSound(null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showHeartReceivedNotification(
        context: Context,
        coupleId: String = "",
        myUserId: String = "",
        myUserName: String = "Aşkım",
        senderName: String,
        noteText: String,
        timestamp: Long
    ) {
        createNotificationChannels(context)

        val overlayIntent = HeartBurstOverlayActivity.createIntent(
            context = context,
            coupleId = coupleId,
            myUserId = myUserId,
            myUserName = myUserName,
            senderName = senderName,
            noteText = noteText
        )

        val overlayPendingIntent = PendingIntent.getActivity(
            context,
            (timestamp and 0x7FFFFFFF).toInt(),
            overlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "❤️ $senderName sana kalp gönderdi!"
        val message = if (noteText.isNotBlank()) noteText else "Seni seviyorum biriciğim..."

        val notification = NotificationCompat.Builder(context, CHANNEL_HEARTS_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_widget_heart)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(overlayPendingIntent)
            .setFullScreenIntent(overlayPendingIntent, true)
            .setAutoCancel(true)
            .setSound(null)
            .setDefaults(0)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(HEART_NOTIFICATION_ID, notification)

        // Play ONLY the app's custom sound and heartbeat vibration
        HeartSoundPlayer.playLoveChime()
        HeartVibrator.vibrateHeartbeat(context)
    }

    fun showLoveNoteReceivedNotification(
        context: Context,
        senderName: String,
        noteText: String,
        timestamp: Long
    ) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_HEART
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_NOTE_TEXT, noteText)
            putExtra(EXTRA_TIMESTAMP, timestamp)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            ((timestamp + 1) and 0x7FFFFFFF).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "💌 $senderName sana aşk notu gönderdi!"
        val message = noteText.ifBlank { "Seni çok seviyorum ❤️" }

        val notification = NotificationCompat.Builder(context, CHANNEL_HEARTS_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_widget_heart)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(null)
            .setDefaults(0)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTE_NOTIFICATION_ID, notification)

        HeartSoundPlayer.playLoveChime()
        HeartVibrator.vibrateHeartbeat(context)
    }

    fun showPairRequestNotification(context: Context, partnerName: String) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_PAIR
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "💞 Eşleşme İsteği Geldi!"
        val message = "$partnerName seninle Mimu LoveApp üzerinden eşleşmek istiyor! Onaylamak için dokun ❤️"

        val notification = NotificationCompat.Builder(context, CHANNEL_HEARTS_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_widget_heart)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(null)
            .setDefaults(0)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(PAIR_NOTIFICATION_ID, notification)

        HeartSoundPlayer.playLoveChime()
        HeartVibrator.vibrateHeartbeat(context)
    }

    fun showPairAcceptedNotification(context: Context, partnerName: String) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_PAIR
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            998,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "💞 Eşleşme Onaylandı!"
        val message = "$partnerName eşleşme isteğini kabul etti! Artık kalpleriniz bağlı ❤️"

        val notification = NotificationCompat.Builder(context, CHANNEL_HEARTS_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_widget_heart)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(null)
            .setDefaults(0)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(PAIR_NOTIFICATION_ID, notification)

        HeartSoundPlayer.playLoveChime()
        HeartVibrator.vibrateHeartbeat(context)
    }
}
