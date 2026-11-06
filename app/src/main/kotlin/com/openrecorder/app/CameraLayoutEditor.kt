// SPDX-License-Identifier: GPL-3.0-only

package com.openrecorder.app

import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Edits the normalized layout used by the live camera window for either orientation. */
@Composable
internal fun CameraLayoutEditor(settings: OverlaySettings) {
    val displayLandscape = LocalConfiguration.current.orientation != Configuration.ORIENTATION_PORTRAIT
    var landscape by rememberSaveable(displayLandscape) { mutableStateOf(displayLandscape) }
    var layout by remember(settings, landscape) { mutableStateOf(settings.cameraLayout(landscape)) }

    DisposableEffect(settings, landscape) {
        val prefix = if (landscape) "landscape_camera_" else "portrait_camera_"
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith(prefix) == true) layout = settings.cameraLayout(landscape)
        }
        settings.addListener(listener)
        onDispose { settings.removeListener(listener) }
    }

    fun save(value: OverlaySettings.CameraLayout) {
        layout = value
        settings.setCameraLayout(landscape, value)
    }

    SwitchPreference(
        title = stringResource(R.string.camera_layout),
        summary = stringResource(
            if (landscape) R.string.orientation_landscape else R.string.orientation_portrait,
        ),
        checked = landscape,
        onCheckedChange = { landscape = it },
    )
    val canvasColor = MiuixTheme.colorScheme.surfaceVariant
    val cameraColor = MiuixTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth(if (landscape) 0.72f else 0.47f)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .aspectRatio(if (landscape) 35f / 23f else 23f / 35f)
            .pointerInput(landscape, layout.size()) {
                detectTapGestures { point ->
                    val (cameraWidth, cameraHeight) = cameraSize(
                        minOf(size.width, size.height).toFloat(), layout.size(), landscape,
                    )
                    val x = (point.x - cameraWidth / 2) / (size.width - cameraWidth).coerceAtLeast(1f)
                    val y = (point.y - cameraHeight / 2) / (size.height - cameraHeight).coerceAtLeast(1f)
                    save(OverlaySettings.CameraLayout(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), layout.size()))
                }
            },
    ) {
        drawRoundRect(canvasColor, cornerRadius = CornerRadius(14.dp.toPx()))
        val (cameraWidth, cameraHeight) = cameraSize(size.minDimension, layout.size(), landscape)
        drawRoundRect(
            color = cameraColor,
            topLeft = Offset(
                (size.width - cameraWidth) * layout.x(),
                (size.height - cameraHeight) * layout.y(),
            ),
            size = Size(cameraWidth, cameraHeight),
            cornerRadius = CornerRadius(5.dp.toPx()),
        )
    }
    SliderPreference(
        title = stringResource(R.string.camera_size),
        summary = "${layout.size()}%",
        value = layout.size().toFloat(),
        valueRange = 12f..55f,
        onValueChange = { layout = OverlaySettings.CameraLayout(layout.x(), layout.y(), it.roundToInt()) },
        onValueChangeFinished = { save(layout) },
    )
    SliderPreference(
        title = stringResource(R.string.camera_horizontal),
        value = layout.x(),
        valueRange = 0f..1f,
        onValueChange = { layout = OverlaySettings.CameraLayout(it, layout.y(), layout.size()) },
        onValueChangeFinished = { save(layout) },
    )
    SliderPreference(
        title = stringResource(R.string.camera_vertical),
        value = layout.y(),
        valueRange = 0f..1f,
        onValueChange = { layout = OverlaySettings.CameraLayout(layout.x(), it, layout.size()) },
        onValueChangeFinished = { save(layout) },
    )
}

private fun cameraSize(shortEdge: Float, percent: Int, landscape: Boolean): Size {
    val longSide = shortEdge * percent / 100f
    val shortSide = longSide / OverlaySettings.CAMERA_ASPECT_RATIO
    return if (landscape) Size(longSide, shortSide) else Size(shortSide, longSide)
}
