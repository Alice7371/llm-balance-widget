package com.example.myapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startUpdateService(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, WidgetUpdateService::class.java))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val manager = AppWidgetManager.getInstance(context)
        if (manager.getAppWidgetIds(
                ComponentName(context, WidgetProvider::class.java)
            ).isEmpty()
        ) {
            context.stopService(Intent(context, WidgetUpdateService::class.java))
        }
    }

    private fun startUpdateService(context: Context) {
        context.startForegroundService(Intent(context, WidgetUpdateService::class.java))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(
                ComponentName(context, WidgetProvider::class.java)
            )
            for (widgetId in widgetIds) {
                updateWidget(context, manager, widgetId)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        val now = Date()
        views.setTextViewText(
            R.id.widget_time,
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
        )
        views.setTextViewText(
            R.id.widget_date,
            SimpleDateFormat("yyyy-MM-dd EEEE", Locale.getDefault()).format(now)
        )

        val refreshIntent = Intent(context, WidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        private const val ACTION_REFRESH = "com.example.myapp.action.REFRESH"
    }
}