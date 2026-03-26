package com.nekolaska.calabiyau.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.data.AppPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var savePath by remember { mutableStateOf(AppPrefs.savePath) }
    var maxConcurrency by remember { mutableStateOf(AppPrefs.maxConcurrency.toString()) }
    var showAbout by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // SAF 目录选择器
    val dirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 获取实际路径或使用 URI 的 path 部分
            val path = getPathFromUri(context, it)
            if (path != null) {
                savePath = path
                AppPrefs.savePath = path
            }
        }
    }

    // 拦截返回键：关于页面内返回到设置，设置页面返回到主界面
    BackHandler(enabled = showAbout) {
        showAbout = false
    }
    BackHandler(enabled = !showAbout) {
        onBack()
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "设置",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))

            // 下载设置分组
            SettingsGroupHeader("下载设置")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    // 保存路径
                    SettingsItem(
                        icon = Icons.Outlined.Folder,
                        title = "保存路径",
                        subtitle = savePath,
                        onClick = { dirPicker.launch(null) }
                    )

                    // 快速打开保存路径
                    SettingsItem(
                        icon = Icons.Outlined.FolderOpen,
                        title = "打开保存目录",
                        subtitle = "在文件管理器中查看",
                        onClick = {
                            val dir = java.io.File(savePath)
                            dir.mkdirs()
                            val storagePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                            val relativePath = savePath.removePrefix("$storagePath/")
                            val encodedPath = Uri.encode(relativePath, "/")
                            val docUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:$encodedPath")
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(docUri, "vnd.android.document/directory")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                        putExtra("android.content.extra.SHOW_ADVANCED", true)
                                        putExtra("android.provider.extra.INITIAL_URI", docUri)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                    )

                    // 保存路径手动输入
                    var showPathDialog by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Outlined.Edit,
                        title = "手动设置路径",
                        subtitle = "手动输入自定义保存路径",
                        onClick = { showPathDialog = true }
                    )

                    if (showPathDialog) {
                        var tempPath by remember { mutableStateOf(savePath) }
                        AlertDialog(
                            onDismissRequest = { showPathDialog = false },
                            title = { Text("设置保存路径") },
                            text = {
                                OutlinedTextField(
                                    value = tempPath,
                                    onValueChange = { tempPath = it },
                                    label = { Text("路径") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 3,
                                    shape = RoundedCornerShape(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(28.dp),
                            confirmButton = {
                                FilledTonalButton(onClick = {
                                    savePath = tempPath
                                    AppPrefs.savePath = tempPath
                                    showPathDialog = false
                                }) { Text("确定") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPathDialog = false }) { Text("取消") }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 性能设置
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    // 最大并发数
                    var showConcurrencyDialog by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Outlined.Speed,
                        title = "最大并发下载数",
                        subtitle = "$maxConcurrency 个并发连接",
                        onClick = { showConcurrencyDialog = true }
                    )

                    if (showConcurrencyDialog) {
                        var tempConcurrency by remember { mutableStateOf(maxConcurrency) }
                        AlertDialog(
                            onDismissRequest = { showConcurrencyDialog = false },
                            title = { Text("最大并发下载数") },
                            text = {
                                Column {
                                    Text(
                                        "设置同时下载文件的最大数量（1-32）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = tempConcurrency,
                                        onValueChange = { tempConcurrency = it.filter { c -> c.isDigit() } },
                                        label = { Text("并发数") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            confirmButton = {
                                FilledTonalButton(onClick = {
                                    val value = tempConcurrency.toIntOrNull()?.coerceIn(1, 32) ?: 8
                                    maxConcurrency = value.toString()
                                    AppPrefs.maxConcurrency = value
                                    showConcurrencyDialog = false
                                }) { Text("确定") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConcurrencyDialog = false }) { Text("取消") }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 关于分组
            SettingsGroupHeader("关于")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "关于",
                        subtitle = "版本信息与版权声明",
                        onClick = { showAbout = true }
                    )

                    SettingsItem(
                        icon = Icons.Outlined.Code,
                        title = "开源仓库",
                        subtitle = "github.com/znzsofficial/CalabiYauVoice_GUI",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/znzsofficial/CalabiYauVoice_GUI"))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 从 SAF 返回的 URI 中提取可读路径
 */
private fun getPathFromUri(context: android.content.Context, uri: Uri): String? {
    // 对于 content:// URI，尝试解析实际路径
    val docId = try {
        android.provider.DocumentsContract.getTreeDocumentId(uri)
    } catch (_: Exception) {
        return uri.path
    }

    // 常见的 ExternalStorageProvider 格式: "primary:some/path"
    val split = docId.split(":")
    return when {
        split.size == 2 && split[0].equals("primary", ignoreCase = true) -> {
            "${android.os.Environment.getExternalStorageDirectory().absolutePath}/${split[1]}"
        }
        split.size == 2 -> {
            // 外部 SD 卡等
            "/storage/${split[0]}/${split[1]}"
        }
        else -> uri.path
    }
}
