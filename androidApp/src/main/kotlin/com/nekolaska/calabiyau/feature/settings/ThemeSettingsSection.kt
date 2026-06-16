package com.nekolaska.calabiyau.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.launcher.LauncherIconTheme
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
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
import okhttp3.Request
import java.io.File

private val PRESET_COLORS = listOf(
    AppPrefs.SEED_WALLPAPER to "跟随背景图",
    0 to "系统默认",
    0xFF4285F4.toInt() to "蓝色",
    0xFF0F9D58.toInt() to "绿色",
    0xFFDB4437.toInt() to "红色",
    0xFFF4B400.toInt() to "琥珀",
    0xFF9C27B0.toInt() to "紫色",
    0xFF00BCD4.toInt() to "青色",
    0xFFFF5722.toInt() to "橙色",
    0xFF607D8B.toInt() to "蓝灰",
    0xFFE91E63.toInt() to "粉色",
    0xFF3F51B5.toInt() to "靛蓝",
    0xFF009688.toInt() to "蓝绿",
    0xFF795548.toInt() to "棕色",
)

@Composable
internal fun AppearanceSettingsSection() {
    val globalThemeMode = LocalThemeMode.current
    val globalSeedColor = LocalSeedColor.current
    val globalLiquidGlass = LocalLiquidGlassEnabled.current
    val globalHighReadability = LocalHighReadabilityDrawer.current
    val globalPaletteStyle = LocalPaletteStyle.current

    var themeMode by globalThemeMode
    var seedColorInt by globalSeedColor
    var liquidGlassEnabled by globalLiquidGlass
    var highReadabilityDrawer by globalHighReadability
    var paletteStyleIndex by globalPaletteStyle
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
                title = "主题模式",
                subtitle = themeName,
                onClick = { showThemeDialog = true }
            )

            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text("选择主题") },
                    text = {
                        Column {
                            listOf(
                                AppPrefs.THEME_SYSTEM to "跟随系统",
                                AppPrefs.THEME_LIGHT to "浅色模式",
                                AppPrefs.THEME_DARK to "深色模式"
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
                title = "应用图标",
                subtitle = launcherIconName,
                onClick = { showLauncherIconDialog = true }
            )
            if (showLauncherIconDialog) {
                val context = LocalContext.current
                AlertDialog(
                    onDismissRequest = { showLauncherIconDialog = false },
                    title = { Text("应用图标") },
                    text = {
                        Column {
                            Text(
                                "切换后桌面图标可能需要几秒刷新，部分启动器会缓存旧图标。",
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
                title = "液态玻璃效果",
                subtitle = "需 Android 12+",
                checked = liquidGlassEnabled,
                onCheckedChange = {
                    liquidGlassEnabled = it
                    AppPrefs.liquidGlassEnabled = it
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
            SettingsToggleItem(
                icon = Icons.Outlined.Visibility,
                title = "高可读性侧栏",
                subtitle = "增强液态玻璃侧栏文字和选中项对比",
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
    val currentName = PRESET_COLORS.firstOrNull { it.first == currentSeedColor }?.second
        ?: if (currentSeedColor == 0) "系统默认" else "自定义"

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
                    hsv[0] = 210f; hsv[1] = 0.7f; hsv[2] = 0.8f
                }
            }
        }
        var hue by remember { mutableFloatStateOf(initHsv[0]) }
        var saturation by remember { mutableFloatStateOf(initHsv[1]) }
        var value by remember { mutableFloatStateOf(initHsv[2]) }
        var showCustomPicker by remember {
            mutableStateOf(
                currentSeedColor > 0 && PRESET_COLORS.none { it.first == currentSeedColor }
            )
        }

        val customColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择主题色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rows = PRESET_COLORS.chunked(4)
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { (argb, label) ->
                                val isSelected = argb == currentSeedColor && !showCustomPicker
                                val displayColor = when (argb) {
                                    AppPrefs.SEED_WALLPAPER -> if (wallpaperSeedArgb != 0) Color(wallpaperSeedArgb)
                                    else MaterialTheme.colorScheme.primary

                                    0 -> MaterialTheme.colorScheme.primary
                                    else -> Color(argb)
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(smoothCornerShape(14.dp))
                                        .clickable {
                                            showCustomPicker = false
                                            onColorSelected(argb)
                                            showDialog = false
                                        }
                                        .then(
                                            if (isSelected) Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                smoothCornerShape(14.dp)
                                            ) else Modifier
                                        )
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(displayColor)
                                            .then(
                                                if (argb == 0) Modifier.border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    CircleShape
                                                ) else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "已选",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        if (argb == 0 && !isSelected) {
                                            Icon(
                                                Icons.Outlined.Palette,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(smoothCornerShape(12.dp))
                            .clickable { showCustomPicker = !showCustomPicker }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
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
                                    .height(48.dp)
                                    .clip(smoothCornerShape(16.dp))
                                    .background(customColor),
                                contentAlignment = Alignment.Center
                            ) {
                                val hexStr = String.format("#%06X", customColor.toArgb() and 0xFFFFFF)
                                Text(
                                    hexStr,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                "色相",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = hue,
                                onValueChange = { hue = it },
                                valueRange = 0f..360f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                "饱和度",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = saturation,
                                onValueChange = { saturation = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                "明度",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = value,
                                onValueChange = { value = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
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
                        Text("应用自定义颜色")
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
        title = "自动刷新壁纸",
        subtitle = "启动时随机更换首页背景图",
        checked = wallpaperAutoRefresh,
        onCheckedChange = {
            wallpaperAutoRefresh = it
            AppPrefs.wallpaperAutoRefresh = it
        }
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
    SettingsItem(
        icon = Icons.Outlined.Refresh,
        title = "刷新首页背景图",
        subtitle = if (isRefreshing) "正在获取新壁纸…"
        else wallpaperMessage ?: "随机更换一张 Wiki 壁纸",
        onClick = {
            if (isRefreshing) return@SettingsItem
            isRefreshing = true
            wallpaperMessage = null
            scope.launch {
                val url = withContext(Dispatchers.IO) {
                    WallpaperApi.fetchRandomWallpaperUrl(forceRefresh = true)
                }
                wallpaperMessage = if (url != null) "已刷新，返回首页查看"
                else "获取失败，请检查网络"
                isRefreshing = false
            }
        }
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.screen))
    SettingsItem(
        icon = Icons.Outlined.SaveAlt,
        title = "保存当前背景图",
        subtitle = if (isSaving) "正在保存…"
        else "将当前壁纸保存到下载目录",
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
                        val request = Request.Builder()
                            .url(currentUrl).build()
                        WikiEngine.client.newCall(request).execute().use { resp ->
                            if (!resp.isSuccessful) return@withContext false
                            resp.body.byteStream().use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
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

private val PALETTE_STYLE_OPTIONS = listOf(
    PaletteStyleInfo(0, "TonalSpot", "柔和均衡，M3 默认风格"),
    PaletteStyleInfo(1, "Neutral", "低饱和中性色调"),
    PaletteStyleInfo(2, "Vibrant", "高饱和活力色彩"),
    PaletteStyleInfo(3, "Expressive", "表现力强，色彩丰富"),
    PaletteStyleInfo(4, "Rainbow", "彩虹色谱，多彩渐变"),
    PaletteStyleInfo(5, "FruitSalad", "水果拼盘，缤纷混搭"),
    PaletteStyleInfo(6, "Monochrome", "单色灰阶风格"),
    PaletteStyleInfo(7, "Fidelity", "高保真，贴近种子色"),
    PaletteStyleInfo(8, "Content", "内容驱动，自适应配色"),
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
        subtitle = "${currentInfo.label} — ${currentInfo.description}",
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择配色风格") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "配色风格会影响主题色衍生出的 primary、secondary、tertiary 等色彩的视觉特征。",
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
