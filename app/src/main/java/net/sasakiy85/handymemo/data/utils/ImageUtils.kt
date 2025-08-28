package net.sasakiy85.handymemo.utils

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.drawToBitmap

/**
 * Converts a Jetpack Compose ImageVector to a Bitmap.
 * This is useful for things like widgets and notifications that don't support ImageVectors directly.
 */
fun imageVectorToBitmap(
    context: Context,
    imageVector: ImageVector,
    color: Color = Color.Black // Default color for the icon tint
): Bitmap {
    // A view that can host Jetpack Compose content.
    val composeView = ComposeView(context)

    // Set the Composable content for the view.
    composeView.setContent {
        androidx.compose.material3.Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = color
        )
    }

    // Measure and lay out the Compose content.
    composeView.measure(
        // Use unspecified measure specs to get the content's desired size.
        android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
    )
    composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

    // Draw the view's content into a Bitmap and return it.
    return composeView.drawToBitmap()
}