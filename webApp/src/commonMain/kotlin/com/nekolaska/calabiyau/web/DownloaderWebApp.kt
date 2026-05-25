package com.nekolaska.calabiyau.web

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calabiyauvoice_gui.webapp.generated.resources.Res
import calabiyauvoice_gui.webapp.generated.resources.noto_sans_cjk_sc_regular
import org.jetbrains.compose.resources.Font
import kotlinx.browser.window

private enum class DownloadMode(val label: String, val description: String) {
    Voice("语音", "按角色语音分类下载"),
    Category("分类", "选择 Wiki 分类中的资源"),
    FileSearch("文件搜索", "按文件名直接筛选资源"),
    Portrait("立绘", "角色立绘与皮肤预览")
}

private data class WebDownloadItem(
    val title: String,
    val detail: String,
    val count: Int,
    val enabled: Boolean = true
)

private const val LatestReleaseUrl = "https://calabiyauwiki.pages.dev/"

@Composable
internal fun DownloaderWebApp() {
    val darkColors = androidx.compose.material3.darkColorScheme(
        primary = Color(0xFFAEC6FF), 
        onPrimary = Color(0xFF002E69),
        primaryContainer = Color(0xFF19448F),
        onPrimaryContainer = Color(0xFFD8E2FF),
        secondary = Color(0xFFE8DEF8), 
        onSecondary = Color(0xFF4A3E5C),
        secondaryContainer = Color(0xFF635575),
        onSecondaryContainer = Color(0xFFFFD6F9),
        background = Color(0xFF0A0C12), 
        surface = Color(0xFF141620), 
        surfaceVariant = Color(0xFF282A36),
        onBackground = Color(0xFFE2E2E9),
        onSurface = Color(0xFFE2E2E9),
        onSurfaceVariant = Color(0xFFBEC2D3),
        outline = Color(0xFF8E9099)
    )

    MaterialTheme(
        colorScheme = darkColors,
        typography = webTypography(FontFamily(Font(Res.font.noto_sans_cjk_sc_regular)))
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF452A68), // 渐变中心：优雅深紫
                                Color(0xFF1E2849), // 渐变过渡：静谧夜蓝
                                Color(0xFF080A10)  // 渐变边缘：深邃极暗
                            ),
                            radius = 2000f,
                            center = androidx.compose.ui.geometry.Offset(x = 0f, y = 0f) // 左上角发光效果
                        )
                    )
            ) {
                DownloaderContent()
            }
        }
    }
}

@Composable
private fun webTypography(cjkFontFamily: FontFamily) = MaterialTheme.typography.copy(
    displayLarge = MaterialTheme.typography.displayLarge.cjk(cjkFontFamily),
    displayMedium = MaterialTheme.typography.displayMedium.cjk(cjkFontFamily),
    displaySmall = MaterialTheme.typography.displaySmall.cjk(cjkFontFamily),
    headlineLarge = MaterialTheme.typography.headlineLarge.cjk(cjkFontFamily),
    headlineMedium = MaterialTheme.typography.headlineMedium.cjk(cjkFontFamily),
    headlineSmall = MaterialTheme.typography.headlineSmall.cjk(cjkFontFamily),
    titleLarge = MaterialTheme.typography.titleLarge.cjk(cjkFontFamily),
    titleMedium = MaterialTheme.typography.titleMedium.cjk(cjkFontFamily),
    titleSmall = MaterialTheme.typography.titleSmall.cjk(cjkFontFamily),
    bodyLarge = MaterialTheme.typography.bodyLarge.cjk(cjkFontFamily),
    bodyMedium = MaterialTheme.typography.bodyMedium.cjk(cjkFontFamily),
    bodySmall = MaterialTheme.typography.bodySmall.cjk(cjkFontFamily),
    labelLarge = MaterialTheme.typography.labelLarge.cjk(cjkFontFamily),
    labelMedium = MaterialTheme.typography.labelMedium.cjk(cjkFontFamily),
    labelSmall = MaterialTheme.typography.labelSmall.cjk(cjkFontFamily),
)

