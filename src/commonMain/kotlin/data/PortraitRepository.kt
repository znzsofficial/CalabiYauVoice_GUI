package data

import portrait.*

/**
 * 立绘仓库 —— 共享业务逻辑。
 *
 * 通过函数参数注入 Wiki API 调用，避免直接依赖平台特定的 WikiEngine。
 * 各平台的 WikiEngine 或 PortraitRepository 代理负责提供具体实现。
 */
object PortraitRepositoryCore {
    @Volatile
    private var portraitFilesByCharacterCache: Map<String, List<Pair<String, String>>>? = null

    suspend fun searchCharacters(
        keyword: String,
        fetchFilesInCategory: suspend (String, Boolean) -> List<Pair<String, String>>,
        searchFilesFn: suspend (String, Boolean) -> List<Pair<String, String>>,
        getAllCharacterNames: suspend () -> List<String>
    ): List<String> {
        return searchCharacterNames(
            ensurePortraitFilesByCharacter(fetchFilesInCategory, getAllCharacterNames).keys,
            keyword
        )
    }

    suspend fun loadCharacterPortraitCatalog(
        characterName: String,
        fetchFilesInCategory: suspend (String, Boolean) -> List<Pair<String, String>>,
        searchFilesFn: suspend (String, Boolean) -> List<Pair<String, String>>,
        getAllCharacterNames: suspend () -> List<String>
    ): CharacterPortraitCatalog {
        val characterIndex = ensurePortraitFilesByCharacter(fetchFilesInCategory, getAllCharacterNames)
        val indexedFiles = findArchivedFilesForCharacter(characterIndex, characterName)
        val files = indexedFiles.ifEmpty { searchFilesFn(characterName, false) }
        return buildPortraitCatalog(characterName, files)
    }

    private suspend fun ensurePortraitFilesByCharacter(
        fetchFilesInCategory: suspend (String, Boolean) -> List<Pair<String, String>>,
        getAllCharacterNames: suspend () -> List<String>
    ): Map<String, List<Pair<String, String>>> {
        portraitFilesByCharacterCache?.let { return it }

        val illustrationFiles = PORTRAIT_SOURCE_CATEGORIES.flatMap { category ->
            fetchFilesInCategory(category, false)
        }
        val previewFiles = PREVIEW_SOURCE_CATEGORIES.flatMap { category ->
            fetchFilesInCategory(category, false)
        }
        val rawGrouped = groupRawPortraitFiles(illustrationFiles + previewFiles)

        val officialNames = getAllCharacterNames()
        val remapped = remapPortraitFilesToOfficialNames(rawGrouped, officialNames)
            .filterKeys { officialNames.contains(it) }
        portraitFilesByCharacterCache = remapped
        return remapped
    }
}
