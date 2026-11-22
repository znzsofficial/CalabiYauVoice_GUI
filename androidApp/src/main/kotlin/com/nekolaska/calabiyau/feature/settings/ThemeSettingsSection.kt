package com.nekolaska.calabiyau.feature.settings

import android.system.Os
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AppShortcut
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.launcher.LauncherIconTheme
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.preferences.encodeCustomSeed
import com.nekolaska.calabiyau.core.preferences.explicitSeedArgb
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSizes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.LocalAmoledDark
import com.nekolaska.calabiyau.core.ui.LocalColorSpec2025
import com.nekolaska.calabiyau.core.ui.LocalContrastLevel
import com.nekolaska.calabiyau.core.ui.LocalHighReadabilityDrawer
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.LocalPaletteStyle
import com.nekolaska.calabiyau.core.ui.LocalSeedColor
import com.nekolaska.calabiyau.core.ui.LocalThemeMode
import com.nekolaska.calabiyau.core.ui.LocalWallpaperSeedColor
import com.nekolaska.calabiyau.core.ui.WallpaperSeedColor
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.tools.rememberSyncedSliderState
import com.nekolaska.calabiyau.feature.wiki.gallery.WallpaperApi
import com.nekolaska.calabiyau.feature.wiki.gallery.WallpaperRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import util.awaitGetToFile
import java.io.File
import java.io.IOException

private data class ThemeColorSource(
    val value: Int,
    val label: String,
    val description: String
)

private val THEME_COLOR_SOURCES = listOf(
    ThemeColorSource(AppPrefs.SEED_WALLPAPER, "跟随壁纸", "根据当前壁纸生成主题色"),
    ThemeColorSource(0, "系统", "使用系统强调色"),
)

private val PRESET_SWATCHES = listOf(
    0xFF4285F4.toInt() to "蓝",
    0xFF0F9D58.toInt() to "绿",
    0xFFDB4437.toInt() to "红",
    0xFFF4B400.toInt() to "琥珀",
    0xFF9C27B0.toInt() to "紫",
    0xFF00BCD4.toInt() to "青",
    0xFFFF5722.toInt() to "橙",
    0xFF607D8B.toInt() to "蓝灰",
    0xFFE91E63.toInt() to "粉",
    0xFF3F51B5.toInt() to "靛蓝",
    0xFF009688.toInt() to "蓝绿",
    0xFF795548.toInt() to "棕",
)

internal fun themeModeLabel(mode: Int): String = when (mode) {
    AppPrefs.THEME_LIGHT -> "浅色"
    AppPrefs.THEME_DARK -> "深色"
    else -> "跟随系统"
}

