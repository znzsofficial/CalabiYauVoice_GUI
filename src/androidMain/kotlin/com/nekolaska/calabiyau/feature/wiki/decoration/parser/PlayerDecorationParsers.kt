package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import com.nekolaska.calabiyau.core.wiki.LuaTableParser
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object PlayerDecorationParsers {

    internal data class RawItem(
        val id: Int,
        val name: String,
        val quality: Int,
        val iconFile: String,
        val imgFile: String,
        val desc: String,
        val source: String,
        val extraPreviewFiles: List<Pair<String, String>> = emptyList()
    )

    data class DecorationModuleData(
        val name: String,
        val quality: Int,
        val description: String,
        val specialDescription: String,
        val source: String
    )

    private val idFromFileRegex = Regex("""[\s_](\d+)""")
    private val iconToImgRegex = Regex("""(.+[\s_]\d+)[\s_]icon\w*(\.\w+)""")

    internal fun parseHtml(html: String): List<Pair<String, List<RawItem>>> {
        val document = Jsoup.parse(html)
        val sections = mutableListOf<Pair<String, MutableList<RawItem>>>()
        var currentTitle = "默认"
        var currentItems = mutableListOf<RawItem>()

        document.select(".mw-parser-output").firstOrNull()?.children().orEmpty().forEach { child ->
            when {
                child.tagName().matches(Regex("h[1-6]")) && child.selectFirst("span.mw-headline") != null -> {
                    if (currentItems.isNotEmpty()) {
                        sections.add(currentTitle to currentItems)
                        currentItems = mutableListOf()
                    }
                    currentTitle = child.selectFirst("span.mw-headline")?.text()?.trim().orEmpty().ifEmpty { "默认" }
                }
                child.hasClass("gallerygrid") || child.selectFirst(".gallerygrid-item") != null -> {
                    child.select(".gallerygrid-item").forEach { itemElement ->
                        parseGridItem(itemElement)?.let { currentItems.add(it) }
                    }
                }
            }
        }

        if (currentItems.isNotEmpty()) sections.add(currentTitle to currentItems)
        return WikiParseLogger.finishList("PlayerDecorationApi.parseHtml", sections, html)
    }

    internal fun extractFileNames(rawSections: List<Pair<String, List<RawItem>>>): Set<String> {
        val allIconFiles = rawSections.flatMap { s -> s.second.map { it.iconFile } }
            .filter { it.isNotEmpty() }
        val allImgFiles = rawSections.flatMap { s -> s.second.map { it.imgFile } }
            .filter { it.isNotEmpty() }
        val extraPreviewFiles = rawSections
            .flatMap { section -> section.second.flatMap { item -> item.extraPreviewFiles.map { preview -> preview.second } } }
            .filter { it.isNotEmpty() }
        return (allIconFiles + allImgFiles + extraPreviewFiles).toSet()
    }

    internal fun mapSections(
        rawSections: List<Pair<String, List<RawItem>>>,
        urlMap: Map<String, String>,
        moduleDataMap: Map<Int, DecorationModuleData>,
        mapper: (title: String, items: List<com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationItem>) -> com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationSection
    ): List<com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationSection> {
        return rawSections.mapNotNull { (title, rawItems) ->
            val items = rawItems.mapNotNull { raw ->
                val iconUrl = urlMap[raw.iconFile] ?: ""
                val imgUrl = urlMap[raw.imgFile] ?: ""
                if (iconUrl.isEmpty() && imgUrl.isEmpty()) return@mapNotNull null
                val extraPreviews = raw.extraPreviewFiles.mapNotNull { (label, fileName) ->
                    val previewUrl = urlMap[fileName].orEmpty()
                    if (previewUrl.isBlank()) null
                    else com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationPreview(label, previewUrl)
                }
                com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationItem(
                    id = raw.id,
                    name = moduleDataMap[raw.id]?.name ?: raw.name,
                    quality = moduleDataMap[raw.id]?.quality ?: raw.quality,
                    description = moduleDataMap[raw.id]?.description ?: raw.desc,
                    specialDescription = moduleDataMap[raw.id]?.specialDescription.orEmpty(),
                    source = moduleDataMap[raw.id]?.source ?: raw.source,
                    iconUrl = iconUrl,
                    imageUrl = imgUrl,
                    extraPreviews = extraPreviews
                )
            }
            if (items.isEmpty()) null else mapper(title, items)
        }
    }

    fun parseModuleData(wikitext: String): Map<Int, DecorationModuleData> {
        val root = LuaTableParser.parseReturnArray(wikitext)
        return root.mapNotNull { value ->
            val fields = (value as? LuaTableParser.LuaValue.LuaObject)?.fields ?: return@mapNotNull null
            val id = (fields["id"] as? LuaTableParser.LuaValue.LuaNumber)?.value ?: return@mapNotNull null
            val name = (fields["name"] as? LuaTableParser.LuaValue.LuaString)?.value.orEmpty().trim()
            val quality = (fields["quality"] as? LuaTableParser.LuaValue.LuaNumber)?.value ?: 0
            val description = ((fields["desc"] as? LuaTableParser.LuaValue.LuaString)?.value)
                .orEmpty()
                .replace("<br />", "\n")
                .replace("<br/>", "\n")
                .replace("<br>", "\n")
                .trim()
            val specialDescription = ((fields["spdesc"] as? LuaTableParser.LuaValue.LuaString)?.value)
                .orEmpty()
                .replace("<br />", "\n")
                .replace("<br/>", "\n")
                .replace("<br>", "\n")
                .trim()
            val source = ((fields["get"] as? LuaTableParser.LuaValue.LuaArray)?.values ?: emptyList())
                .mapNotNull { (it as? LuaTableParser.LuaValue.LuaString)?.value?.trim() }
                .filter { it.isNotBlank() }
                .joinToString("、")

            id to DecorationModuleData(
                name = name,
                quality = quality,
                description = description,
                specialDescription = specialDescription,
                source = source
            )
        }.toMap()
    }

    private fun parseGridItem(itemElement: Element): RawItem? {
        if (itemElement.hasClass("roomgrid-item")) {
            return parseRoomGridItem(itemElement)
        }

        val iconFile = itemElement.selectFirst("img[alt]")
            ?.attr("alt")
            ?.trim()
            ?.takeIf { it.contains('.') }
            ?: return null

        val id = idFromFileRegex.find(iconFile)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        val imgFile = iconToImgRegex.find(iconFile)?.let {
            "${it.groupValues[1]}${it.groupValues[2]}"
        } ?: iconFile

        val qualityElement = itemElement.selectFirst("span[data-quality]")
        val quality = qualityElement?.attr("data-quality")?.toIntOrNull() ?: 0
        val name = qualityElement?.text()?.trim().orEmpty().ifEmpty { "未知" }

        val desc = itemElement.selectFirst("small")?.text()?.trim().orEmpty()

        val source = itemElement.select("li")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }.joinToString("、")

        return RawItem(id, name, quality, iconFile, imgFile, desc, source)
    }

    private fun parseRoomGridItem(itemElement: Element): RawItem? {
        val backgroundFile = itemElement.selectFirst(".roomgrid-image-bg img[alt]")
            ?.attr("alt")
            ?.trim()
            ?.takeIf { it.contains('.') }
            ?: return null

        val id = idFromFileRegex.find(backgroundFile)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val qualityElement = itemElement.selectFirst(".roomgrid-player-decoration [data-quality]")
        val quality = qualityElement?.attr("data-quality")?.toIntOrNull() ?: 0
        val name = qualityElement?.text()?.trim().orEmpty().ifEmpty { "未知" }
        val desc = itemElement.selectFirst(".roomgrid-desc")?.text()?.trim().orEmpty()
        val source = itemElement.select("li")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .joinToString("、")
        val extraPreviewFiles = itemElement.select(".roomgrid-ui-item").mapNotNull { uiItem ->
            val label = uiItem.selectFirst(".ui-label")?.text()?.trim().orEmpty()
            val fileName = uiItem.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty()
            if (label.isBlank() || !fileName.contains('.')) null else label to fileName
        }

        return RawItem(
            id = id,
            name = name,
            quality = quality,
            iconFile = backgroundFile,
            imgFile = backgroundFile,
            desc = desc,
            source = source,
            extraPreviewFiles = extraPreviewFiles
        )
    }
}
