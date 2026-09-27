package com.bakkwo.translate.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.bakkwo.translate.R
import com.bakkwo.translate.data.Prefs
import com.bakkwo.translate.ui.ClipboardTranslateActivity
import com.bakkwo.translate.ui.MainActivity

class BakkwoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildRemoteViews(context, appWidgetManager, id))
        }
    }

    // Fires whenever the user resizes the widget. Without this, a widget dragged down to a
    // narrow width after being placed would keep the layout picked at placement time.
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetManager, appWidgetId))
    }

    companion object {
        // Below this width the "번역" button's text label doesn't reliably fit next to its icon
        // and the copy/reset buttons, so it's hidden and the button falls back to icon-only.
        private const val COMPACT_WIDTH_THRESHOLD_DP = 160

        // Below this height there isn't room for a multi-line result preview above the button
        // row, so it's capped to a single ellipsized line instead of getting clipped mid-line.
        private const val COMPACT_HEIGHT_THRESHOLD_DP = 100

        /** Called by the worker/receiver once a new translation (or error) is ready. */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BakkwoWidgetProvider::class.java))
            for (id in ids) {
                manager.updateAppWidget(id, buildRemoteViews(context, manager, id))
            }
        }

        private fun buildRemoteViews(context: Context, manager: AppWidgetManager, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bakkwo)

            val resultText = when (Prefs.getLastState(context)) {
                Prefs.STATE_LOADING -> context.getString(R.string.translating)
                Prefs.STATE_ERROR -> Prefs.getLastResult(context) ?: context.getString(R.string.translate_error)
                Prefs.STATE_DONE -> Prefs.getLastResult(context) ?: context.getString(R.string.widget_placeholder_result)
                else -> context.getString(R.string.widget_placeholder_source)
            }
            views.setTextViewText(R.id.widget_result, resultText)

            val options = manager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, Int.MAX_VALUE)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, Int.MAX_VALUE)
            views.setViewVisibility(
                R.id.widget_translate_label,
                if (minWidthDp < COMPACT_WIDTH_THRESHOLD_DP) View.GONE else View.VISIBLE
            )
            views.setInt(
                R.id.widget_result, "setMaxLines",
                if (minHeightDp < COMPACT_HEIGHT_THRESHOLD_DP) 1 else 4
            )

            views.setOnClickPendingIntent(R.id.widget_btn_translate, translatePendingIntent(context))
            views.setOnClickPendingIntent(R.id.widget_btn_copy, copyPendingIntent(context))
            views.setOnClickPendingIntent(R.id.widget_btn_reset, resetPendingIntent(context))
            views.setOnClickPendingIntent(R.id.widget_result, openAppPendingIntent(context))

            return views
        }

        private fun translatePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ClipboardTranslateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun copyPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_COPY_RESULT
            }
            return PendingIntent.getBroadcast(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun resetPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_RESET_SESSION
            }
            return PendingIntent.getBroadcast(
                context, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(
                context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
