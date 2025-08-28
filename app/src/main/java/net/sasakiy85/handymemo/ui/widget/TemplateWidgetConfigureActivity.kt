package net.sasakiy85.handymemo.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.sasakiy85.handymemo.data.IconRepository
import net.sasakiy85.handymemo.data.WidgetSettingRepository
import net.sasakiy85.handymemo.ui.theme.HandyMemoTheme

class TemplateWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var widgetSettingRepository: WidgetSettingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("WidgetConfig", "Activity onCreate started.")

        widgetSettingRepository = WidgetSettingRepository(this)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e("WidgetConfig", "Invalid widget ID.")
            finish() // Exit if the ID is not valid
            return
        }

        setResult(Activity.RESULT_CANCELED)

        setContent {
            HandyMemoTheme {
                ConfigureScreen(
                    onSaveClick = { name, templateText, selectedIcon ->
                        lifecycleScope.launch {
                            widgetSettingRepository.saveWidgetConfig(appWidgetId, name, templateText, selectedIcon)
                            updateWidget(this@TemplateWidgetConfigureActivity, AppWidgetManager.getInstance(this@TemplateWidgetConfigureActivity), appWidgetId)

                            val resultValue = Intent()
                            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(Activity.RESULT_OK, resultValue)
                            finish()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ConfigureScreen(onSaveClick: (String, String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var selectedIconResId by remember {
        mutableIntStateOf(IconRepository.widgetIcons.first())
    }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Template Name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Template Text") },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Select Icon")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 48.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(IconRepository.widgetIcons) { drawableId ->
                Icon(
                    // painterResourceを使ってDrawableを読み込む
                    painter = painterResource(id = drawableId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { selectedIconResId = drawableId },
                    tint = if (selectedIconResId == drawableId) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        }
        Button(
            onClick = {
                onSaveClick(name, text, selectedIconResId)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Widget")
        }
    }
}


fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    updateAppWidget(context, appWidgetManager, appWidgetId)
}