package net.sasakiy85.handymemo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_settings")
class WidgetSettingRepository(private val context: Context) {
    private fun getTemplateTextKey(appWidgetId: Int) = stringPreferencesKey("template_text_$appWidgetId")
    private fun getIconNameKey(appWidgetId: Int) = intPreferencesKey("icon_$appWidgetId")
    private fun getTemplateNameKey(appWidgetId: Int) = stringPreferencesKey("template_name_$appWidgetId")

    suspend fun saveWidgetConfig(appWidgetId: Int, name: String, templateText: String, iconName: Int) {
        context.widgetDataStore.edit { settings ->
            settings[getTemplateNameKey(appWidgetId)] = name
            settings[getTemplateTextKey(appWidgetId)] = templateText
            settings[getIconNameKey(appWidgetId)] = iconName
        }
    }

    suspend fun loadTemplateName(appWidgetId: Int): String? {
        val preferences = context.widgetDataStore.data.first()
        return preferences[getTemplateNameKey(appWidgetId)]
    }

    suspend fun loadTemplateText(appWidgetId: Int): String? {
        val preferences = context.widgetDataStore.data.first()
        return preferences[getTemplateTextKey(appWidgetId)]
    }

    suspend fun loadIconName(appWidgetId: Int): Int? {
        val preferences = context.widgetDataStore.data.first()
        return preferences[getIconNameKey(appWidgetId)]
    }
}