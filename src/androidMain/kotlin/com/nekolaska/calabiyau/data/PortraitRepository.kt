package com.nekolaska.calabiyau.data

import portrait.*

object PortraitRepository {
    @Volatile
    private var portraitFilesByCharacterCache: Map<String, List<Pair<String, String>>>? = null

    suspend fun searchCharacters(keyword: String): List<String> {
        return searchCharacterNames(ensurePortraitFilesByCharacter().keys, keyword)
    }

    suspend fun loadCharacterPortraitCatalog(characterName: String): CharacterPortraitCatalog {
        val characterIndex = ensurePortraitFilesByCharacter()
        val indexedFiles = findArchivedFilesForCharacter(characterIndex, characterName)
        val files = indexedFiles.ifEmpty { WikiEngine.searchFiles(characterName, audioOnly = false) }
        return buildPortraitCatalog(characterName, files)
    }

    private suspend fun ensurePortraitFilesByCharacter(): Map<String, List<Pair<String, String>>> {
        portraitFilesByCharacterCache?.let { return it }

        val illustrationFiles = PORTRAIT_SOURCE_CATEGORIES.flatMap { category ->
            WikiEngine.fetchFilesInCategory(category, audioOnly = false)
        }
        val previewFiles = PREVIEW_SOURCE_CATEGORIES.flatMap { category ->
            WikiEngine.fetchFilesInCategory(category, audioOnly = false)
        }
        val rawGrouped = groupRawPortraitFiles(illustrationFiles + previewFiles)

        val officialNames = WikiEngine.getAllCharacterNames()
        val remapped = remapPortraitFilesToOfficialNames(rawGrouped, officialNames)
            .filterKeys { officialNames.contains(it) }
        portraitFilesByCharacterCache = remapped
        return remapped
    }
}
