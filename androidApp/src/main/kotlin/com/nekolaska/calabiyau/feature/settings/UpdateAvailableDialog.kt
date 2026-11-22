package com.nekolaska.calabiyau.feature.settings

import android.app.DownloadManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.tools.getFileUri
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import java.io.File
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private enum class ApkDownloadUi {
    Idle, Downloading, Paused, Ready, Failed
}

@Composable
internal fun UpdateAvailableDialog(
    info: UpdateApi.UpdateInfo,
    install: UpdateInstallFlight,
    currentVersion: String,
    onDismiss: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // An installer result (including cancel) only unlocks retry. Cleanup still checks PackageManager.
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { install.returned() }
    val dialogKey = "${info.versionCode}:${info.versionName}:${UpdateApi.resolveApkUrl(info)}"
    // Reconcile from DownloadManager on each entry; stale Bundle progress cannot authorize installation.
    var ui by remember(dialogKey) { mutableStateOf(ApkDownloadUi.Idle) }
    var progress by remember(dialogKey) { mutableFloatStateOf(0f) }
    var progressText by remember(dialogKey) { mutableStateOf("") }
    var errorText by remember(dialogKey) { mutableStateOf<String?>(null) }
    var pausedReason by remember(dialogKey) { mutableStateOf<String?>(null) }
    var downloadId by remember(dialogKey) { mutableLongStateOf(-1L) }
    var downloadFilePath by remember(dialogKey) { mutableStateOf<String?>(null) }
    var apkFilePath by remember(dialogKey) { mutableStateOf<String?>(null) }
    var reconciling by remember(dialogKey) { mutableStateOf(true) }

    fun installReadyFile() {
        val file = apkFilePath?.let(::File) ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            install.launch open@{
                val failure = withContext(Dispatchers.IO) {
                    when {
                        !file.isFile -> "安装包未找到"
                        !AppPrefs.persistPendingAppUpdateInstall(PendingAppUpdateInstall(
                            info.versionCode, info.versionName, file.absolutePath).encode()) -> "无法保存安装状态，请重试"
                        else -> null
                    }
                }
                if (failure != null) {
                    errorText = failure
                    ui = ApkDownloadUi.Failed
                    return@open false
                }
                try {
                    installer.launch(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(getFileUri(context, file), "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    })
                    true
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    withContext(Dispatchers.IO) { AppPrefs.persistPendingAppUpdateInstall(null) }
                    errorText = "无法打开该文件"
                    ui = ApkDownloadUi.Failed
                    false
                }
            }
        }
    }

    LaunchedEffect(dialogKey) {
        try {
            val existing = withContext(Dispatchers.IO) { UpdateDownloadOwner.resolve(context, info, false) }
            if (existing != null) {
                downloadFilePath = existing.file.absolutePath; downloadId = existing.id
                ui = ApkDownloadUi.Downloading
            } else if (downloadId < 0L) ui = ApkDownloadUi.Idle
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { errorText = e.message; ui = ApkDownloadUi.Failed }
        finally { reconciling = false }
    }

    fun cancelActiveDownload() {
        if (reconciling) return
        val currentId = downloadId
        reconciling = true
        scope.launch {
            try {
                if (currentId >= 0L) withContext(Dispatchers.IO) {
                    checkNotNull(context.getSystemService(DownloadManager::class.java)).remove(currentId)
                }
                downloadId = -1L; downloadFilePath = null; apkFilePath = null
                progress = 0f; progressText = ""; errorText = null; pausedReason = null
                ui = ApkDownloadUi.Idle
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { ui = ApkDownloadUi.Failed; errorText = "无法取消系统下载" }
            finally { reconciling = false }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(downloadId, downloadFilePath, lifecycle, reconciling) {
        if (reconciling) return@LaunchedEffect
        if (downloadId < 0L) return@LaunchedEffect
        val fallbackFile = downloadFilePath?.let(::File) ?: return@LaunchedEffect
        val dm = context.getSystemService(DownloadManager::class.java)
        if (dm == null) {
            ui = ApkDownloadUi.Failed
            errorText = "下载服务不可用"
            return@LaunchedEffect
        }
        val query = DownloadManager.Query().setFilterById(downloadId)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
          while (isActive) {
            val snapshot = try {
                awaitDownloadRow(read = { withContext(Dispatchers.IO) { readDownloadSnapshot(dm, query) } })
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { errorText = "无法读取系统下载记录"; ui = ApkDownloadUi.Failed; break }
            if (snapshot == null) {
                ui = ApkDownloadUi.Failed; errorText = "系统下载任务已不存在"; downloadId = -1L; downloadFilePath = null
                break
            }
            when (snapshot.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    progress = 1f
                    progressText = "下载完成"
                    val resolved = withContext(Dispatchers.IO) { resolveDownloadedFile(fallbackFile, snapshot.localUri) }
                    apkFilePath = resolved?.absolutePath
                    ui = if (resolved != null) ApkDownloadUi.Ready else {
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
                DownloadManager.STATUS_PAUSED -> {
                    // 系统暂停（等网络/重试等）：显示原因并解锁弹窗，但继续轮询——
                    // PAUSED_WAITING_TO_RETRY 等状态会自动恢复，恢复后回到 Downloading
                    pausedReason = downloadPausedMessage(snapshot.reason)
                    ui = ApkDownloadUi.Paused
                }
                else -> {
                    ui = ApkDownloadUi.Downloading
                    if (snapshot.total > 0) {
                        progress = (snapshot.soFar.toFloat() / snapshot.total.toFloat()).coerceIn(0f, 1f)
                        val percent = (progress * 100).toInt()
                        progressText = "${formatBytes(snapshot.soFar)} / ${formatBytes(snapshot.total)} ($percent%)"
                    } else {
                        progress = 0f
                        progressText = if (snapshot.soFar > 0) formatBytes(snapshot.soFar) else "正在连接…"
                    }
                }
            }
            delay(250.milliseconds)
          }
        }
    }

    Dialog(
        onDismissRequest = { if (!install.busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = ui != ApkDownloadUi.Downloading && !install.busy,
            dismissOnClickOutside = ui != ApkDownloadUi.Downloading && !install.busy),
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
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
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
                            fontWeight = FontWeight.Bold
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

                if ((ui == ApkDownloadUi.Failed || ui == ApkDownloadUi.Paused) && (errorText ?: pausedReason) != null) {
                    Text(
                        errorText ?: pausedReason.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ui == ApkDownloadUi.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (ui) {
                        ApkDownloadUi.Ready -> {
                            FilledTonalButton(
                                enabled = !install.busy,
                                onClick = ::installReadyFile,
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
                        ApkDownloadUi.Paused -> {
                            FilledTonalButton(onClick = ::cancelActiveDownload, enabled = !reconciling,
                                modifier = Modifier.fillMaxWidth(), shape = smoothCornerShape(16.dp)) { Text("取消下载") }
                        }
                        else -> {
                            FilledTonalButton(
                                enabled = !reconciling,
                                onClick = {
                                    reconciling = true
                                    scope.launch {
                                    try {
                                        errorText = null
                                        pausedReason = null
                                        apkFilePath = null
                                        progress = 0f
                                        progressText = "正在开始…"
                                        ui = ApkDownloadUi.Downloading
                                        val started = withContext(Dispatchers.IO) { checkNotNull(UpdateDownloadOwner.resolve(context, info, true)) }
                                        downloadFilePath = started.file.absolutePath
                                        downloadId = started.id
                                    } catch (e: CancellationException) { throw e }
                                    catch (e: Exception) {
                                        ui = ApkDownloadUi.Failed
                                        errorText = e.message ?: e.javaClass.simpleName
                                    } finally { reconciling = false }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = smoothCornerShape(16.dp)
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when (ui) {
                                        ApkDownloadUi.Failed -> "重试下载"
                                        else -> "下载安装包"
                                    }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onOpenBrowser,
                            modifier = Modifier.weight(1f),
                            enabled = ui != ApkDownloadUi.Downloading && !install.busy,
                            shape = smoothCornerShape(16.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("浏览器")
                        }
                        TextButton(
                            enabled = !install.busy,
                            onClick = {
                                if (install.busy) return@TextButton
                                if (ui == ApkDownloadUi.Downloading) cancelActiveDownload()
                                else onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = smoothCornerShape(16.dp)
                        ) {
                            Text(if (ui == ApkDownloadUi.Downloading) "取消下载" else "稍后")
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

/** A newly enqueued row may be briefly invisible; a permanently removed row must be terminal. */
internal suspend fun <T : Any> awaitDownloadRow(read: suspend () -> T?, backoff: suspend () -> Unit = { delay(250.milliseconds) }): T? {
    repeat(4) { attempt ->
        read()?.let { return it }
        if (attempt < 3) backoff()
    }
    return null
}

private fun readDownloadSnapshot(dm: DownloadManager, query: DownloadManager.Query): DownloadSnapshot? =
    dm.query(query)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        fun int(column: String) = cursor.getInt(cursor.getColumnIndexOrThrow(column))
        fun long(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
        val uri = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI).takeIf { it >= 0 }?.let { cursor.getString(it) }
        DownloadSnapshot(int(DownloadManager.COLUMN_STATUS), long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
            long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES), int(DownloadManager.COLUMN_REASON), uri)
    }

private fun resolveDownloadedFile(fallback: File, localUri: String?): File? {
    val fromManager = localUri
        ?.let(Uri::parse)
        ?.takeIf { it.scheme.equals("file", ignoreCase = true) }
        ?.path
        ?.let(::File)
    return fromManager?.takeIf { it.isFile } ?: fallback.takeIf { it != fromManager && it.isFile }
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

private fun downloadPausedMessage(reason: Int): String = when (reason) {
    DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待可用网络（可在系统设置中允许移动数据下载）"
    DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待 Wi-Fi 网络下载"
    DownloadManager.PAUSED_WAITING_TO_RETRY -> "下载暂停，即将自动重试"
    DownloadManager.PAUSED_UNKNOWN -> "下载已暂停"
    else -> "下载已暂停（错误码 $reason）"
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
        .toList()
}
