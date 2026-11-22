package com.nekolaska.calabiyau.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.feature.tools.getWritablePathFromTreeUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class DownloadOption { PATH, CONCURRENCY, BAR_STYLE }

@Composable
internal fun DownloadSettingsSection(
    concurrency: Int,
    onConcurrencyChange: (Int) -> Unit,
    onOpenStorage: () -> Unit
) {
    var savePath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { savePath = withContext(Dispatchers.IO) { AppPrefs.savePath } }
    var barStyle by remember { mutableIntStateOf(AppPrefs.bottomBarStyle) }
    var option by rememberSaveable { mutableStateOf<DownloadOption?>(null) }
    var pickerError by rememberSaveable { mutableStateOf<String?>(null) }
    val showSnack = rememberSnackbarLauncher()
    val barStyles = listOf(
        AppPrefs.BAR_STYLE_DOCKED_TOOLBAR to "悬浮工具栏",
        AppPrefs.BAR_STYLE_BOTTOM_APP_BAR to "经典导航栏"
    )

    SettingsGroupHeader("下载设置", topPadding = AppSpacing.small)
    SettingsCard {
        SettingsItem(
            icon = Icons.Outlined.Folder,
            title = "保存位置",
            subtitle = savePath?.let { downloadDirectorySummary(it) } ?: "正在读取…",
            compactSubtitle = savePath?.let { downloadDirectorySummary(it, includeParent = false) }
        ) {
            pickerError = null
            option = DownloadOption.PATH
        }
        SettingsDivider()
        SettingsItem(
            icon = Icons.Outlined.Speed,
            title = "同时下载",
            value = "$concurrency 项",
            tabularValue = true
        ) { option = DownloadOption.CONCURRENCY }
        SettingsDivider()
        SettingsItem(
            icon = Icons.Outlined.ViewDay,
            title = "下载页底栏",
            value = barStyles.find { it.first == barStyle }?.second ?: "经典导航栏"
        ) { option = DownloadOption.BAR_STYLE }
    }

    SettingsGroupHeader("空间管理")
    SettingsCard {
        SettingsItem(
            icon = Icons.Outlined.Storage,
            title = SettingsPage.STORAGE.title,
            subtitle = "下载文件与缓存用量",
            onClick = onOpenStorage
        )
    }

    when (option) {
        DownloadOption.PATH -> {
            val scope = rememberCoroutineScope()
            var checking by remember { mutableStateOf(false) }
            var draft by rememberSaveable { mutableStateOf<String?>(null) }
            val pathReady = savePath != null
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    checking = true
                    scope.launch {
                        try {
                            val path = withContext(Dispatchers.IO) { getWritablePathFromTreeUri(uri) }
                            if (option == DownloadOption.PATH) {
                                if (path != null) draft = path else pickerError = "此目录无法直接写入，需要本机可写目录"
                            }
                        } finally { checking = false }
                    }
                }
            }
            SettingsDialog(
                title = { Text("保存位置") },
                onDismissRequest = { option = null },
                text = {
                    Column {
                        OutlinedTextField(
                            value = draft ?: savePath.orEmpty(),
                            readOnly = checking || !pathReady,
                            onValueChange = { draft = it; pickerError = null },
                            placeholder = { if (!pathReady) Text("正在读取…") },
                            label = { Text("目录路径") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = SettingsIconFrameShape
                        )
                        Spacer(Modifier.height(AppSpacing.large))
                        OutlinedButton(
                            enabled = pathReady && !checking,
                            onClick = { pickerError = null; picker.launch(null) }
                        ) { Text("选择目录…") }
                        pickerError?.let {
                            Spacer(Modifier.height(AppSpacing.xSmall))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        enabled = pathReady && !checking,
                        onClick = {
                            if (checking || option != DownloadOption.PATH) return@FilledTonalButton
                            val path = draft ?: savePath ?: return@FilledTonalButton
                            checking = true
                            scope.launch {
                                try {
                                    val writable = withContext(Dispatchers.IO) { isDownloadDirectoryPath(path) }
                                    if (option != DownloadOption.PATH) return@launch
                                    if (writable) {
                                        savePath = path
                                        AppPrefs.savePath = path
                                        option = null
                                        showSnack("已更新保存目录")
                                    } else pickerError = "请输入本机可写目录的完整路径"
                                } finally { checking = false }
                            }
                        }
                    ) { Text(if (checking) "验证中…" else "保存") }
                },
                dismissButton = { TextButton(onClick = { option = null }) { Text("取消") } }
            )
        }

        DownloadOption.CONCURRENCY -> {
            var draft by rememberSaveable { mutableStateOf(concurrency.toString()) }
            SettingsDialog(
                title = { Text("同时下载") },
                onDismissRequest = { option = null },
                text = {
                    Column {
                        Text("可同时进行的下载数量，范围为 1 到 32。", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(AppSpacing.large))
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.filter(Char::isDigit) },
                            label = { Text("并发数") },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = SettingsIconFrameShape
                        )
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        onClick = {
                            val value = draft.toIntOrNull() ?: return@FilledTonalButton
                            onConcurrencyChange(value.coerceIn(1, 32))
                            option = null
                        },
                        enabled = draft.toIntOrNull() != null
                    ) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { option = null }) { Text("取消") } }
            )
        }

        DownloadOption.BAR_STYLE -> SettingsSingleChoiceDialog(
            title = "下载页底栏",
            options = barStyles,
            selectedValue = barStyle,
            onSelect = { barStyle = it; AppPrefs.bottomBarStyle = it; option = null },
            onDismissRequest = { option = null }
        )

        null -> Unit
    }
}

/** Keep a valid custom spelling; a not-yet-created download directory is supported. */
internal fun isDownloadDirectoryPath(path: String): Boolean = runCatching {
    val file = File(path)
    file.isAbsolute && generateSequence(file.canonicalFile) { it.parentFile }.firstOrNull { it.exists() }
        ?.let { it.isDirectory && it.canWrite() } == true
}.getOrDefault(false)
