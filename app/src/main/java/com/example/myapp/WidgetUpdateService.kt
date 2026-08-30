package com.example.myapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WidgetUpdateService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            updateWidgets()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    private fun startInForeground() {
        val channelId = "widget_updates"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Widget updates",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("MyApp Clock")
            .setContentText("Updating widget...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateWidgets() {
        val manager = AppWidgetManager.getInstance(this)
        val widgetIds = manager.getAppWidgetIds(
            ComponentName(this, WidgetProvider::class.java)
        )
        val now = Date()
        val views = RemoteViews(packageName, R.layout.widget_layout)
        views.setTextViewText(
            R.id.widget_time,
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
        )
        views.setTextViewText(
            R.id.widget_date,
            SimpleDateFormat("yyyy-MM-dd EEEE", Locale.getDefault()).format(now)
        )
        for (widgetId in widgetIds) {
            manager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}