package com.nekolaska.calabiyau

import android.app.Application
import android.media.MediaScannerConnection
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.data.NetworkMonitor
import data.DownloadRecord
import data.PortraitRepository
import data.SearchMode
import com.nekolaska.calabiyau.data.WikiEngine
import data.CharacterGroup
import data.sanitizeFileName
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import portrait.CharacterPortraitCatalog
import portrait.PortraitCostume
import java.io.File

/** Android 端下载记录持久化辅助（读写 AppPrefs） */
private object DownloadRecordStore {
    fun loadAll(): List<DownloadRecord> =
        DownloadRecord.decodeFromJson(AppPrefs.downloadHistoryJson)

    fun saveAll(records: List<DownloadRecord>) {
        AppPrefs.downloadHistoryJson = DownloadRecord.encodeToJson(records)
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ========== 网络状态 ==========
    val isNetworkAvailable: StateFlow<Boolean> = NetworkMonitor
        .observeNetworkState(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 一次性错误事件（Snackbar 消费后即消失） */
    private val _errorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    /** 最近一次搜索是否因网络问题失败 */
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _searchKeyword = MutableStateFlow("角色")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    private val _searchMode = MutableStateFlow(SearchMode.VOICE_ONLY)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _characterGroups = MutableStateFlow<List<CharacterGroup>>(emptyList())
    val characterGroups: StateFlow<List<CharacterGroup>> = _characterGroups.asStateFlow()

    // 文件搜索结果
    private val _fileSearchResults = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val fileSearchResults: StateFlow<List<Pair<String, String>>> = _fileSearchResults.asStateFlow()

    private val _fileSearchSelectedUrls = MutableStateFlow<Set<String>>(emptySet())
    val fileSearchSelectedUrls: StateFlow<Set<String>> = _fileSearchSelectedUrls.asStateFlow()

    // 选中的角色组
    private val _selectedGroup = MutableStateFlow<CharacterGroup?>(null)
    val selectedGroup: StateFlow<CharacterGroup?> = _selectedGroup.asStateFlow()

    private val _subCategories = MutableStateFlow<List<String>>(emptyList())
    val subCategories: StateFlow<List<String>> = _subCategories.asStateFlow()

    private val _checkedCategories = MutableStateFlow<List<String>>(emptyList())
    val checkedCategories: StateFlow<List<String>> = _checkedCategories.asStateFlow()

    private val _isScanningTree = MutableStateFlow(false)
    val isScanningTree: StateFlow<Boolean> = _isScanningTree.asStateFlow()

    // 下载状态
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadStatusText = MutableStateFlow("")
    val downloadStatusText: StateFlow<String> = _downloadStatusText.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private val _characterAvatars = MutableStateFlow<Map<String, String>>(emptyMap())
    val characterAvatars: StateFlow<Map<String, String>> = _characterAvatars.asStateFlow()

    // 分类文件选择对话框
    private val _showFileDialog = MutableStateFlow(false)
    val showFileDialog: StateFlow<Boolean> = _showFileDialog.asStateFlow()

    private val _dialogCategoryName = MutableStateFlow("")
    val dialogCategoryName: StateFlow<String> = _dialogCategoryName.asStateFlow()

    private val _dialogFileList = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val dialogFileList: StateFlow<List<Pair<String, String>>> = _dialogFileList.asStateFlow()

    private val _dialogIsLoading = MutableStateFlow(false)
    val dialogIsLoading: StateFlow<Boolean> = _dialogIsLoading.asStateFlow()

    private val _dialogSelectedUrls = MutableStateFlow<Set<String>>(emptySet())
    val dialogSelectedUrls: StateFlow<Set<String>> = _dialogSelectedUrls.asStateFlow()

    // 手动选择的文件映射 (分类名 -> 选中的文件列表)
    private val _manualSelectionMap = MutableStateFlow<Map<String, List<Pair<String, String>>>>(emptyMap())
    val manualSelectionMap: StateFlow<Map<String, List<Pair<String, String>>>> = _manualSelectionMap.asStateFlow()

    // 立绘相关状态
    private val _portraitCharacters = MutableStateFlow<List<String>>(emptyList())
    val portraitCharacters: StateFlow<List<String>> = _portraitCharacters.asStateFlow()

    private val _selectedPortraitCharacter = MutableStateFlow<String?>(null)
    val selectedPortraitCharacter: StateFlow<String?> = _selectedPortraitCharacter.asStateFlow()

    private val _portraitCatalog = MutableStateFlow<CharacterPortraitCatalog?>(null)
    val portraitCatalog: StateFlow<CharacterPortraitCatalog?> = _portraitCatalog.asStateFlow()

    private val _isLoadingPortrait = MutableStateFlow(false)
    val isLoadingPortrait: StateFlow<Boolean> = _isLoadingPortrait.asStateFlow()

    private val _selectedPortraitCostume = MutableStateFlow<PortraitCostume?>(null)
    val selectedPortraitCostume: StateFlow<PortraitCostume?> = _selectedPortraitCostume.asStateFlow()

    // 每个模式缓存的搜索关键词，用于切换 tab 时恢复
    private val cachedKeywords = mutableMapOf<SearchMode, String>(
        SearchMode.VOICE_ONLY to "角色",
        SearchMode.ALL_CATEGORIES to "角色",
        SearchMode.PORTRAIT to "角色",
        SearchMode.FILE_SEARCH to ""
    )
    // 记录哪些模式已经有搜索结果（无需再次自动搜索）
    private val hasResultsCache = mutableSetOf<SearchMode>()

    // 语音 / 分类 各自独立缓存搜索结果，切换 tab 时保存 & 恢复
    private val cachedCharacterGroups = mutableMapOf<SearchMode, List<CharacterGroup>>()
    private val cachedCharacterAvatars = mutableMapOf<SearchMode, Map<String, String>>()
    private val cachedSelectedGroup = mutableMapOf<SearchMode, CharacterGroup?>()
    private val cachedSubCategories = mutableMapOf<SearchMode, List<String>>()
    private val cachedCheckedCategories = mutableMapOf<SearchMode, List<String>>()

    init {
        // 启动时自动以默认关键词"角色"搜索语音页
        performSearch()
    }

    fun onSearchKeywordChange(value: String) { _searchKeyword.value = value }
    fun onSearchModeChange(mode: SearchMode) {
        val prev = _searchMode.value
        // 先保存当前模式的关键词
        cachedKeywords[prev] = _searchKeyword.value

        // 保存语音/分类模式的搜索结果缓存
        if (prev == SearchMode.VOICE_ONLY || prev == SearchMode.ALL_CATEGORIES) {
            cachedCharacterGroups[prev] = _characterGroups.value
            cachedCharacterAvatars[prev] = _characterAvatars.value
            cachedSelectedGroup[prev] = _selectedGroup.value
            cachedSubCategories[prev] = _subCategories.value
            cachedCheckedCategories[prev] = _checkedCategories.value
        }

        _searchMode.value = mode

        if (mode == SearchMode.FILE_SEARCH) {
            // 文件搜索页：恢复上次关键词，若无缓存结果则清空
            _searchKeyword.value = cachedKeywords[mode] ?: ""
            if (mode !in hasResultsCache) {
                _fileSearchResults.value = emptyList()
                _fileSearchSelectedUrls.value = emptySet()
                _hasSearched.value = false
            }
        } else if (mode == SearchMode.VOICE_ONLY || mode == SearchMode.ALL_CATEGORIES) {
            // 语音/分类页：恢复各自独立的缓存
            _searchKeyword.value = cachedKeywords[mode] ?: "角色"
            if (mode in hasResultsCache) {
                _characterGroups.value = cachedCharacterGroups[mode] ?: emptyList()
                _characterAvatars.value = cachedCharacterAvatars[mode] ?: emptyMap()
                _selectedGroup.value = cachedSelectedGroup[mode]
                _subCategories.value = cachedSubCategories[mode] ?: emptyList()
                _checkedCategories.value = cachedCheckedCategories[mode] ?: emptyList()
                _hasSearched.value = true
                return
            }
            performSearch()
        } else {
            // 立绘页
            _searchKeyword.value = cachedKeywords[mode] ?: "角色"
            if (mode in hasResultsCache) {
                _hasSearched.value = true
                return
            }
            performSearch()
        }
    }
    fun toggleFileSearchSelection(url: String) {
        val current = _fileSearchSelectedUrls.value.toMutableSet()
        if (url in current) current.remove(url) else current.add(url)
        _fileSearchSelectedUrls.value = current
    }

    fun selectAllFileSearchResults() {
        _fileSearchSelectedUrls.value = _fileSearchResults.value.map { it.second }.toSet()
    }

    // 收藏状态
    private val _favorites = MutableStateFlow(AppPrefs.favoriteCharacters)
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun toggleFavorite(name: String) {
        AppPrefs.toggleFavorite(name)
        _favorites.value = AppPrefs.favoriteCharacters
    }

    // 下载历史
    private val _downloadHistory = MutableStateFlow(DownloadRecordStore.loadAll())
    val downloadHistory: StateFlow<List<DownloadRecord>> = _downloadHistory.asStateFlow()

    private fun addDownloadRecord(record: DownloadRecord) {
        val list = _downloadHistory.value.toMutableList()
        list.add(0, record) // 最新的在前
        if (list.size > 100) list.subList(100, list.size).clear()
        _downloadHistory.value = list
        DownloadRecordStore.saveAll(list)
    }

    fun clearDownloadHistory() {
        _downloadHistory.value = emptyList()
        DownloadRecordStore.saveAll(emptyList())
    }

    fun performSearch() {
        if (_isSearching.value) return
        val keyword = _searchKeyword.value.trim()
        if (keyword.isBlank()) return

        // 记录搜索历史
        AppPrefs.addSearchHistory(keyword)

        // 手动搜索时，清除当前模式的旧缓存标记
        hasResultsCache.remove(_searchMode.value)

        viewModelScope.launch {
            _isSearching.value = true
            _hasSearched.value = false

            // 只清除当前模式相关的状态，不影响其他模式的缓存
            when (_searchMode.value) {
                SearchMode.FILE_SEARCH -> {
                    _fileSearchResults.value = emptyList()
                    _fileSearchSelectedUrls.value = emptySet()
                }
                else -> {
                    _selectedGroup.value = null
                    _subCategories.value = emptyList()
                    _checkedCategories.value = emptyList()
                }
            }

            // 预先检查网络
            if (!isNetworkAvailable.value) {
                _searchError.value = "无网络连接，请检查网络后重试"
                _errorEvent.tryEmit("无网络连接，请检查网络后重试")
                addLog("[错误] 搜索失败: 无网络连接")
                _isSearching.value = false
                _hasSearched.value = true
                return@launch
            }
            _searchError.value = null

            try {
                when (_searchMode.value) {
                    SearchMode.VOICE_ONLY -> {
                        val groups = WikiEngine.searchAndGroupCharacters(keyword, voiceOnly = true)
                        _characterGroups.value = groups
                        _characterAvatars.value = WikiEngine.fetchCharacterAvatars(groups.map { it.characterName })
                    }
                    SearchMode.ALL_CATEGORIES -> {
                        val groups = WikiEngine.searchAndGroupCharacters(keyword, voiceOnly = false)
                        _characterGroups.value = groups
                        _characterAvatars.value = WikiEngine.fetchCharacterAvatars(groups.map { it.characterName })
                    }
                    SearchMode.PORTRAIT -> {
                        addLog("搜索立绘角色: $keyword")
                        val characters = PortraitRepository.searchCharacters(keyword)
                        _portraitCharacters.value = characters
                        _characterAvatars.value = WikiEngine.fetchCharacterAvatars(characters)
                        addLog("找到 ${characters.size} 个角色")
                    }
                    SearchMode.FILE_SEARCH -> {
                        addLog("开始搜索文件: $keyword")
                        val results = WikiEngine.searchFiles(
                            keyword = keyword,
                            audioOnly = false,
                            onLog = { addLog(it) }
                        )
                        _fileSearchResults.value = results
                        addLog("搜索完成，共找到 ${results.size} 个文件")
                    }
                }
                _searchError.value = null
            } catch (e: Exception) {
                val msg = if (!isNetworkAvailable.value) "网络连接失败" else "搜索失败: ${e.message}"
                _searchError.value = msg
                _errorEvent.tryEmit(msg)
                addLog("[错误] 搜索失败: ${e.message}")
            } finally {
                _isSearching.value = false
                _hasSearched.value = true
                // 标记当前模式已有结果缓存
                hasResultsCache.add(_searchMode.value)
                cachedKeywords[_searchMode.value] = keyword
            }
        }
    }

    fun onSelectGroup(group: CharacterGroup) {
        _selectedGroup.value = group
        _isScanningTree.value = true
        viewModelScope.launch {
            try {
                val cats = WikiEngine.scanCategoryTree(group.rootCategory)
                _subCategories.value = cats
                _checkedCategories.value = cats.toList()
            } catch (e: Exception) {
                val msg = if (!isNetworkAvailable.value) "网络连接失败，无法加载分类" else "扫描分类树失败: ${e.message}"
                _errorEvent.tryEmit(msg)
                addLog("[错误] 扫描分类树失败: ${e.message}")
            } finally {
                _isScanningTree.value = false
            }
        }
    }

    fun clearSelectedGroup() {
        _selectedGroup.value = null
    }

    fun setCategoryChecked(cat: String, checked: Boolean) {
        val current = _checkedCategories.value.toMutableList()
        if (checked) { if (cat !in current) current.add(cat) } else current.remove(cat)
        _checkedCategories.value = current
    }

    fun checkAllCategories() { _checkedCategories.value = _subCategories.value.toList() }
    fun uncheckAllCategories() { _checkedCategories.value = emptyList() }

    // 分类文件选择对话框
    fun openFileDialog(cat: String) {
        _dialogCategoryName.value = cat
        _dialogFileList.value = emptyList()
        _dialogSelectedUrls.value = emptySet()
        _showFileDialog.value = true
        _dialogIsLoading.value = true
        viewModelScope.launch {
            try {
                val files = WikiEngine.fetchFilesInCategory(cat, audioOnly = _searchMode.value == SearchMode.VOICE_ONLY)
                _dialogFileList.value = files
                val manual = _manualSelectionMap.value[cat]
                _dialogSelectedUrls.value = manual?.map { it.second }?.toSet()
                    ?: files.map { it.second }.toSet()
            } catch (e: Exception) {
                addLog("加载分类文件失败: ${e.message}")
            } finally {
                _dialogIsLoading.value = false
            }
        }
    }

    fun closeFileDialog() { _showFileDialog.value = false }

    fun toggleDialogFileSelection(url: String) {
        val current = _dialogSelectedUrls.value.toMutableSet()
        if (url in current) current.remove(url) else current.add(url)
        _dialogSelectedUrls.value = current
    }

    fun selectAllDialogFiles() {
        _dialogSelectedUrls.value = _dialogFileList.value.map { it.second }.toSet()
    }

    fun clearDialogSelection() {
        _dialogSelectedUrls.value = emptySet()
    }

    fun confirmFileDialog() {
        val cat = _dialogCategoryName.value
        val selectedFiles = _dialogFileList.value.filter { it.second in _dialogSelectedUrls.value }
        _showFileDialog.value = false

        val newMap = _manualSelectionMap.value.toMutableMap()
        newMap[cat] = selectedFiles
        _manualSelectionMap.value = newMap

        // 自动勾选该分类
        val checked = _checkedCategories.value.toMutableList()
        if (selectedFiles.isNotEmpty() && cat !in checked) {
            checked.add(cat)
            _checkedCategories.value = checked
        }
    }

    fun onSelectPortraitCharacter(characterName: String) {
        _selectedPortraitCharacter.value = characterName
        _portraitCatalog.value = null
        _selectedPortraitCostume.value = null
        _isLoadingPortrait.value = true
        viewModelScope.launch {
            try {
                val catalog = PortraitRepository.loadCharacterPortraitCatalog(characterName)
                _portraitCatalog.value = catalog
            } catch (e: Exception) {
                _portraitCatalog.value = CharacterPortraitCatalog(characterName, emptyList())
            } finally {
                _isLoadingPortrait.value = false
            }
        }
    }

    fun clearSelectedPortraitCharacter() {
        _selectedPortraitCharacter.value = null
        _portraitCatalog.value = null
        _selectedPortraitCostume.value = null
    }

    fun selectPortraitCostume(costume: PortraitCostume) {
        _selectedPortraitCostume.value = costume
    }

    fun startDownload() {
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

                when (_searchMode.value) {
                    SearchMode.PORTRAIT -> {
                        val costume = _selectedPortraitCostume.value ?: run {
                            addLog("请先选择一个角色和服装"); return@launch
                        }
                        val characterName = _selectedPortraitCharacter.value ?: run {
                            addLog("请先选择一个角色"); return@launch
                        }
                        val assets = buildList {
                            costume.illustration?.let { add(it) }
                            costume.frontPreview?.let { add(it) }
                            costume.backPreview?.let { add(it) }
                            addAll(costume.extraAssets)
                        }
                        if (assets.isEmpty()) {
                            addLog("当前服装没有可下载的立绘资产"); return@launch
                        }
                        val files = assets.map { it.title to it.url }
                        val saveDir = File(
                            File(
                                File(baseDir, "立绘"),
                                sanitizeFileName(characterName)
                            ),
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
                    SearchMode.FILE_SEARCH -> {
                        val selected = _fileSearchSelectedUrls.value
                        val files = _fileSearchResults.value.filter { it.second in selected }
                        if (files.isEmpty()) {
                            addLog("未选择任何文件")
                            return@launch
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
                    else -> {
                        val group = _selectedGroup.value ?: run {
                            addLog("请先选择一个角色"); return@launch
                        }
                        val cats = _checkedCategories.value
                        if (cats.isEmpty()) {
                            addLog("请至少勾选一个分类"); return@launch
                        }
                        val charDir = File(baseDir, sanitizeFileName(group.characterName))
                        var totalDownloaded = 0
                        for (cat in cats) {
                            val manual = _manualSelectionMap.value[cat]
                            val files = if (manual != null) {
                                addLog("[${cat.removePrefix("Category:").removePrefix("分类:")}] 使用手动选择 (${manual.size}项)")
                                manual
                            } else {
                                WikiEngine.fetchFilesInCategory(cat, audioOnly = _searchMode.value == SearchMode.VOICE_ONLY)
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

    private fun addLog(msg: String) {
        _logs.value = _logs.value + msg
    }

    /**
     * 扫描目录下的图片文件，通知系统媒体库更新（使其在相册中可见）。
     */
    private fun scanMediaFiles(dir: File) {
        if (!dir.exists()) return
        val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
        val imageFiles = dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in imageExtensions }
            .toList()
        if (imageFiles.isEmpty()) return

        val paths = imageFiles.map { it.absolutePath }.toTypedArray()
        val mimeTypes = imageFiles.map { file ->
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase())
                ?: "image/*"
        }.toTypedArray()

        MediaScannerConnection.scanFile(
            getApplication(),
            paths,
            mimeTypes
        ) { _, _ -> }
        addLog("已通知系统媒体库更新 ${imageFiles.size} 张图片")
    }
}
