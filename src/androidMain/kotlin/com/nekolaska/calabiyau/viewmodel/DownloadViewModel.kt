package com.nekolaska.calabiyau.viewmodel

import android.app.Application
import android.media.MediaScannerConnection
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nekolaska.calabiyau.NotificationHelper
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.data.NetworkMonitor
import com.nekolaska.calabiyau.data.WikiEngine
import data.CharacterGroup
import data.DownloadRecord
import data.sanitizeFileName
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import portrait.PortraitCostume
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
 * 下载任务描述 —— 从 UI 层收集好所有需要的数据后传入，
 * 避免 DownloadViewModel 直接依赖 SearchViewModel / PortraitViewModel。
 */
sealed class DownloadTask {
    /** 立绘下载 */
    data class Portrait(
        val characterName: String,
        val costume: PortraitCostume
    ) : DownloadTask()

    /** 文件搜索结果下载 */
    data class FileSearch(
        val files: List<Pair<String, String>>   // name to url
    ) : DownloadTask()

    /** 分类下载（语音 / 全分类） */
    data class Category(
        val group: CharacterGroup,
        val checkedCategories: List<String>,
        val manualSelectionMap: Map<String, List<Pair<String, String>>>,
        val voiceOnly: Boolean
    ) : DownloadTask()
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
    private val _errorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

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
        _logs.value = _logs.value + msg
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
        if (_isDownloading.value) return
        if (!isNetworkAvailable.value) {
            _errorEvent.tryEmit("无网络连接，无法开始下载")
            addLog("[错误] 下载失败: 无网络连接")
            return
        }
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0f
            _downloadStatusText.value = "准备下载..."

            try {
                val baseDir = File(AppPrefs.savePath)

                when (task) {
                    is DownloadTask.Portrait -> downloadPortrait(task, baseDir)
                    is DownloadTask.FileSearch -> downloadFileSearch(task, baseDir)
                    is DownloadTask.Category -> downloadCategory(task, baseDir)
                }
            } catch (e: Exception) {
                val msg = if (!isNetworkAvailable.value) "网络连接失败，下载中断" else "下载失败: ${e.message}"
                _errorEvent.tryEmit(msg)
                addLog("[错误] 下载失败: ${e.message}")
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
        WikiEngine.downloadSpecificFiles(
            files = files,
            saveDir = saveDir,
            maxConcurrency = AppPrefs.maxConcurrency,
            onLog = { addLog(it) },
            onProgress = { current, total, name ->
                _downloadProgress.value = current.toFloat() / total
                _downloadStatusText.value = "[$current/$total] $name"
            }
        )
        scanMediaFiles(saveDir)
        addLog("下载完成！保存至: ${saveDir.absolutePath}")
        addDownloadRecord(DownloadRecord(
            name = "$characterName / ${costume.name}",
            fileCount = assets.size,
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
        WikiEngine.downloadSpecificFiles(
            files = files,
            saveDir = saveDir,
            maxConcurrency = AppPrefs.maxConcurrency,
            onLog = { addLog(it) },
            onProgress = { current, total, name ->
                _downloadProgress.value = current.toFloat() / total
                _downloadStatusText.value = "[$current/$total] $name"
            }
        )
        scanMediaFiles(saveDir)
        addLog("下载完成！保存至: ${saveDir.absolutePath}")
        addDownloadRecord(DownloadRecord(
            name = "文件搜索 (${files.size}个)",
            fileCount = files.size,
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
        var totalDownloaded = 0
        for (cat in cats) {
            val manual = task.manualSelectionMap[cat]
            val files = if (manual != null) {
                addLog("[${cat.removePrefix("Category:").removePrefix("分类:")}] 使用手动选择 (${manual.size}项)")
                manual
            } else {
                WikiEngine.fetchFilesInCategory(cat, audioOnly = task.voiceOnly)
            }
            if (files.isEmpty()) continue
            val catName = sanitizeFileName(cat.removePrefix("Category:").removePrefix("分类:"))
            val saveDir = File(charDir, catName)
            addLog("下载分类 [$catName]: ${files.size} 个文件")
            WikiEngine.downloadSpecificFiles(
                files = files,
                saveDir = saveDir,
                maxConcurrency = AppPrefs.maxConcurrency,
                onLog = { addLog(it) },
                onProgress = { current, total, name ->
                    _downloadProgress.value = current.toFloat() / total
                    _downloadStatusText.value = "[$catName] $current/$total: $name"
                }
            )
            totalDownloaded += files.size
        }
        scanMediaFiles(charDir)
        addLog("全部下载完成！共 $totalDownloaded 个文件，保存至: ${charDir.absolutePath}")
        addDownloadRecord(DownloadRecord(
            name = group.characterName,
            fileCount = totalDownloaded,
            timestamp = System.currentTimeMillis(),
            status = "success",
            savePath = charDir.absolutePath
        ))
    }

    private fun scanMediaFiles(dir: File) {
        if (!dir.exists()) return
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

        dir.walkTopDown()
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
