package com.nekolaska.calabiyau.feature.settings

import android.app.DownloadManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.tools.openFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private enum class ApkDownloadUi {
    Idle, Downloading, Ready, Failed
}

@Composable
internal fun UpdateAvailableDialog(
    info: UpdateApi.UpdateInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    val context = LocalContext.current
    var ui by remember { mutableStateOf(ApkDownloadUi.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var download by remember { mutableStateOf<UpdateApi.ApkDownload?>(null) }
    var apkFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(download?.id) {
        val current = download ?: return@LaunchedEffect
        val dm = context.getSystemService(DownloadManager::class.java)
        if (dm == null) {
            ui = ApkDownloadUi.Failed
            errorText = "下载服务不可用"
            return@LaunchedEffect
        }
        val query = DownloadManager.Query().setFilterById(current.id)
        while (isActive) {
            val snapshot = withContext(Dispatchers.IO) {
                dm.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    val localUri = runCatching {
                        cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    }.getOrNull()
                    DownloadSnapshot(status, soFar, total, reason, localUri)
                }
            }
            if (snapshot == null) {
                delay(250.milliseconds)
                continue
            }
            when (snapshot.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    progress = 1f
                    progressText = "下载完成"
                    apkFile = resolveDownloadedFile(current.file, snapshot.localUri)
                    ui = if (apkFile?.exists() == true) ApkDownloadUi.Ready else {
                        errorText = "安装包未找到"
                        ApkDownloadUi.Failed
                    }
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    ui = ApkDownloadUi.Failed
                    errorText = downloadFailureMessage(snapshot.reason)
                    break
                }
                else -> {
                    ui = ApkDownloadUi.Downloading
                    if (snapshot.total > 0) {
                        progress = (snapshot.soFar.toFloat() / snapshot.total.toFloat()).coerceIn(0f, 1f)
                        progressText = "${formatBytes(snapshot.soFar)} / ${formatBytes(snapshot.total)}"
                    } else {
                        progress = 0f
                        progressText = if (snapshot.soFar > 0) formatBytes(snapshot.soFar) else "正在连接…"
                    }
                }
            }
            delay(250.milliseconds)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = ui != ApkDownloadUi.Downloading, dismissOnClickOutside = ui != ApkDownloadUi.Downloading),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = smoothCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = smoothCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.padding(13.dp).size(26.dp)
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "发现新版本",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "当前版本 $currentVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = smoothCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            info.versionName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            maxLines = 1
                        )
                    }
                }

                UpdateContent(info.body)

                if (ui == ApkDownloadUi.Downloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (progress > 0f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(smoothCornerShape(3.dp)),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(smoothCornerShape(3.dp)),
                            )
                        }
                        Text(
                            progressText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (ui == ApkDownloadUi.Failed && errorText != null) {
                    Text(
                        errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (ui) {
                        ApkDownloadUi.Ready -> {
                            FilledTonalButton(
                                onClick = {
                                    val file = apkFile
                                    if (file != null && file.exists()) {
                                        openFile(context, file) { errorText = it; ui = ApkDownloadUi.Failed }
                                    } else {
                                        errorText = "安装包未找到"
                                        ui = ApkDownloadUi.Failed
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = smoothCornerShape(16.dp)
                            ) {
                                Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("安装")
                            }
                        }
                        ApkDownloadUi.Downloading -> {
                            FilledTonalButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                shape = smoothCornerShape(16.dp)
                            ) {
                                Text("正在下载…")
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        errorText = null
                                        progress = 0f
                                        progressText = "正在开始…"
                                        ui = ApkDownloadUi.Downloading
                                        download = UpdateApi.enqueueApkDownload(context, info)
                                    } catch (e: Exception) {
                                        ui = ApkDownloadUi.Failed
                                        errorText = e.message ?: e.javaClass.simpleName
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = smoothCornerShape(16.dp)
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (ui == ApkDownloadUi.Failed) "重试下载" else "下载安装包")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onOpenBrowser,
                            modifier = Modifier.weight(1f),
                            enabled = ui != ApkDownloadUi.Downloading,
                            shape = smoothCornerShape(16.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("浏览器")
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = ui != ApkDownloadUi.Downloading,
                            shape = smoothCornerShape(16.dp)
                        ) {
                            Text(if (ui == ApkDownloadUi.Downloading) "下载中" else "稍后")
                        }
                    }
                }
            }
        }
    }
}

private data class DownloadSnapshot(
    val status: Int,
    val soFar: Long,
    val total: Long,
    val reason: Int,
    val localUri: String?,
)

private fun resolveDownloadedFile(fallback: File, localUri: String?): File {
    val fromManager = localUri
        ?.let(Uri::parse)
        ?.takeIf { it.scheme.equals("file", ignoreCase = true) }
        ?.path
        ?.let(::File)
    return when {
        fromManager?.exists() == true -> fromManager
        fallback.exists() -> fallback
        else -> fromManager ?: fallback
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}

private fun downloadFailureMessage(reason: Int): String = when (reason) {
    DownloadManager.ERROR_CANNOT_RESUME -> "下载中断，请重试"
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "存储不可用"
    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "安装包已存在"
    DownloadManager.ERROR_FILE_ERROR -> "无法写入安装包"
    DownloadManager.ERROR_HTTP_DATA_ERROR -> "网络传输失败"
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "下载地址无效"
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "服务器拒绝下载"
    else -> "下载失败"
}

@Composable
private fun UpdateContent(body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "更新内容",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = smoothCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val lines = updateLines(body)
                if (lines.isEmpty()) {
                    Text(
                        "现在可以下载新版本。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    lines.forEach { line -> UpdateLine(line) }
                }
            }
        }
    }
}

@Composable
private fun UpdateLine(line: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(smoothCornerShape(3.dp))
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = smoothCornerShape(3.dp),
                color = MaterialTheme.colorScheme.primary
            ) {}
        }
        Text(
            line.removePrefix("•").removePrefix("-").trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun updateLines(body: String): List<String> {
    return body
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line -> line.removePrefix("•").removePrefix("-").trim() }
        .filter { it.isNotBlank() }
        .take(8)
        .toList()
}
