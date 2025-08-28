package net.sasakiy85.handymemo.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.sasakiy85.handymemo.MainActivity
import net.sasakiy85.handymemo.R
import net.sasakiy85.handymemo.data.IconRepository
import net.sasakiy85.handymemo.data.WidgetSettingRepository
import net.sasakiy85.handymemo.utils.imageVectorToBitmap

class TemplateMemoWidgetProvider: AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    val widgetSettingRepository = WidgetSettingRepository(context)
    val scope = CoroutineScope(Dispatchers.Main)

    scope.launch {
        val templateName = widgetSettingRepository.loadTemplateName(appWidgetId)
        val iconName = widgetSettingRepository.loadIconName(appWidgetId)
        val templateText = widgetSettingRepository.loadTemplateText(appWidgetId)

        val views = RemoteViews(context.packageName, R.layout.template_widget)
        views.setTextViewText(R.id.widget_name, templateName)

        val iconResId = IconRepository.widgetIcons.find { it == iconName }
        if (iconResId != null) {
            views.setImageViewResource(R.id.widget_icon, iconResId)
        } else {
            views.setImageViewResource(R.id.widget_icon, R.drawable.ic_widget_inkmarker)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "CREATE_MEMO_FROM_WIDGET"
            putExtra("EXTRA_TEMPLATE_TEXT", templateText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}