@Composable
internal fun AppearanceSettingsSection(onOpenColors: () -> Unit) {
    var themeMode by LocalThemeMode.current
    var amoledDark by LocalAmoledDark.current
    var liquidGlassEnabled by LocalLiquidGlassEnabled.current
    var highReadabilityDrawer by LocalHighReadabilityDrawer.current
    var launcherIconTheme by remember { mutableIntStateOf(AppPrefs.launcherIconTheme) }

    SettingsGroupHeader("主题", topPadding = AppSpacing.small)
    SettingsCard {
        var showThemeDialog by remember { mutableStateOf(false) }
        SettingsItem(
            icon = Icons.Outlined.Contrast,
            title = "主题模式",
            value = themeModeLabel(themeMode)
        ) { showThemeDialog = true }
        if (showThemeDialog) {
            SettingsSingleChoiceDialog(
                title = "主题模式",
                options = listOf(AppPrefs.THEME_SYSTEM, AppPrefs.THEME_LIGHT, AppPrefs.THEME_DARK)
                    .map { it to themeModeLabel(it) },
                selectedValue = themeMode,
                onSelect = { themeMode = it; AppPrefs.themeMode = it; showThemeDialog = false },
                onDismissRequest = { showThemeDialog = false }
            )
        }
        SettingsDivider()
        SettingsToggleItem(
            icon = Icons.Outlined.DarkMode,
            title = "纯黑模式",
            subtitle = "深色外观下使用纯黑背景",
            checked = amoledDark
        ) {
            amoledDark = it
            AppPrefs.amoledDark = it
        }
        SettingsDivider()
        SettingsItem(
            icon = Icons.Outlined.ColorLens,
            title = SettingsPage.COLORS.title,
            value = themeColorName(LocalSeedColor.current.intValue),
            onClick = onOpenColors
        )
    }

    SettingsGroupHeader("视觉效果")
    SettingsCard {
        var showLauncherIconDialog by remember { mutableStateOf(false) }
        val launcherIconName = when (launcherIconTheme) {
            AppPrefs.LAUNCHER_ICON_SYSTEM -> "系统强调色"
            else -> "品牌深色"
        }
        SettingsItem(
            icon = Icons.Outlined.AppShortcut,
            title = "图标",
            value = launcherIconName,
            onClick = { showLauncherIconDialog = true }
        )
        if (showLauncherIconDialog) {
            val context = LocalContext.current
            SettingsDialog(
                onDismissRequest = { showLauncherIconDialog = false },
                title = { Text("图标") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                        Text(
                            "更改后，主屏幕图标可能需要片刻才会更新。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(
                            modifier = Modifier.selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                        ) {
                            listOf(
                                AppPrefs.LAUNCHER_ICON_BRAND to "品牌深色",
                                AppPrefs.LAUNCHER_ICON_SYSTEM to "系统强调色"
                            ).forEach { (theme, label) ->
                                val selected = launcherIconTheme == theme
                                val colors = MaterialTheme.colorScheme
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(SettingsIconFrameShape)
                                        .border(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.35f),
                                            shape = SettingsIconFrameShape
                                        )
                                        .selectable(
                                            selected = selected,
                                            role = Role.RadioButton,
                                            onClick = {
                                                launcherIconTheme = theme
                                                AppPrefs.launcherIconTheme = theme
                                                LauncherIconTheme.apply(context, theme)
                                                showLauncherIconDialog = false
                                            }
                                        )
                                        .minimumInteractiveComponentSize()
                                        .padding(horizontal = AppSpacing.large, vertical = AppSpacing.medium),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.iconGap)
                                ) {
                                    RadioButton(selected = selected, onClick = null)
                                    Text(
                                        text = label,
                                        style = AppTextStyles.settingsItemTitle,
                                        color = if (selected) colors.primary else colors.onSurface
                                    )
                                }
                            }
                        }
                    }
                },
                shape = AppShapes.dialog,
                confirmButton = {
                    TextButton(onClick = { showLauncherIconDialog = false }) { Text("关闭") }
                }
            )
        }

        SettingsDivider()
        SettingsToggleItem(
            icon = Icons.Outlined.BlurOn,
            title = "液态玻璃",
            subtitle = "需要 Android 12 或更高版本",
            checked = liquidGlassEnabled,
            onCheckedChange = {
                liquidGlassEnabled = it
                AppPrefs.liquidGlassEnabled = it
            }
        )

        SettingsDivider()
        SettingsToggleItem(
            icon = Icons.Outlined.Visibility,
            title = "提高侧栏对比度",
            subtitle = "增强液态玻璃侧栏中的文字可读性",
            checked = highReadabilityDrawer,
            onCheckedChange = {
                highReadabilityDrawer = it
                AppPrefs.highReadabilityDrawer = it
            }
        )
    }
}

@Composable
internal fun ColorSettingsSection() {
    var seedColor by LocalSeedColor.current
    var paletteStyle by LocalPaletteStyle.current
    var contrast by LocalContrastLevel.current
    var colorSpec2025 by LocalColorSpec2025.current

    SettingsGroupHeader("配色参数", topPadding = AppSpacing.small)
    SettingsCard {
        ThemeColorPicker(seedColor) { seedColor = it; AppPrefs.customSeedColor = it }
        SettingsDivider()
        PaletteStylePicker(paletteStyle) { paletteStyle = it; AppPrefs.paletteStyle = it }
        SettingsDivider()
        ContrastLevelPicker(contrast) { contrast = it; AppPrefs.contrastLevel = it }
        SettingsDivider()
        SettingsToggleItem(
            icon = Icons.Outlined.AutoAwesome,
            title = "动态配色",
            subtitle = if (colorSpec2025) "2025 规范" else "2021 规范",
            checked = colorSpec2025,
            tabularSubtitle = true
        ) {
            colorSpec2025 = it
            AppPrefs.colorSpec2025 = it
        }
    }
}

// ─────────────────────── 主题色选择器 ───────────────────────

