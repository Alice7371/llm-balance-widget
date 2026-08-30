package com.example.myapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import java.util.concurrent.Executors

class CombinedBalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            refreshWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(
                ComponentName(context, CombinedBalanceWidgetProvider::class.java)
            )
            for (widgetId in widgetIds) {
                refreshWidget(context, manager, widgetId)
            }
        }
    }

    private fun refreshWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        renderLoading(context, manager, widgetId)
        executor.execute {
            val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = MainActivity.readEnabledProviders(prefs)
            val providers = MainActivity.readProviderOrder(prefs).filter { it in enabled }
            val custom = CustomProvider.listFromJson(prefs.getString(MainActivity.KEY_CUSTOM, null))
            val results: MutableList<Pair<String, DeepSeekApi.Balance>> = mutableListOf()

            for (provider in providers) {
                val credential = prefs.getString(provider.key, null)
                val balance = if (credential.isNullOrBlank()) {
                    DeepSeekApi.Balance(false, "No key", "Set in MyApp")
                } else {
                    try {
                        provider.fetch(credential)
                    } catch (e: Exception) {
                        DeepSeekApi.Balance(false, "Error", e.message ?: "Error")
                    }
                }
                results.add(provider.displayName to balance)
            }

            for (customProvider in custom) {
                val balance = try {
                    customProvider.fetch()
                } catch (e: Exception) {
                    DeepSeekApi.Balance(false, "Error", e.message ?: "Error")
                }
                results.add(customProvider.name to balance)
            }

            render(context, manager, widgetId, results)
        }
    }

    private fun renderLoading(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.combined_widget_layout)
        for (slot in 0 until MAX_SLOTS) {
            views.setTextViewText(labelId(slot), "--")
            views.setTextViewText(amountId(slot), "Loading...")
            setBar(views, barId(slot), 0, 100, COLOR_LOADING)
        }
        views.setTextViewText(R.id.combined_status, "Fetching...")
        views.setTextViewText(R.id.combined_updated, "Updated " + now())
        manager.updateAppWidget(widgetId, views)
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        results: List<Pair<String, DeepSeekApi.Balance>>
    ) {
        val views = RemoteViews(context.packageName, R.layout.combined_widget_layout)
        for (slot in 0 until MAX_SLOTS) {
            if (slot < results.size) {
                val (label, balance) = results[slot]
                views.setViewVisibility(slotId(slot), View.VISIBLE)
                views.setTextViewText(labelId(slot), label)
                views.setTextViewText(amountId(slot), balance.title)
                when (balance.kind) {
                    DeepSeekApi.Kind.CNY -> setBar(views, barId(slot), cnyProgress(balance), 100, cnyColor(balance))
                    DeepSeekApi.Kind.QUOTA -> setBar(views, barId(slot), quotaProgress(balance), 100, quotaColor(balance))
                }
            } else {
                views.setViewVisibility(slotId(slot), View.GONE)
            }
        }
        views.setTextViewText(R.id.combined_status, "Auto-refresh every 30 min")
        views.setTextViewText(R.id.combined_updated, "Updated " + now())
        manager.updateAppWidget(widgetId, views)
    }

    private fun setBar(views: RemoteViews, barId: Int, progress: Int, max: Int, color: Int) {
        views.setProgressBar(barId, max, progress.coerceIn(0, max), false)
        views.setColorStateList(barId, "setProgressTintList", ColorStateList.valueOf(color))
    }

    /** 10 CNY = 100%. */
    private fun cnyProgress(b: DeepSeekApi.Balance): Int =
        if (b.ok) (b.value / 10.0 * 100.0).toInt().coerceIn(0, 100) else 0

    /** hue 120 (green) at 10 CNY -> hue 0 (red) at 0 CNY, gradual. */
    private fun cnyColor(b: DeepSeekApi.Balance): Int =
        if (!b.ok) COLOR_LOADING else gradientColor(b.value / 10.0)

    /** opencode-go: bar shows remaining quota (inverted usage). */
    private fun quotaProgress(b: DeepSeekApi.Balance): Int =
        if (b.ok) (100 - b.value).toInt().coerceIn(0, 100) else 0

    /** hue 120 (green) at 0% usage -> hue 0 (red) at 100% usage, gradual. */
    private fun quotaColor(b: DeepSeekApi.Balance): Int =
        if (!b.ok) COLOR_LOADING else gradientColor((100 - b.value) / 100.0)

    /** fraction in 0..1 (1 = full). Maps to hue 0 (red) .. 120 (green). */
    private fun gradientColor(fraction: Double): Int {
        val f = fraction.coerceIn(0.0, 1.0)
        return Color.HSVToColor(floatArrayOf((120.0 * f).toFloat(), 0.9f, 0.85f))
    }

    private fun slotId(slot: Int): Int = when (slot) {
        0 -> R.id.slot_0
        1 -> R.id.slot_1
        2 -> R.id.slot_2
        3 -> R.id.slot_3
        4 -> R.id.slot_4
        5 -> R.id.slot_5
        6 -> R.id.slot_6
        else -> R.id.slot_7
    }

    private fun labelId(slot: Int): Int = when (slot) {
        0 -> R.id.label_0
        1 -> R.id.label_1
        2 -> R.id.label_2
        3 -> R.id.label_3
        4 -> R.id.label_4
        5 -> R.id.label_5
        6 -> R.id.label_6
        else -> R.id.label_7
    }

    private fun amountId(slot: Int): Int = when (slot) {
        0 -> R.id.amount_0
        1 -> R.id.amount_1
        2 -> R.id.amount_2
        3 -> R.id.amount_3
        4 -> R.id.amount_4
        5 -> R.id.amount_5
        6 -> R.id.amount_6
        else -> R.id.amount_7
    }

    private fun barId(slot: Int): Int = when (slot) {
        0 -> R.id.bar_0
        1 -> R.id.bar_1
        2 -> R.id.bar_2
        3 -> R.id.bar_3
        4 -> R.id.bar_4
        5 -> R.id.bar_5
        6 -> R.id.bar_6
        else -> R.id.bar_7
    }

    private fun now(): String =
        java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

    companion object {
        const val ACTION_REFRESH = "com.example.myapp.action.COMBINED_REFRESH"
        private const val MAX_SLOTS = 8
        private val executor = Executors.newSingleThreadExecutor()
        private val COLOR_LOADING = Color.parseColor("#90A4AE")
    }
}