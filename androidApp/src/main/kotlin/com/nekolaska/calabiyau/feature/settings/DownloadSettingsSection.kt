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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.feature.tools.getWritablePathFromTreeUri
import java.io.File

private enum class DownloadOption { PATH, CONCURRENCY, BAR_STYLE }

@Composable
internal fun DownloadSettingsSection(concurrency: Int, onConcurrencyChange: (Int) -> Unit, onOpenStorage: () -> Unit) {
    var savePath by remember { mutableStateOf(AppPrefs.savePath) }
    var barStyle by remember { mutableIntStateOf(AppPrefs.bottomBarStyle) }
    var option by rememberSaveable { mutableStateOf<DownloadOption?>(null) }
    var pickerError by rememberSaveable { mutableStateOf<String?>(null) }
    val showSnack = rememberSnackbarLauncher()
    val barStyles = listOf(
        AppPrefs.BAR_STYLE_DOCKED_TOOLBAR to "悬浮工具栏",
        AppPrefs.BAR_STYLE_BOTTOM_APP_BAR to "经典导航栏"
    )
    SettingsCard {
        SettingsItem(Icons.Outlined.Folder, "保存位置", savePath) { pickerError = null; option = DownloadOption.PATH }
        SettingsDivider()
        SettingsItem(Icons.Outlined.Speed, "同时下载", "$concurrency 项") { option = DownloadOption.CONCURRENCY }
        SettingsDivider()
        SettingsItem(Icons.Outlined.ViewDay, "下载页底栏", barStyles.find { it.first == barStyle }?.second ?: "经典导航栏") {
            option = DownloadOption.BAR_STYLE
        }
        SettingsDivider()
        SettingsItem(Icons.Outlined.Storage, SettingsPage.STORAGE.title, "下载文件与缓存用量", onOpenStorage)
    }
    when (option) {
        DownloadOption.PATH -> {
            var draft by rememberSaveable { mutableStateOf(savePath) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    val path = getWritablePathFromTreeUri(uri)
                    if (path != null) draft = path else pickerError = "此目录无法直接写入，需要本机可写目录"
                }
            }
            SettingsDialog(
                title = { Text("保存位置") },
                onDismissRequest = { option = null },
                text = {
                    Column {
                        OutlinedTextField(
                            value = draft, onValueChange = { draft = it; pickerError = null },
                            label = { Text("目录路径") }, modifier = Modifier.fillMaxWidth(),
                            maxLines = 3, shape = AppShapes.compactCard
                        )
                        Spacer(Modifier.height(AppSpacing.large))
                        OutlinedButton(onClick = { pickerError = null; picker.launch(null) }) { Text("选择目录…") }
                        pickerError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = { FilledTonalButton(onClick = {
                    if (isDownloadDirectoryPath(draft)) {
                        savePath = draft
                        AppPrefs.savePath = draft
                        option = null
                        showSnack("已更新保存目录")
                    } else pickerError = "请输入本机可写目录的完整路径"
                }) { Text("保存") } },
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
                            value = draft, onValueChange = { draft = it.filter(Char::isDigit) },
                            label = { Text("并发数") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = AppShapes.compactCard
                        )
                    }
                },
                confirmButton = {
                    FilledTonalButton(onClick = {
                        val value = draft.toIntOrNull() ?: return@FilledTonalButton
                        onConcurrencyChange(value.coerceIn(1, 32))
                        option = null
                    }, enabled = draft.toIntOrNull() != null) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { option = null }) { Text("取消") } }
            )
        }
        DownloadOption.BAR_STYLE -> SettingsSingleChoiceDialog(
            title = "下载页底栏", options = barStyles, selectedValue = barStyle,
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