private fun themeColorName(color: Int): String = THEME_COLOR_SOURCES.firstOrNull { it.value == color }?.label
    ?: PRESET_SWATCHES.firstOrNull { it.first == color }?.second ?: "自定义"

// Choose the higher WCAG contrast of black and white on the opaque color swatch.
private fun swatchInk(background: Color) = if (background.luminance() > 0.179f) Color.Black else Color.White

@Composable
private fun ThemeColorPicker(
    currentSeedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val wallpaperSeed = LocalWallpaperSeedColor.current
    val explicitArgb = explicitSeedArgb(currentSeedColor)
    val activeSwatch = when {
        explicitArgb != null -> Color(explicitArgb)
        currentSeedColor == AppPrefs.SEED_WALLPAPER && wallpaperSeed.intValue != 0 -> Color(wallpaperSeed.intValue)
        else -> MaterialTheme.colorScheme.primary
    }

    SettingsItem(
        icon = Icons.Outlined.ColorLens,
        title = "主题色",
        value = themeColorName(currentSeedColor),
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(activeSwatch)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
                SettingsNavigationChevron()
            }
        },
        onClick = { showDialog = true }
    )

    if (showDialog) {
        // AppTheme owns the active wallpaper seed. Other themes only need this sample inside the chooser.
        val wallpaperSeedArgb = if (currentSeedColor == AppPrefs.SEED_WALLPAPER) wallpaperSeed.intValue else {
            val context = LocalContext.current
            val url by AppPrefs.wallpaperUrlState.collectAsState()
            var sample by remember(url) { mutableIntStateOf(WallpaperSeedColor.cachedColorFor(url)) }
            if (sample == 0 && !url.isNullOrBlank()) LaunchedEffect(context, url) {
                sample = WallpaperSeedColor.resolve(context, url)
            }
            sample
        }
        val initHsv = remember {
            FloatArray(3).also { hsv ->
                if (explicitArgb != null) {
                    android.graphics.Color.colorToHSV(explicitArgb, hsv)
                } else {
                    hsv[0] = 210f
                    hsv[1] = 0.7f
                    hsv[2] = 0.8f
                }
            }
        }
        var hue by remember { mutableFloatStateOf(initHsv[0]) }
        var saturation by remember { mutableFloatStateOf(initHsv[1]) }
        var value by remember { mutableFloatStateOf(initHsv[2]) }
        val isPresetSelected = PRESET_SWATCHES.any { it.first == currentSeedColor }
        var showCustomPicker by remember {
            mutableStateOf(explicitArgb != null && !isPresetSelected)
        }
        val customColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
        val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

        SettingsDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择主题色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    THEME_COLOR_SOURCES.forEach { source ->
                        val isSelected = source.value == currentSeedColor && !showCustomPicker
                        val swatch = when (source.value) {
                            AppPrefs.SEED_WALLPAPER -> if (wallpaperSeedArgb != 0) Color(wallpaperSeedArgb)
                            else MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        ThemeColorSourceRow(
                            label = source.label,
                            description = source.description,
                            swatch = swatch,
                            selected = isSelected,
                            onClick = {
                                showCustomPicker = false
                                if (source.value == AppPrefs.SEED_WALLPAPER && wallpaperSeedArgb != 0) {
                                    wallpaperSeed.intValue = wallpaperSeedArgb
                                }
                                onColorSelected(source.value)
                                showDialog = false
                            }
                        )
                    }

                    Text(
                        "预设颜色",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    PRESET_SWATCHES.chunked(6).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { (argb, label) ->
                                val isSelected = argb == currentSeedColor && !showCustomPicker
                                val swatchColor = Color(argb)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .then(
                                            if (isSelected) Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape
                                            ) else Modifier.border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                                CircleShape
                                            )
                                        )
                                        .clickable {
                                            showCustomPicker = false
                                            onColorSelected(argb)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = label,
                                            tint = swatchInk(swatchColor),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = settingsDividerColor())

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SettingsIconFrameShape)
                            .clickable { showCustomPicker = !showCustomPicker }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "自定义颜色",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (showCustomPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showCustomPicker) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(SettingsIconFrameShape)
                                    .background(customColor)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        SettingsIconFrameShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = String.format("#%06X", customColor.toArgb() and 0xFFFFFF),
                                    color = swatchInk(customColor),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                            }
                            ColorChannelSlider(
                                label = "色相",
                                value = hue,
                                valueRange = 0f..360f,
                                onValueChange = { hue = it },
                                trackBrush = Brush.horizontalGradient(
                                    listOf(
                                        Color.Red,
                                        Color.Yellow,
                                        Color.Green,
                                        Color.Cyan,
                                        Color.Blue,
                                        Color.Magenta,
                                        Color.Red
                                    )
                                )
                            )
                            ColorChannelSlider(
                                label = "饱和度",
                                value = saturation,
                                valueRange = 0f..1f,
                                onValueChange = { saturation = it },
                                trackBrush = Brush.horizontalGradient(
                                    listOf(Color(0xFF9E9E9E), hueColor)
                                )
                            )
                            ColorChannelSlider(
                                label = "明度",
                                value = value,
                                valueRange = 0f..1f,
                                onValueChange = { value = it },
                                trackBrush = Brush.horizontalGradient(
                                    listOf(Color.Black, hueColor)
                                )
                            )
                        }
                    }
                }
            },
            shape = AppShapes.dialog,
            confirmButton = {
                if (showCustomPicker) {
                    FilledTonalButton(onClick = {
                        onColorSelected(encodeCustomSeed(customColor.toArgb()))
                        showDialog = false
                    }) {
                        Text("应用")
                    }
                } else {
                    TextButton(onClick = { showDialog = false }) {
                        Text("关闭")
                    }
                }
            },
            dismissButton = if (showCustomPicker) {
                {
                    TextButton(onClick = { showDialog = false }) {
                        Text("取消")
                    }
                }
            } else null
        )
    }
}

