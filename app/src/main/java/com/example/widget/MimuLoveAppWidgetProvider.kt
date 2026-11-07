package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.service.CoupleNotificationHelper
import java.util.concurrent.TimeUnit

class MimuLoveAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val PREFS_NAME = "mimu_widget_prefs"
        private const val KEY_COUPLE_NAMES = "key_couple_names"
        private const val KEY_ANNIVERSARY = "key_anniversary"
        private const val KEY_LAST_NOTE = "key_last_note"
        private const val KEY_IS_MATCHED = "key_is_matched"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val coupleNames = prefs.getString(KEY_COUPLE_NAMES, "Mimu LoveApp ❤️") ?: "Mimu LoveApp ❤️"
            val anniversary = prefs.getLong(KEY_ANNIVERSARY, System.currentTimeMillis())
            val lastNote = prefs.getString(KEY_LAST_NOTE, "") ?: ""
            val isMatched = prefs.getBoolean(KEY_IS_MATCHED, false)

            val elapsedMillis = (System.currentTimeMillis() - anniversary).coerceAtLeast(0L)
            val daysTogether = TimeUnit.MILLISECONDS.toDays(elapsedMillis)

            val views = RemoteViews(context.packageName, R.layout.widget_mimu_love)
            views.setTextViewText(R.id.widget_couple_names, coupleNames)
            views.setTextViewText(R.id.widget_days_count, daysTogether.toString())
            views.setTextViewText(R.id.widget_days_label, "GÜNDÜR BİRLİKTE")
            views.setTextViewText(
                R.id.widget_last_note,
                if (lastNote.isNotBlank()) "\"$lastNote\"" else "Dokunarak sevdiğinize kalp gönderin ❤️"
            )
            views.setTextViewText(
                R.id.widget_status_pill,
                if (isMatched) "Bağlı ❤️" else "Beklemede"
            )

            // Click PendingIntent: Opens MainActivity directly to pulsating heart
            val intent = Intent(context, MainActivity::class.java).apply {
                action = CoupleNotificationHelper.ACTION_OPEN_HEART
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_heart_btn, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun saveAndRefreshWidget(
            context: Context,
            coupleNames: String,
            anniversaryDate: Long,
            lastNote: String,
            isMatched: Boolean
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_COUPLE_NAMES, coupleNames)
                .putLong(KEY_ANNIVERSARY, anniversaryDate)
                .putString(KEY_LAST_NOTE, lastNote)
                .putBoolean(KEY_IS_MATCHED, isMatched)
                .apply()

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MimuLoveAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, MimuLoveAppWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
