package com.nekolaska.calabiyau.data

import data.PortraitRepositoryCore
import portrait.CharacterPortraitCatalog

/**
 * Android 端立绘仓库 —— 委托给 commonMain 的 PortraitRepositoryCore。
 */
object PortraitRepository {

    suspend fun searchCharacters(keyword: String): List<String> =
        PortraitRepositoryCore.searchCharacters(
            keyword,
            fetchFilesInCategory = { cat, audio -> WikiEngine.fetchFilesInCategory(cat, audio) },
            searchFilesFn = { kw, audio -> WikiEngine.searchFiles(kw, audio) },
            getAllCharacterNames = { WikiEngine.getAllCharacterNames() }
        )

    suspend fun loadCharacterPortraitCatalog(characterName: String): CharacterPortraitCatalog =
        PortraitRepositoryCore.loadCharacterPortraitCatalog(
            characterName,
            fetchFilesInCategory = { cat, audio -> WikiEngine.fetchFilesInCategory(cat, audio) },
            searchFilesFn = { kw, audio -> WikiEngine.searchFiles(kw, audio) },
            getAllCharacterNames = { WikiEngine.getAllCharacterNames() }
        )
}