@Composable
private fun ThemeColorSourceRow(
    label: String,
    description: String,
    swatch: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsIconFrameShape)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.35f),
                shape = SettingsIconFrameShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(swatch)
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = swatchInk(swatch),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.primary else colors.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    trackBrush: Brush
) {
    // material3 alpha28 起自定义 track 需改用 SliderState 重载
    val sliderState = rememberSyncedSliderState(value, valueRange)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            state = sliderState,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(CircleShape)
                        .background(trackBrush)
                )
            }
        )
    }
}

// ─────────────────────── 壁纸管理 ───────────────────────

@Composable
internal fun WallpaperSettingsSection(
    wallpaperAutoRefresh: Boolean,
    onAutoRefreshChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val wallpaperUrl by AppPrefs.wallpaperUrlState.collectAsState()
    val followsWallpaperColor = LocalSeedColor.current.intValue == AppPrefs.SEED_WALLPAPER
    var isRefreshing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var wallpaperMessage by remember { mutableStateOf<String?>(null) }
    var saveMessage by remember(wallpaperUrl) { mutableStateOf<String?>(null) }
    var showImagePreview by remember(wallpaperUrl) { mutableStateOf(false) }

    SettingsGroupHeader("当前壁纸", topPadding = AppSpacing.small)
    WallpaperPreviewCard(
        wallpaperUrl = wallpaperUrl,
        wallpaperAutoRefresh = wallpaperAutoRefresh,
        followsWallpaperColor = followsWallpaperColor,
        isRefreshing = isRefreshing,
        onPreview = { showImagePreview = true }
    )

    SettingsGroupHeader("壁纸设置")
    SettingsCard {
        SettingsToggleItem(
            icon = Icons.Outlined.Autorenew,
            title = "自动更换",
            subtitle = "启动时随机更换壁纸",
            checked = wallpaperAutoRefresh,
            onCheckedChange = onAutoRefreshChange
        )

        SettingsDivider()
        SettingsItem(
            icon = Icons.Outlined.Refresh,
            title = "立即更换",
            subtitle = if (isRefreshing) "正在获取…" else wallpaperMessage ?: "随机更换一张壁纸",
            enabled = !isRefreshing,
            showChevron = false,
            trailing = if (isRefreshing) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AppSizes.iconSmall),
                        strokeWidth = 2.dp
                    )
                }
            } else null,
            onClick = {
                if (isRefreshing) return@SettingsItem
                isRefreshing = true
                wallpaperMessage = null
                scope.launch {
                    try {
                        wallpaperMessage = when (WallpaperApi.refresh(manual = true)) {
                            WallpaperRefresh.UPDATED -> "已更换"
                            WallpaperRefresh.RETAINED -> "沿用当前壁纸"
                            WallpaperRefresh.UNAVAILABLE -> "获取失败"
                        }
                    } finally {
                        isRefreshing = false
                    }
                }
            }
        )

        SettingsDivider()
        SettingsItem(
            icon = Icons.Outlined.SaveAlt,
            title = "存储到“下载”",
            subtitle = if (isSaving) "正在存储…" else saveMessage ?: "将当前壁纸存储到下载目录",
            enabled = !isSaving,
            showChevron = false,
            trailing = if (isSaving) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AppSizes.iconSmall),
                        strokeWidth = 2.dp
                    )
                }
            } else null,
            onClick = {
                if (isSaving) return@SettingsItem
                val currentUrl = wallpaperUrl
                if (currentUrl.isNullOrBlank()) {
                    saveMessage = "暂无壁纸，可先点“立即更换”"
                    return@SettingsItem
                }
                isSaving = true
                scope.launch {
                    try {
                        val path = withContext(Dispatchers.IO) {
                            val fileName = currentUrl.substringAfterLast("/")
                                .substringBefore("?")
                                .ifBlank { "wallpaper_${System.currentTimeMillis()}.jpg" }
                            val saveDir = File(AppPrefs.savePath)
                            if (!saveDir.exists()) saveDir.mkdirs()
                            saveWallpaperFile(currentUrl, File(saveDir, fileName))
                            saveDir.absolutePath
                        }
                        saveMessage = "已保存到 $path"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        saveMessage = "保存失败"
                    } finally {
                        isSaving = false
                    }
                }
            }
        )
    }

    if (showImagePreview && !wallpaperUrl.isNullOrBlank()) {
        ImagePreviewDialog(
            model = wallpaperUrl,
            contentDescription = "当前壁纸",
            onDismiss = { showImagePreview = false }
        )
    }
}

