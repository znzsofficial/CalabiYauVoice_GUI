package com.nekolaska.calabiyau.feature.wiki.decoration.model

/** Source-owned directory before imageinfo: produced by a validated Lua pair or the HTML directory adapter. */
internal data class RawDecorationSection(
    val title: String,
    val items: List<RawDecorationItem>,
    val key: String = title
)

internal data class RawDecorationItem(
    val id: Int,
    val name: String,
    val quality: Int,
    val description: String,
    val source: String,
    val icon: DecorationImageFile,
    val image: DecorationImageFile,
    val extraPreviews: List<RawDecorationPreview> = emptyList(),
    val specialDescription: String = "",
    val platform: String = "PC"
) {
    val imageFiles: List<DecorationImageFile>
        get() = listOf(icon, image) + extraPreviews.map { it.image }
}

/** An original URL is evidence for this exact file only; a thumbnail leaves originalUrl empty. */
internal data class DecorationImageFile(val fileName: String, val originalUrl: String = "")

internal data class RawDecorationPreview(val label: String, val image: DecorationImageFile)

internal data class DecorationParseResult(
    val sections: List<RawDecorationSection>,
    val issues: List<String> = emptyList()
) {
    val isComplete: Boolean get() = sections.isNotEmpty() && issues.isEmpty()
}

/** One validated pair is cached atomically; never join independently aged Data and ways snapshots. */
internal data class DecorationLuaSnapshot(
    val itemsText: String,
    val waysText: String,
    val isFromCache: Boolean = false,
    val ageMs: Long = 0L
)

internal data class LuaDecorationItem(
    val id: Int,
    val name: String,
    val quality: Int,
    val description: String,
    val specialDescription: String?,
    val fileName: String?,
    val rawName: String
)

internal data class DecorationItemsResult(val items: List<LuaDecorationItem>, val issues: List<String>)
internal data class DecorationWaysResult(val ways: Map<Int, String>, val issues: List<String>)
