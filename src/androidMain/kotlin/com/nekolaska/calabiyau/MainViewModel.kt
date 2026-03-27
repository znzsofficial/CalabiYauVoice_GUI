package com.nekolaska.calabiyau

import android.app.Application
import android.media.MediaScannerConnection
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.data.PortraitRepository
import com.nekolaska.calabiyau.data.WikiEngine
import portrait.CharacterPortraitCatalog
import portrait.PortraitCostume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class SearchMode { VOICE_ONLY, ALL_CATEGORIES, FILE_SEARCH, PORTRAIT }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _searchKeyword = MutableStateFlow("角色")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    private val _searchMode = MutableStateFlow(SearchMode.VOICE_ONLY)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _characterGroups = MutableStateFlow<List<WikiEngine.CharacterGroup>>(emptyList())
    val characterGroups: StateFlow<List<WikiEngine.CharacterGroup>> = _characterGroups.asStateFlow()

    // 文件搜索结果
    private val _fileSearchResults = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val fileSearchResults: StateFlow<List<Pair<String, String>>> = _fileSearchResults.asStateFlow()

    private val _fileSearchSelectedUrls = MutableStateFlow<Set<String>>(emptySet())
    val fileSearchSelectedUrls: StateFlow<Set<String>> = _fileSearchSelectedUrls.asStateFlow()

    // 选中的角色组
    private val _selectedGroup = MutableStateFlow<WikiEngine.CharacterGroup?>(null)
    val selectedGroup: StateFlow<WikiEngine.CharacterGroup?> = _selectedGroup.asStateFlow()

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

    private val _maxConcurrencyStr = MutableStateFlow("8")
    val maxConcurrencyStr: StateFlow<String> = _maxConcurrencyStr.asStateFlow()

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

    fun onSearchKeywordChange(value: String) { _searchKeyword.value = value }
    fun onSearchModeChange(mode: SearchMode) {
        _searchMode.value = mode
        if (mode == SearchMode.FILE_SEARCH) {
            // 文件搜索页：清空搜索栏，不主动搜索
            _searchKeyword.value = ""
            _fileSearchResults.value = emptyList()
            _fileSearchSelectedUrls.value = emptySet()
            _hasSearched.value = false
        } else {
            // 语音/分类/立绘页：设默认关键词"角色"并自动搜索
            if (mode == SearchMode.PORTRAIT) {
                _portraitCharacters.value = emptyList()
                _selectedPortraitCharacter.value = null
                _portraitCatalog.value = null
                _selectedPortraitCostume.value = null
            }
            _searchKeyword.value = "角色"
            performSearch()
        }
    }
    fun onMaxConcurrencyChange(value: String) { _maxConcurrencyStr.value = value.filter { it.isDigit() } }

    fun toggleFileSearchSelection(url: String) {
        val current = _fileSearchSelectedUrls.value.toMutableSet()
        if (url in current) current.remove(url) else current.add(url)
        _fileSearchSelectedUrls.value = current
    }

    fun selectAllFileSearchResults() {
        _fileSearchSelectedUrls.value = _fileSearchResults.value.map { it.second }.toSet()
    }

    fun clearFileSearchSelection() {
        _fileSearchSelectedUrls.value = emptySet()
    }

    fun performSearch() {
        if (_isSearching.value) return
        val keyword = _searchKeyword.value.trim()
        if (keyword.isBlank()) return

        viewModelScope.launch {
            _isSearching.value = true
            _hasSearched.value = false
            _selectedGroup.value = null
            _subCategories.value = emptyList()
            _checkedCategories.value = emptyList()
            _fileSearchResults.value = emptyList()
            _fileSearchSelectedUrls.value = emptySet()

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
            } catch (e: Exception) {
                addLog("[错误] 搜索失败: ${e.message}")
            } finally {
                _isSearching.value = false
                _hasSearched.value = true
            }
        }
    }

    fun onSelectGroup(group: WikiEngine.CharacterGroup) {
        _selectedGroup.value = group
        _isScanningTree.value = true
        viewModelScope.launch {
            try {
                val cats = WikiEngine.scanCategoryTree(group.rootCategory)
                _subCategories.value = cats
                _checkedCategories.value = cats.toList()
            } catch (e: Exception) {
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
                                WikiEngine.sanitizeFileName(characterName)
                            ),
                            WikiEngine.sanitizeFileName(costume.name)
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
                    }
                    else -> {
                        val group = _selectedGroup.value ?: run {
                            addLog("请先选择一个角色"); return@launch
                        }
                        val cats = _checkedCategories.value
                        if (cats.isEmpty()) {
                            addLog("请至少勾选一个分类"); return@launch
                        }
                        val charDir = File(baseDir, WikiEngine.sanitizeFileName(group.characterName))
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
                            val catName = WikiEngine.sanitizeFileName(cat.removePrefix("Category:").removePrefix("分类:"))
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
                    }
                }
            } catch (e: Exception) {
                addLog("[错误] 下载失败: ${e.message}")
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