@Composable
private fun WallpaperPreviewCard(
    wallpaperUrl: String?,
    wallpaperAutoRefresh: Boolean,
    followsWallpaperColor: Boolean,
    isRefreshing: Boolean,
    onPreview: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isPreview = LocalInspectionMode.current
    val hasWallpaper = !wallpaperUrl.isNullOrBlank()
    val statusSummary = buildString {
        append(if (wallpaperAutoRefresh) "启动时随机更换" else "手动更换")
        if (followsWallpaperColor) append(" · 跟随取色")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screen),
        shape = SettingsCardShape,
        color = colors.surfaceContainerLow,
        border = settingsCardBorder(highlighted = hasWallpaper)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SettingsAccentBarHeight)
                    .background(colors.primary)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        Brush.linearGradient(
                            listOf(colors.primaryContainer, colors.tertiaryContainer)
                        )
                    )
                    .then(
                        if (hasWallpaper) Modifier.clickable(role = Role.Button, onClick = onPreview)
                        else Modifier
                    )
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(AppSpacing.screen),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                ) {
                    SettingsFramedIcon(
                        icon = Icons.Outlined.Wallpaper,
                        size = 46.dp,
                        iconSize = 24.dp
                    )
                    Text(
                        text = if (hasWallpaper && !isPreview) "正在加载壁纸…" else "暂无壁纸，点下方“立即更换”即可获取",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }

                if (hasWallpaper && !isPreview) {
                    AsyncImage(
                        model = wallpaperUrl,
                        contentDescription = "当前壁纸",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isRefreshing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.36f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White
                        )
                    }
                }

                if (hasWallpaper) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.72f)
                                    )
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                        ) {
                            Text(
                                text = statusSummary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = SettingsBadgeShape,
                                color = Color.Black.copy(alpha = 0.48f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ZoomIn,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "点按预览",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun saveWallpaperFile(url: String, target: File) {
    // Scoped storage accepts images in Pictures/DCIM but refuses generic .tmp files there.
    val temp = File.createTempFile(".wallpaper-", ".${target.extension}", target.parentFile)
    try {
        if (!WikiEngine.client.awaitGetToFile(url, temp) || temp.length() == 0L) {
            throw IOException("Empty or failed wallpaper download")
        }
        currentCoroutineContext().ensureActive()
        Os.rename(temp.absolutePath, target.absolutePath)
    } finally {
        temp.delete()
    }
}

// ─────────────────────── 配色风格选择器 ───────────────────────

private data class PaletteStyleInfo(
    val index: Int,
    val label: String,
    val description: String
)

private data class ContrastLevelInfo(
    val index: Int,
    val label: String,
    val description: String
)

private val CONTRAST_LEVEL_OPTIONS = listOf(
    ContrastLevelInfo(AppPrefs.CONTRAST_DEFAULT, "默认", "标准对比度"),
    ContrastLevelInfo(AppPrefs.CONTRAST_MEDIUM, "中", "提高对比度"),
    ContrastLevelInfo(AppPrefs.CONTRAST_HIGH, "高", "最大对比度"),
    ContrastLevelInfo(AppPrefs.CONTRAST_REDUCED, "降低", "降低对比度"),
)

@Composable
private fun ContrastLevelPicker(
    currentIndex: Int,
    onSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentInfo = CONTRAST_LEVEL_OPTIONS.firstOrNull { it.index == currentIndex }
        ?: CONTRAST_LEVEL_OPTIONS.first()

    SettingsItem(
        icon = Icons.Outlined.Contrast,
        title = "对比度",
        value = currentInfo.label,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        SettingsDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择对比度") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    CONTRAST_LEVEL_OPTIONS.forEach { info ->
                        val isSelected = info.index == currentIndex
                        val colors = MaterialTheme.colorScheme
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SettingsIconFrameShape)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) colors.primary else colors.outlineVariant.copy(alpha = 0.35f),
                                    shape = SettingsIconFrameShape
                                )
                                .clickable {
                                    onSelected(info.index)
                                    showDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(30.dp),
                                shape = SettingsBadgeShape,
                                color = if (isSelected) colors.primaryContainer else colors.surfaceContainerLow,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) colors.primary.copy(alpha = 0.4f) else colors.outlineVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选",
                                            modifier = Modifier.size(16.dp),
                                            tint = colors.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    info.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) colors.primary else colors.onSurface
                                )
                                Text(
                                    info.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            shape = AppShapes.dialog,
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

private val PALETTE_STYLE_OPTIONS = listOf(
    PaletteStyleInfo(0, "柔和", "均衡的默认风格"),
    PaletteStyleInfo(1, "中性", "降低饱和度"),
    PaletteStyleInfo(2, "鲜艳", "提高饱和度"),
    PaletteStyleInfo(3, "表现", "更丰富的色彩"),
    PaletteStyleInfo(4, "彩虹", "使用多种色相"),
    PaletteStyleInfo(5, "缤纷", "提高色彩对比"),
    PaletteStyleInfo(6, "单色", "接近灰度"),
    PaletteStyleInfo(7, "保真", "更接近主题色"),
    PaletteStyleInfo(8, "内容", "适合阅读界面"),
)

@Composable
private fun PaletteStylePicker(
    currentIndex: Int,
    onStyleSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentInfo = PALETTE_STYLE_OPTIONS.getOrNull(currentIndex) ?: PALETTE_STYLE_OPTIONS[0]

    SettingsItem(
        icon = Icons.Outlined.Tune,
        title = "配色风格",
        value = currentInfo.label,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        SettingsDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择配色风格") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(
                        "选择主题色生成配色的方式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppSpacing.xSmall))

                    PALETTE_STYLE_OPTIONS.forEach { info ->
                        val isSelected = info.index == currentIndex
                        val colors = MaterialTheme.colorScheme
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SettingsIconFrameShape)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) colors.primary else colors.outlineVariant.copy(alpha = 0.35f),
                                    shape = SettingsIconFrameShape
                                )
                                .clickable {
                                    onStyleSelected(info.index)
                                    showDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(30.dp),
                                shape = SettingsBadgeShape,
                                color = if (isSelected) colors.primaryContainer else colors.surfaceContainerLow,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) colors.primary.copy(alpha = 0.4f) else colors.outlineVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选",
                                            modifier = Modifier.size(16.dp),
                                            tint = colors.onPrimaryContainer
                                        )
                                    } else {
                                        Text(
                                            text = "${info.index}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFeatureSettings = "tnum"
                                            ),
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    info.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) colors.primary else colors.onSurface
                                )
                                Text(
                                    info.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            shape = AppShapes.dialog,
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
