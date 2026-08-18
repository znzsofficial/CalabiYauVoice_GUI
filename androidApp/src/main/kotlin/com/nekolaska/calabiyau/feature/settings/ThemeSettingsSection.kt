package com.nekolaska.calabiyau.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.launcher.LauncherIconTheme
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.LocalAmoledDark
import com.nekolaska.calabiyau.core.ui.LocalColorSpec2025
import com.nekolaska.calabiyau.core.ui.LocalContrastLevel
import com.nekolaska.calabiyau.core.ui.LocalHighReadabilityDrawer
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.LocalPaletteStyle
import com.nekolaska.calabiyau.core.ui.LocalSeedColor
import com.nekolaska.calabiyau.core.ui.LocalThemeMode
import com.nekolaska.calabiyau.core.ui.LocalWallpaperSeedColor
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.feature.wiki.gallery.WallpaperApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import util.bodyToFile
import util.executeGet
import java.io.File

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

@Composable
internal fun AppearanceSettingsSection() {
    val globalThemeMode = LocalThemeMode.current
    val globalSeedColor = LocalSeedColor.current
    val globalLiquidGlass = LocalLiquidGlassEnabled.current
    val globalHighReadability = LocalHighReadabilityDrawer.current
    val globalPaletteStyle = LocalPaletteStyle.current
    val globalContrastLevel = LocalContrastLevel.current
    val globalAmoledDark = LocalAmoledDark.current
    val globalColorSpec2025 = LocalColorSpec2025.current

    var themeMode by globalThemeMode
    var seedColorInt by globalSeedColor
    var liquidGlassEnabled by globalLiquidGlass
    var highReadabilityDrawer by globalHighReadability
    var paletteStyleIndex by globalPaletteStyle
    var contrastLevel by globalContrastLevel
    var amoledDark by globalAmoledDark
    var colorSpec2025 by globalColorSpec2025
    var launcherIconTheme by remember { mutableIntStateOf(AppPrefs.launcherIconTheme) }

    // ── 主题配色 ──
    SettingsGroupHeader("主题配色")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screen),
        shape = AppShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            // 主题模式
            var showThemeDialog by remember { mutableStateOf(false) }
            val themeName = when (themeMode) {
                AppPrefs.THEME_LIGHT -> "浅色"
                AppPrefs.THEME_DARK -> "深色"
                else -> "跟随系统"
            }
            SettingsItem(
                icon = Icons.Outlined.Palette,
                title = "外观",
                subtitle = themeName,
                onClick = { showThemeDialog = true }
            )

            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text("外观") },
                    text = {
                        Column {
                            listOf(
                                AppPrefs.THEME_SYSTEM to "跟随系统",
                                AppPrefs.THEME_LIGHT to "浅色",
                                AppPrefs.THEME_DARK to "深色"
                            ).forEach { (mode, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(smoothCornerShape(12.dp))
                                        .clickable {
                                            themeMode = mode
                                            AppPrefs.themeMode = mode
                                            showThemeDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = themeMode == mode,
                                        onClick = {
                                            themeMode = mode
                                            AppPrefs.themeMode = mode
                                            showThemeDialog = false
                                        }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    },
                    shape = AppShapes.dialog,
                    confirmButton = {}
                )
            }

            // 主题色
            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
            ThemeColorPicker(
                currentSeedColor = seedColorInt,
                onColorSelected = { argb ->
                    seedColorInt = argb
                    AppPrefs.customSeedColor = argb
                }
            )

            // 配色风格
            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
            PaletteStylePicker(
                currentIndex = paletteStyleIndex,
                onStyleSelected = { index ->
                    paletteStyleIndex = index
                    AppPrefs.paletteStyle = index
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
            ContrastLevelPicker(
                currentIndex = contrastLevel,
                onSelected = { index ->
                    contrastLevel = index
                    AppPrefs.contrastLevel = index
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
            SettingsToggleItem(
                icon = Icons.Outlined.DarkMode,
                title = "纯黑模式",
                subtitle = "深色外观下使用纯黑背景",
                checked = amoledDark,
                onCheckedChange = {
                    amoledDark = it
                    AppPrefs.amoledDark = it
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
            SettingsToggleItem(
                icon = Icons.Outlined.AutoAwesome,
                title = "动态配色",
                subtitle = if (colorSpec2025) "2025 规范" else "2021 规范",
                checked = colorSpec2025,
                onCheckedChange = {
                    colorSpec2025 = it
                    AppPrefs.colorSpec2025 = it
                }
            )
        }
    }

    // ── 视觉效果 ──
    Spacer(Modifier.height(AppSpacing.itemGap))
    SettingsGroupHeader("视觉效果")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screen),
        shape = AppShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            // 应用图标
            var showLauncherIconDialog by remember { mutableStateOf(false) }
            val launcherIconName = when (launcherIconTheme) {
                AppPrefs.LAUNCHER_ICON_SYSTEM -> "系统强调色"
                else -> "品牌深色"
            }
            SettingsItem(
                icon = Icons.Outlined.Palette,
                title = "图标",
                subtitle = launcherIconName,
                onClick = { showLauncherIconDialog = true }
            )
            if (showLauncherIconDialog) {
                val context = LocalContext.current
                AlertDialog(
                    onDismissRequest = { showLauncherIconDialog = false },
                    title = { Text("图标") },
                    text = {
                        Column {
                            Text(
                                "更改后，主屏幕图标可能需要片刻才会更新。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(AppSpacing.medium))
                            listOf(
                                AppPrefs.LAUNCHER_ICON_BRAND to "品牌深色",
                                AppPrefs.LAUNCHER_ICON_SYSTEM to "系统强调色"
                            ).forEach { (theme, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(smoothCornerShape(12.dp))
                                        .clickable {
                                            launcherIconTheme = theme
                                            AppPrefs.launcherIconTheme = theme
                                            LauncherIconTheme.apply(context, theme)
                                            showLauncherIconDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = launcherIconTheme == theme,
                                        onClick = {
                                            launcherIconTheme = theme
                                            AppPrefs.launcherIconTheme = theme
                                            LauncherIconTheme.apply(context, theme)
                                            showLauncherIconDialog = false
                                        }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    },
                    shape = AppShapes.dialog,
                    confirmButton = {}
                )
            }

            // 液态玻璃
            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
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

            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
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

    // ── 壁纸 ──
    Spacer(Modifier.height(AppSpacing.itemGap))
    SettingsGroupHeader("壁纸")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screen),
        shape = AppShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        WallpaperItems()
    }
}

// ─────────────────────── 主题色选择器 ───────────────────────

@Composable
private fun ThemeColorPicker(
    currentSeedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val wallpaperSeedArgb = LocalWallpaperSeedColor.current.intValue
    val currentName = THEME_COLOR_SOURCES.firstOrNull { it.value == currentSeedColor }?.label
        ?: PRESET_SWATCHES.firstOrNull { it.first == currentSeedColor }?.second
        ?: if (currentSeedColor == 0) "系统" else "自定义"

    SettingsItem(
        icon = Icons.Outlined.ColorLens,
        title = "主题色",
        subtitle = currentName,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        val initHsv = remember {
            FloatArray(3).also { hsv ->
                if (currentSeedColor > 0) {
                    android.graphics.Color.colorToHSV(currentSeedColor, hsv)
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
            mutableStateOf(currentSeedColor > 0 && !isPresetSelected)
        }
        val customColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
        val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择主题色") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                                onColorSelected(source.value)
                                showDialog = false
                            }
                        )
                    }

                    Text(
                        "预设颜色",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PRESET_SWATCHES.chunked(6).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { (argb, label) ->
                                val isSelected = argb == currentSeedColor && !showCustomPicker
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .then(
                                            if (isSelected) Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape
                                            ) else Modifier
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
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(smoothCornerShape(14.dp))
                            .clickable { showCustomPicker = !showCustomPicker }
                            .padding(vertical = 6.dp),
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
                                    .height(52.dp)
                                    .clip(smoothCornerShape(16.dp))
                                    .background(customColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    String.format("#%06X", customColor.toArgb() and 0xFFFFFF),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
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
                        onColorSelected(customColor.toArgb())
                        showDialog = false
                    }) {
                        Text("应用")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(smoothCornerShape(14.dp))
            .then(
                if (selected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    smoothCornerShape(14.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(swatch),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
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
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorChannelSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    trackBrush: Brush
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
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
private fun WallpaperItems() {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var wallpaperMessage by remember { mutableStateOf<String?>(null) }
    var wallpaperAutoRefresh by remember { mutableStateOf(AppPrefs.wallpaperAutoRefresh) }

    Column {
        SettingsToggleItem(
        icon = Icons.Outlined.Autorenew,
        title = "自动更换",
        subtitle = "启动时随机更换壁纸",
        checked = wallpaperAutoRefresh,
        onCheckedChange = {
            wallpaperAutoRefresh = it
            AppPrefs.wallpaperAutoRefresh = it
        }
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
    SettingsItem(
        icon = Icons.Outlined.Refresh,
        title = "立即更换",
        subtitle = if (isRefreshing) "正在获取…"
        else wallpaperMessage ?: "随机更换一张壁纸",
        onClick = {
            if (isRefreshing) return@SettingsItem
            isRefreshing = true
            wallpaperMessage = null
            scope.launch {
                val url = withContext(Dispatchers.IO) {
                    WallpaperApi.fetchRandomWallpaperUrl(forceRefresh = true)
                }
                wallpaperMessage = if (url != null) "已更换" else "获取失败"
                isRefreshing = false
            }
        }
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
    SettingsItem(
        icon = Icons.Outlined.SaveAlt,
        title = "存储到“下载”",
        subtitle = if (isSaving) "正在存储…"
        else "将当前壁纸存储到下载目录",
        onClick = {
            val currentUrl = AppPrefs.wallpaperUrl
            if (currentUrl.isNullOrBlank() || isSaving) return@SettingsItem
            isSaving = true
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        val fileName = currentUrl.substringAfterLast("/")
                            .substringBefore("?")
                            .ifBlank { "wallpaper_${System.currentTimeMillis()}.jpg" }
                        val saveDir = File(AppPrefs.savePath)
                        if (!saveDir.exists()) saveDir.mkdirs()
                        val destFile = File(saveDir, fileName)
                        WikiEngine.client.executeGet(currentUrl).use { resp ->
                            if (!resp.isSuccessful) return@withContext false
                            resp.bodyToFile(destFile)
                        }
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                wallpaperMessage = if (success) "已保存到 ${AppPrefs.savePath}"
                else "保存失败"
                isSaving = false
            }
        }
    )
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
        subtitle = currentInfo.label,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择对比度") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CONTRAST_LEVEL_OPTIONS.forEach { info ->
                        val isSelected = info.index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(smoothCornerShape(14.dp))
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        smoothCornerShape(14.dp)
                                    ) else Modifier
                                )
                                .clickable {
                                    onSelected(info.index)
                                    showDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
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
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    info.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            shape = AppShapes.dialog,
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
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
        subtitle = currentInfo.label,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择配色风格") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "选择主题色生成配色的方式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppSpacing.medium))

                    PALETTE_STYLE_OPTIONS.forEach { info ->
                        val isSelected = info.index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(smoothCornerShape(14.dp))
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        smoothCornerShape(14.dp)
                                    ) else Modifier
                                )
                                .clickable {
                                    onStyleSelected(info.index)
                                    showDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        Text(
                                            "${info.index}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    info.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            shape = AppShapes.dialog,
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
