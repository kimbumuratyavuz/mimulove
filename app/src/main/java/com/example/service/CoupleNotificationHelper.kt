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
import com.example.util.HeartSoundPlayer
import com.example.util.HeartVibrator

object CoupleNotificationHelper {

    const val CHANNEL_HEARTS_ID = "mimu_heart_alerts"
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

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // High priority channel for incoming hearts and whispers
            val heartChannel = NotificationChannel(
                CHANNEL_HEARTS_ID,
                CHANNEL_HEARTS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sevdiğinizden gelen anlık kalp atışları ve aşk notları"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 110, 80, 160)
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showHeartReceivedNotification(
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
            timestamp.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "❤️ $senderName sana kalp gönderdi!"
        val message = if (noteText.isNotBlank()) noteText else "Seni seviyorum biriciğim..."

        val notification = NotificationCompat.Builder(context, CHANNEL_HEARTS_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_widget_heart)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 110, 80, 160))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(HEART_NOTIFICATION_ID, notification)

        // Also trigger sound and vibration
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

        val title = "💞 Eşleşme Bildirimi!"
        val message = "$partnerName seninle Mimu LoveApp üzerinden eşleşti!"

        val notification = NotificationCompat.Builder(context, CHANNEL_HEARTS_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_widget_heart)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(PAIR_NOTIFICATION_ID, notification)

        HeartSoundPlayer.playLoveChime()
        HeartVibrator.vibrateHeartbeat(context)
    }
}
