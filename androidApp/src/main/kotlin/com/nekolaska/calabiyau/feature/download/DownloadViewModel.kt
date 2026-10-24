package com.nekolaska.calabiyau.feature.download

import android.app.Application
import android.media.MediaScannerConnection
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nekolaska.calabiyau.NotificationHelper
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.network.NetworkMonitor
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.DownloadRecord
import data.sanitizeFileName
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File

/** Android 端下载记录持久化辅助 */
private object DownloadRecordStore {
    fun loadAll(): List<DownloadRecord> =
        DownloadRecord.decodeFromJson(AppPrefs.downloadHistoryJson)

    fun saveAll(records: List<DownloadRecord>) {
        AppPrefs.downloadHistoryJson = DownloadRecord.encodeToJson(records)
    }
}

/**
 * 下载 & 历史 & 收藏相关状态。
 * 需要 Application 上下文用于 MediaScanner。
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val MEDIA_SCAN_BATCH_SIZE = 128
    }

    // ========== 网络状态 ==========
    val isNetworkAvailable: StateFlow<Boolean> = NetworkMonitor
        .observeNetworkState(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 一次性错误事件 */
    private val _errorEvent = MutableSharedFlow<ResourceDownloadFailure>(extraBufferCapacity = 1)
    val errorEvent = _errorEvent.asSharedFlow()
    private val _noticeEvent = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val noticeEvent = _noticeEvent.asSharedFlow()
    private val _recoverableRequest = MutableStateFlow(ResourceDownloadRequest.restore(AppPrefs.pendingResourceDownload))
    val recoverableRequest = _recoverableRequest.asStateFlow()
    private var downloadJob: Job? = null
    private var activeConcurrency = 1

    // ========== 下载状态 ==========
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadStatusText = MutableStateFlow("")
    val downloadStatusText: StateFlow<String> = _downloadStatusText.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // ========== 下载历史 ==========
    private val _downloadHistory = MutableStateFlow(DownloadRecordStore.loadAll())
    val downloadHistory: StateFlow<List<DownloadRecord>> = _downloadHistory.asStateFlow()

    // ========== 收藏 ==========
    private val _favorites = MutableStateFlow(AppPrefs.favoriteCharacters)
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun toggleFavorite(name: String) {
        AppPrefs.toggleFavorite(name)
        _favorites.value = AppPrefs.favoriteCharacters
    }

    fun clearDownloadHistory() {
        _downloadHistory.value = emptyList()
        DownloadRecordStore.saveAll(emptyList())
    }

    fun addLog(msg: String) {
        _logs.update { (it + msg).takeLast(1000) }
    }

    private fun addDownloadRecord(record: DownloadRecord) {
        val list = _downloadHistory.value.toMutableList()
        list.add(0, record)
        if (list.size > 100) list.subList(100, list.size).clear()
        _downloadHistory.value = list
        DownloadRecordStore.saveAll(list)

        // 发送通知
        if (record.status == "success") {
            NotificationHelper.notifyDownloadComplete(
                context = getApplication(),
                title = record.name,
                fileCount = record.fileCount,
                savePath = record.savePath
            )
        } else if (record.status == "error") {
            NotificationHelper.notifyDownloadError(
                context = getApplication(),
                message = record.name
            )
        }
    }

    /**
     * 启动下载。由 UI 层从 SearchViewModel / PortraitViewModel 收集数据后构造 [DownloadTask] 传入。
     */
    fun startDownload(task: DownloadTask) {
        startRequest(ResourceDownloadRequest.snapshot(task, AppPrefs.savePath, AppPrefs.maxConcurrency))
    }

    fun retryDownload(request: ResourceDownloadRequest? = _recoverableRequest.value) {
        request?.let(::startRequest)
    }

    fun cancelDownload() { downloadJob?.cancel() }

    fun discardRecoverableDownload() {
        if (_isDownloading.value) return
        _isDownloading.value = true
        _downloadStatusText.value = "清除任务记录..."
        viewModelScope.launch {
            try {
                if (withContext(Dispatchers.IO) { AppPrefs.persistResourceDownload(null) }) _recoverableRequest.value = null
            } finally { _isDownloading.value = false; _downloadStatusText.value = "" }
        }
    }

    private fun startRequest(request: ResourceDownloadRequest) {
        if (_isDownloading.value) return
        _isDownloading.value = true // Claim synchronously, before launch, to reject double taps.
        _recoverableRequest.value = request
        activeConcurrency = request.maxConcurrency
        downloadJob = viewModelScope.launch {
            _downloadProgress.value = 0f
            _downloadStatusText.value = "准备下载..."

            val warnings = mutableListOf<String>()
            try {
                val cleared = runResourceDownload(
                    request,
                    persist = { value -> withContext(Dispatchers.IO) { AppPrefs.persistResourceDownload(value) } },
                    download = { active ->
                        val baseDir = File(active.savePath)
                        when (val task = active.task) {
                            is DownloadTask.Portrait -> downloadPortrait(task, baseDir)
                            is DownloadTask.FileSearch -> downloadFileSearch(task, baseDir)
                            is DownloadTask.Category -> downloadCategory(task, baseDir)
                        }
                    },
                    warn = { message ->
                        warnings += message
                        addLog(message)
                        _noticeEvent.tryEmit(message)
                    }
                )
                if (cleared) _recoverableRequest.value = null
            } catch (e: CancellationException) {
                addLog("下载已取消；已完成文件保留，可继续原任务")
                throw e
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName.orEmpty()
                val msg = downloadFailureMessage(warnings, detail)
                _errorEvent.tryEmit(ResourceDownloadFailure(msg, request))
                addLog("[错误] $msg")
                addDownloadRecord(DownloadRecord(
                    name = "下载失败",
                    fileCount = 0,
                    timestamp = System.currentTimeMillis(),
                    status = "error",
                    savePath = ""
                ))
            } finally {
                _isDownloading.value = false
                _downloadStatusText.value = ""
                downloadJob = null
            }
        }
    }

    private suspend fun downloadPortrait(task: DownloadTask.Portrait, baseDir: File) {
        val costume = task.costume
        val characterName = task.characterName
        val assets = buildList {
            costume.illustration?.let { add(it) }
            costume.frontPreview?.let { add(it) }
            costume.backPreview?.let { add(it) }
            addAll(costume.extraAssets)
        }
        if (assets.isEmpty()) {
            addLog("当前服装没有可下载的立绘资产"); return
        }
        val files = assets.map { it.title to it.url }
        val saveDir = File(
            File(File(baseDir, "立绘"), sanitizeFileName(characterName)),
            sanitizeFileName(costume.name)
        )
        addLog("开始下载 [${characterName}/${costume.name}] ${files.size} 个立绘...")
        val downloadedFiles = WikiEngine.downloadSpecificFiles(
            files = files,
            saveDir = saveDir,
            maxConcurrency = activeConcurrency,
            onLog = { addLog(it) },
            onProgress = { current, total, name ->
                _downloadProgress.value = current.toFloat() / total
                _downloadStatusText.value = "[$current/$total] $name"
            }
        )
        scanMediaFiles(downloadedFiles)
        addLog("下载完成！保存至: ${saveDir.absolutePath}")
        addDownloadRecord(DownloadRecord(
            name = "$characterName / ${costume.name}",
            fileCount = downloadedFiles.size,
            timestamp = System.currentTimeMillis(),
            status = "success",
            savePath = saveDir.absolutePath
        ))
    }

    private suspend fun downloadFileSearch(task: DownloadTask.FileSearch, baseDir: File) {
        val files = task.files
        if (files.isEmpty()) {
            addLog("未选择任何文件"); return
        }
        val saveDir = File(baseDir, "文件搜索")
        addLog("开始下载 ${files.size} 个文件...")
        val downloadedFiles = WikiEngine.downloadSpecificFiles(
            files = files,
            saveDir = saveDir,
            maxConcurrency = activeConcurrency,
            onLog = { addLog(it) },
            onProgress = { current, total, name ->
                _downloadProgress.value = current.toFloat() / total
                _downloadStatusText.value = "[$current/$total] $name"
            }
        )
        scanMediaFiles(downloadedFiles)
        addLog("下载完成！保存至: ${saveDir.absolutePath}")
        addDownloadRecord(DownloadRecord(
            name = "文件搜索 (${downloadedFiles.size}个)",
            fileCount = downloadedFiles.size,
            timestamp = System.currentTimeMillis(),
            status = "success",
            savePath = saveDir.absolutePath
        ))
    }

    private suspend fun downloadCategory(task: DownloadTask.Category, baseDir: File) {
        val group = task.group
        val cats = task.checkedCategories
        if (cats.isEmpty()) {
            addLog("请至少勾选一个分类"); return
        }
        val charDir = File(baseDir, sanitizeFileName(group.characterName))
        val resolved = resolveCategoryDownloads(cats, task.manualSelectionMap) { cat ->
            WikiEngine.fetchFilesInCategory(cat, audioOnly = task.voiceOnly)
        }
        resolved.failures.forEach { addLog("[错误] 分类获取失败: $it") }
        if (resolved.batches.isEmpty()) {
            if (resolved.failures.isEmpty()) addLog("所选分类没有可下载的文件")
            else throw java.io.IOException(incompleteDownloadMessage(null, resolved.failures))
            return
        }
        val totalFiles = resolved.batches.sumOf { it.files.size }
        var progressOffset = 0
        var totalDownloaded = 0
        var downloadError: Exception? = null
        try {
            for (batch in resolved.batches) {
                val catName = sanitizeFileName(categoryDisplayName(batch.category))
                val saveDir = File(charDir, catName)
                if (batch.category in task.manualSelectionMap) {
                    addLog("[$catName] 使用手动选择 (${batch.files.size}项)")
                }
                addLog("下载分类 [$catName]: ${batch.files.size} 个文件")
                val downloadedFiles = WikiEngine.downloadSpecificFiles(
                    files = batch.files,
                    saveDir = saveDir,
                    maxConcurrency = activeConcurrency,
                    onLog = { addLog(it) },
                    onProgress = { current, _, name ->
                        val overallCurrent = progressOffset + current
                        _downloadProgress.value = overallCurrent.toFloat() / totalFiles.coerceAtLeast(1)
                        _downloadStatusText.value = "[$catName] $overallCurrent/$totalFiles: $name"
                    }
                )
                progressOffset += batch.files.size
                totalDownloaded += downloadedFiles.size
                scanMediaFiles(downloadedFiles)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            downloadError = e
        }
        if (downloadError != null || resolved.failures.isNotEmpty()) {
            if (totalDownloaded > 0) addLog("已保存 $totalDownloaded 个文件，其余分类未完成")
            throw java.io.IOException(
                incompleteDownloadMessage(downloadError?.message, resolved.failures),
                downloadError
            )
        }
        addLog("全部下载完成！共 $totalDownloaded 个文件，保存至: ${charDir.absolutePath}")
        addDownloadRecord(DownloadRecord(
            name = group.characterName,
            fileCount = totalDownloaded,
            timestamp = System.currentTimeMillis(),
            status = "success",
            savePath = charDir.absolutePath
        ))
    }

    private suspend fun scanMediaFiles(files: List<File>) = withContext(Dispatchers.IO) {
        val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
        val app = getApplication<Application>()
        val pathsBatch = ArrayList<String>(MEDIA_SCAN_BATCH_SIZE)
        val mimeTypesBatch = ArrayList<String>(MEDIA_SCAN_BATCH_SIZE)
        var totalScanned = 0

        fun flushBatch() {
            if (pathsBatch.isEmpty()) return
            MediaScannerConnection.scanFile(
                app,
                pathsBatch.toTypedArray(),
                mimeTypesBatch.toTypedArray()
            ) { _, _ -> }
            totalScanned += pathsBatch.size
            pathsBatch.clear()
            mimeTypesBatch.clear()
        }

        files.asSequence().distinctBy { it.absolutePath }
            .filter { it.isFile && it.extension.lowercase() in imageExtensions }
            .forEach { file ->
                pathsBatch += file.absolutePath
                mimeTypesBatch += (
                    MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(file.extension.lowercase())
                        ?: "image/*"
                )
                if (pathsBatch.size >= MEDIA_SCAN_BATCH_SIZE) flushBatch()
            }

        flushBatch()
        if (totalScanned > 0) {
            addLog("已通知系统媒体库更新 $totalScanned 张图片")
        }
    }
}
