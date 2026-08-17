package ui.components

import AppBackdrop
import LocalAppStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.nucleusframework.application.NucleusDecoratedWindowScope
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground
import dev.nucleusframework.window.WindowsBackdrop
import dev.nucleusframework.window.WindowsBackdropStyle
import dev.nucleusframework.window.WindowsBackdropTier
import dev.nucleusframework.window.fluent.FluentTitleBar
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import util.getNonWin11BackgroundGradient

@Composable
fun NucleusDecoratedWindowScope.AppWindowChrome(
    title: String,
    content: @Composable () -> Unit,
) {
    val appStore = LocalAppStore.current
    val darkMode = appStore.darkMode.value
    val backdrop = appStore.backdropType.value
    val backgroundBrush = remember(darkMode) { getNonWin11BackgroundGradient(darkMode) }
    val backdropStyle = when (backdrop) {
        AppBackdrop.None -> WindowsBackdropStyle.None
        AppBackdrop.Mica -> WindowsBackdropStyle.Mica
        AppBackdrop.MicaAlt -> WindowsBackdropStyle.MicaAlt
        AppBackdrop.Acrylic -> WindowsBackdropStyle.Acrylic
    }

    // Nucleus applies the native DWM material and temporarily clears the client
    // surface. Any area painted below this call would hide the desktop material.
    WindowBackground(FluentTheme.colors.background.mica.base)
    WindowAppearance(if (darkMode) WindowAppearanceMode.Dark else WindowAppearanceMode.Light)
    if (appStore.isWindows) {
        WindowsBackdrop(backdropStyle, tier = WindowsBackdropTier.Auto)
    }

    val defaultTitleBarStyle = LocalTitleBarStyle.current
    val titleBarStyle = remember(defaultTitleBarStyle, backdrop, appStore.isWindows) {
        if (appStore.isWindows && backdrop != AppBackdrop.None) {
            defaultTitleBarStyle.copy(
                colors = defaultTitleBarStyle.colors.copy(
                    background = Color.Transparent,
                    inactiveBackground = Color.Transparent,
                    fullscreenControlButtonsBackground = Color.Transparent,
                ),
            )
        } else {
            defaultTitleBarStyle
        }
    }

    Column(Modifier.fillMaxSize()) {
        FluentTitleBar(style = titleBarStyle) {
            Text(title, style = FluentTheme.typography.caption)
        }
        val useGradient = backdrop == AppBackdrop.None || !appStore.isWindows
        val bgModifier = if (useGradient) Modifier.background(backgroundBrush) else Modifier
        Box(Modifier.fillMaxWidth().weight(1f).then(bgModifier)) {
            content()
        }
    }
}
