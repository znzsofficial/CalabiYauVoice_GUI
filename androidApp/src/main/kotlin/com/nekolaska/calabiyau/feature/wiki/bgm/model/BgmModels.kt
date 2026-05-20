package com.nekolaska.calabiyau.feature.wiki.bgm.model

const val BGM_PAGE_NAME = "BGM"
const val BGM_PAGE_URL = "https://wiki.biligame.com/klbq/BGM"

data class BgmPage(
    val tracks: List<BgmTrack>,
    val albums: List<BgmAlbum>,
    val lyricSections: List<BgmLyricSection>
)

data class BgmTrack(
    val title: String,
    val category: String,
    val group: String?,
    val section: String?,
    val audioUrl: String,
    val coverUrl: String? = null,
    val character: String? = null,
    val album: String? = null,
    val duration: String? = null,
    val scene: String? = null,
    val lyrics: List<BgmLyricSection> = emptyList()
)

data class BgmAlbum(
    val title: String,
    val coverUrl: String?,
    val tracks: List<BgmAlbumTrack>
)

data class BgmAlbumTrack(
    val title: String,
    val duration: String?,
    val scene: String?
)

data class BgmLyricSection(
    val title: String,
    val category: String,
    val group: String?,
    val section: String?,
    val character: String?,
    val lines: List<BgmLyricLine>
)

data class BgmLyricLine(
    val original: String,
    val translated: String? = null
)