private fun TextStyle.cjk(cjkFontFamily: FontFamily): TextStyle = copy(
    fontFamily = cjkFontFamily,
    fontSize = if (fontSize == 0.sp) 14.sp else fontSize
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloaderContent() {
    var keyword by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(DownloadMode.Voice) }
    var selectedItem by remember { mutableStateOf<WebDownloadItem?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var showApkDialog by remember { mutableStateOf(false) }
    val logs = remember {
        mutableStateListOf(
            "Web 下载器界面已初始化",
            "欢迎使用 CalabiYauVoice Web 客户端 (MD3 模糊剔透版)。"
        )
    }

    val items = remember(mode, keyword) { sampleItems(mode, keyword) }
    val pendingCount = items.filter { it.enabled }.sumOf { it.count }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 16.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Header(
                keyword = keyword,
                onKeywordChange = { keyword = it },
                onSearch = {
                    logs.add(0, "发现索引：${keyword.ifBlank { "全部资源" }} / ${mode.label}")
                },
                onShowApkDialog = { showApkDialog = true }
            )

            if (showApkDialog) {
                ApkDownloadDialog(onDismiss = { showApkDialog = false })
            }

            if (compact) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ResourcePanel(
                            mode = mode,
                            items = items,
                            selectedItem = selectedItem,
                            onModeSelected = { mode = it; selectedItem = null },
                            onSelectItem = { selectedItem = it }
                        )
                    }
                    item {
                        DownloadPanel(
                            selectedItem = selectedItem,
                            pendingCount = pendingCount,
                            isDownloading = isDownloading,
                            progress = progress,
                            logs = logs,
                            onStartDownload = {
                                isDownloading = true
                                progress = 0.42f
                                logs.add(0, "🚀 进程激活：${selectedItem?.title ?: mode.label}")
                            },
                            onStopDownload = {
                                isDownloading = false
                                progress = 0f
                                logs.add(0, "⏹ 进程已终止")
                            }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ResourcePanel(
                        mode = mode,
                        items = items,
                        selectedItem = selectedItem,
                        onModeSelected = { mode = it; selectedItem = null },
                        onSelectItem = { selectedItem = it },
                        modifier = Modifier.weight(1.25f).fillMaxHeight()
                    )
                    DownloadPanel(
                        selectedItem = selectedItem,
                        pendingCount = pendingCount,
                        isDownloading = isDownloading,
                        progress = progress,
                        logs = logs,
                        onStartDownload = {
                            isDownloading = true
                            progress = 0.42f
                            logs.add(0, "🚀 进程激活：${selectedItem?.title ?: mode.label}")
                        },
                        onStopDownload = {
                            isDownloading = false
                            progress = 0f
                            logs.add(0, "⏹ 进程已终止")
                        },
                        modifier = Modifier.weight(0.95f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onShowApkDialog: () -> Unit
) {
    // 玻璃拟态 Card (Glassmorphism)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "CalabiYauVoice",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary, // 柔和蓝
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Web 资源枢纽中心",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                singleLine = true,
                placeholder = { Text("探索引擎...") },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1.2f),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color.White.copy(alpha = 0.02f),
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Button(
                onClick = onSearch,
                shape = RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 16.dp)
            ) {
                Text("检索", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            OutlinedButton(
                onClick = onShowApkDialog,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp)
            ) {
                Text("下载 APK", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun ApkDownloadDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Android APK 下载", fontWeight = FontWeight.ExtraBold)
                Text(
                    text = "最新版本：GitHub Releases Latest",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("版本说明", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("打开轻量下载页后，可查看最新版本、更新日志并下载 APK。")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("更新日志", fontWeight = FontWeight.Bold)
                    Text("• 新增 Compose Web 下载器入口")
                    Text("• 支持 Web 端浏览资源分类与下载队列 UI")
                    Text("• 修复 Web 中文字体显示问题")
                    Text("• 优化 Wasm 打包资源与启动页")
                }

                Text(
                    text = "如果浏览器没有自动开始下载，请检查是否被浏览器拦截了新窗口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    window.open(LatestReleaseUrl, "_blank")
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("打开下载页")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeBar(
    selectedMode: DownloadMode,
    onModeSelected: (DownloadMode) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DownloadMode.entries.forEach { mode ->
            val isSelected = selectedMode == mode
            FilterChip(
                selected = isSelected,
                onClick = { onModeSelected(mode) },
                label = {
                    Text(
                        text = mode.label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                shape = CircleShape,
                modifier = Modifier.defaultMinSize(minHeight = 42.dp, minWidth = 88.dp),
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    containerColor = Color.Transparent
                ),
                border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    borderColor = Color.White.copy(alpha = 0.15f),
                    enabled = true,
                    selected = isSelected
                )
            )
        }
    }
}

@Composable
private fun ResourcePanel(
    mode: DownloadMode,
    items: List<WebDownloadItem>,
    selectedItem: WebDownloadItem?,
    onModeSelected: (DownloadMode) -> Unit,
    onSelectItem: (WebDownloadItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ModeBar(selectedMode = mode, onModeSelected = onModeSelected)
            
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(mode.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(mode.description, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 14.sp)
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "${items.size} 枚",
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 13.sp
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items) { item ->
                    ResourceCard(
                        item = item,
                        selected = item == selectedItem,
                        onClick = { onSelectItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourceCard(
    item: WebDownloadItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    // 选中时带有强烈发光感的主题紫/蓝，未选中时则是极淡的透明白
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.02f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f)

    androidx.compose.material3.OutlinedCard(
        onClick = onClick,
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor, contentColor = contentColor),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.enabled) {
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        } else Color.White.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.title.take(1),
                    color = if (selected || !item.enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.detail, color = if (selected) contentColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color.Transparent else Color.White.copy(alpha = 0.1f))
            ) {
                Text(
                    text = "${item.count}",
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun DownloadPanel(
    selectedItem: WebDownloadItem?,
    pendingCount: Int,
    isDownloading: Boolean,
    progress: Float,
    logs: List<String>,
    onStartDownload: () -> Unit,
    onStopDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("任务控制台", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            QueueSummary(selectedItem = selectedItem, pendingCount = pendingCount)

            if (isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("流数据接收中...", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStartDownload,
                    enabled = !isDownloading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Text("核心驱动下载", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                OutlinedButton(
                    onClick = onStopDownload,
                    enabled = isDownloading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDownloading) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f))
                ) {
                    Text("阻断进程", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            
            Text("控制台输出", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Surface(
                color = Color.Black.copy(alpha = 0.25f), // 加深背景产生下沉感
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs) { line ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(">", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                            Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSummary(selectedItem: WebDownloadItem?, pendingCount: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), // 极淡的底色
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = selectedItem?.title ?: "队列未指定",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = selectedItem?.detail ?: "选择左侧资源索引或使用全局任务",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "待处理文件数: ${selectedItem?.count ?: pendingCount}",
                    color = MaterialTheme.colorScheme.primary, // 文字使用主色，增加科技亮光感
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun sampleItems(mode: DownloadMode, keyword: String): List<WebDownloadItem> {
    val all = when (mode) {
        DownloadMode.Voice -> listOf(
            WebDownloadItem("星绘", "战斗语音 / 互动语音 / 系统语音", 128),
            WebDownloadItem("明", "角色语音与剧情片段", 96),
            WebDownloadItem("心夏", "语音资源分类包", 84)
        )
        DownloadMode.Category -> listOf(
            WebDownloadItem("Category:角色语音", "批量下载分类中的音频文件", 420),
            WebDownloadItem("Category:武器图片", "武器图标、皮肤和预览图", 180),
            WebDownloadItem("Category:活动素材", "活动页图片与背景资源", 260)
        )
        DownloadMode.FileSearch -> listOf(
            WebDownloadItem("voice_cn_akari_001.ogg", "匹配文件名：voice / akari", 1),
            WebDownloadItem("portrait_ming_skin_02.png", "匹配文件名：portrait / skin", 1),
            WebDownloadItem("bgm_lobby_theme.flac", "匹配文件名：bgm / theme", 1)
        )
        DownloadMode.Portrait -> listOf(
            WebDownloadItem("星绘 / 默认", "立绘、正面预览、背面预览", 5),
            WebDownloadItem("明 / 特典", "皮肤立绘与额外资产", 7),
            WebDownloadItem("心夏 / 夏日", "角色立绘套装", 6)
        )
    }
    val q = keyword.trim()
    return if (q.isBlank()) all else all.filter { it.title.contains(q, ignoreCase = true) || it.detail.contains(q, ignoreCase = true) }
}
