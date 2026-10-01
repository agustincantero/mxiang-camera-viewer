package com.agustincantero.mxiangcameraviewer.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Dark only: TV is watched in a dim room and the guide recommends it.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MxiangCameraViewerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            primaryContainer = PrimaryContainer,
            onPrimaryContainer = OnPrimaryContainer,
            secondary = OnSurfaceVariant,
            onSecondary = Surface,
            secondaryContainer = SurfaceContainerHigh,
            onSecondaryContainer = OnSurface,
            background = Surface,
            onBackground = OnSurface,
            surface = Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceContainerHigh,
            onSurfaceVariant = OnSurfaceVariant,
            error = Error,
            onError = OnError,
            border = Outline,
        ),
        typography = Typography,
        content = content,
    )
}
