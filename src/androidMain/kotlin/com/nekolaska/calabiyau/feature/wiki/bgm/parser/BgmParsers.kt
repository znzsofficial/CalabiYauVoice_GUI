package com.nekolaska.calabiyau.feature.wiki.bgm.parser

import com.nekolaska.calabiyau.feature.wiki.bgm.model.BgmAlbum
import com.nekolaska.calabiyau.feature.wiki.bgm.model.BgmAlbumTrack
import com.nekolaska.calabiyau.feature.wiki.bgm.model.BgmPage
import com.nekolaska.calabiyau.feature.wiki.bgm.model.BgmTrack
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

object BgmParsers {

    fun parsePage(html: String): BgmPage {
        val document = Jsoup.parse(html)
        document.select("#toc, .mw-editsection, style, script, sup.reference, .references, .mw-references-wrap").remove()
        val content = document.selectFirst(".mw-parser-output") ?: document.body()
        val coverLinks = collectCoverLinks(content)
        val tracks = mutableListOf<BgmTrack>()
        val albums = mutableListOf<BgmAlbum>()
        var category = "未分类"
        var group: String? = null
        var section: String? = null

        content.children().forEach { element ->
            when (element.tagName()) {
                "h2" -> {
                    category = element.headingText().ifBlank { category }
                    group = null
                    section = null
                }
                "h3" -> {
                    group = element.headingText().ifBlank { group }
                    section = null
                }
                "h4" -> {
                    section = element.headingText().ifBlank { section }
                }
                else -> {
                    val context = ParseContext(category, group, section)
                    val parsedTracks = parsePlayers(element, context, coverLinks)
                    tracks += parsedTracks
                    albums += parseAlbumTables(element, context, coverLinks)
                }
            }
        }

        val albumsByTitle = albums.distinctBy { it.title }
        val enrichedTracks = tracks.distinctBy { it.audioUrl }.map { track ->
            val albumTrack = albumsByTitle.firstNotNullOfOrNull { album ->
                album.tracks.firstOrNull { it.title.matchesTrackTitle(track.title) }?.let { album to it }
            }
            track.copy(
                album = track.album ?: albumTrack?.first?.title,
                duration = track.duration ?: albumTrack?.second?.duration,
                scene = track.scene ?: albumTrack?.second?.scene,
                lyrics = emptyList()
            )
        }

        return BgmPage(
            tracks = enrichedTracks,
            albums = albumsByTitle,
            lyricSections = emptyList()
        )
    }

    private fun parsePlayers(element: Element, context: ParseContext, coverLinks: Map<String, String>): List<BgmTrack> {
        val players = element.select("div.playerBox[data-music], div#CDPlayer[data-music]")
        if (players.isEmpty()) return parseHiddenAudioLinks(element, context)

        return players.flatMap { player ->
            val separator = if (player.id() == "CDPlayer" || player.attr("data-music").contains('|')) "|" else ","
            val names = splitMediaAttr(player.attr("data-name"), separator)
            val mediaTitles = collectMediaTitles(element)
            val fallbackTitles = collectFollowingTitles(element)
            val musicUrls = splitMediaAttr(player.attr("data-music"), separator).filter { it.contains(".mp3", true) }
            val covers = splitMediaAttr(player.attr("data-cover"), separator)
            musicUrls.mapIndexed { index, url ->
                val title = if (player.id() == "CDPlayer") {
                    listOf("名为真相的幻影", "To The Beautiful").getOrElse(index) { "名为真相的幻影" }
                } else names.getOrNull(index)
                    ?: mediaTitles.getOrNull(index)
                    ?: fallbackTitles.getOrNull(index)
                    ?: cleanAudioTitle(url.substringAfterLast('/'))
                BgmTrack(
                    title = title,
                    category = context.category,
                    group = context.group,
                    section = context.section,
                    audioUrl = url,
                    coverUrl = covers.getOrNull(index) ?: matchCover(title, context, coverLinks),
                    character = context.character,
                    album = context.albumTitle
                )
            }
        }
    }

    private fun parseHiddenAudioLinks(element: Element, context: ParseContext): List<BgmTrack> {
        val titles = collectFollowingTitles(element)
        return element.select("a[href$=.mp3], a[href*=.mp3]").mapIndexedNotNull { index, link ->
            val url = link.absUrl("href").ifBlank { link.attr("href") }
            if (!url.contains(".mp3", true)) return@mapIndexedNotNull null
            BgmTrack(
                title = titles.getOrNull(index).orEmpty().ifBlank { cleanAudioTitle(link.attr("title").ifBlank { link.cleanText() }) },
                category = context.category,
                group = context.group,
                section = context.section,
                audioUrl = url,
                character = context.character,
                album = context.albumTitle
            )
        }
    }

    private fun parseAlbumTables(element: Element, context: ParseContext, coverLinks: Map<String, String>): List<BgmAlbum> {
        if (context.category != "音乐专辑") return emptyList()
        val tables = element.select("table.klbqtable.table-hover")
        if (tables.isEmpty() || context.group.isNullOrBlank()) return emptyList()
        return tables.mapNotNull { table ->
            val rows = table.select("tr").drop(1).mapNotNull { row ->
                val cells = row.select("td").map { it.cleanText() }
                if (cells.isEmpty()) return@mapNotNull null
                BgmAlbumTrack(
                    title = cells.getOrNull(0).orEmpty().replace(Regex("^\\d+[.、]\\s*"), "").trim(),
                    duration = cells.getOrNull(1)?.takeIf { it.isNotBlank() },
                    scene = cells.getOrNull(2)?.takeIf { it.isNotBlank() }
                )
            }.filter { it.title.isNotBlank() }
            if (rows.isEmpty()) return@mapNotNull null
            BgmAlbum(
                title = context.group,
                coverUrl = matchCover(context.group, context, coverLinks),
                tracks = rows
            )
        }
    }

    private fun collectCoverLinks(content: Element): Map<String, String> {
        return content.select("a[href$=.jpg], a[href$=.png], a[href*=.jpg], a[href*=.png]")
            .mapNotNull { link ->
                val title = link.attr("title").ifBlank { link.cleanText() }
                val key = title
                    .substringAfter("专辑封面-", title)
                    .substringAfter("BGM封面-", title)
                    .substringBeforeLast('.')
                    .trim()
                val url = link.absUrl("href").ifBlank { link.attr("href") }
                if ((title.startsWith("专辑封面") || title.startsWith("BGM封面")) && url.isNotBlank()) key to url else null
            }
            .toMap()
    }

    private fun matchCover(title: String, context: ParseContext, coverLinks: Map<String, String>): String? {
        val candidates = listOfNotNull(title, context.group, context.section).map { it.normalizedKey() }
        return coverLinks.entries.firstOrNull { entry ->
            val key = entry.key.normalizedKey()
            candidates.any { candidate -> candidate.contains(key) || key.contains(candidate) }
        }?.value
    }

    private fun splitMediaAttr(raw: String, separator: String): List<String> {
        return Parser.unescapeEntities(raw, false)
            .split(separator)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun collectFollowingTitles(audioContainer: Element): List<String> {
        return generateSequence(audioContainer.nextElementSibling()) { it.nextElementSibling() }
            .takeWhile { it.tagName() !in setOf("h2", "h3", "h4") }
            .firstOrNull { it.tagName() in setOf("ol", "ul") || it.selectFirst("ol, ul") != null }
            ?.select("li")
            ?.mapNotNull { li ->
                li.cleanText()
                    .replace(Regex("^\\d+[.]\\s*"), "")
                    .takeUnless { it.startsWith("操作方法") || it.startsWith("单击") || it.startsWith("双击") }
                    ?.takeIf { it.isNotBlank() }
            }
            .orEmpty()
    }

    private fun collectMediaTitles(element: Element): List<String> {
        return element.select("a[href$=.mp3], a[href*=.mp3]")
            .mapNotNull { link ->
                cleanAudioTitle(link.attr("title").ifBlank { link.cleanText() })
                    .takeIf { it != "未命名 BGM" }
            }
    }

    private fun cleanAudioTitle(raw: String): String {
        return Parser.unescapeEntities(raw, false)
            .substringAfterLast("媒体文件:")
            .substringAfterLast('/')
            .removePrefix("Bgm-")
            .removePrefix("BGM-")
            .removeSuffix(".mp3")
            .replace('_', ' ')
            .trim()
            .ifBlank { "未命名 BGM" }
    }

    private fun String.matchesTrackTitle(other: String): Boolean {
        val left = normalizedKey()
        val right = other.normalizedKey()
        return left.isNotBlank() && right.isNotBlank() && (left.contains(right) || right.contains(left))
    }

    private fun String.normalizedKey(): String {
        return lowercase()
            .replace(Regex("[《》（）()\\[\\]【】\\s_-]"), "")
            .replace(Regex("^\\d+[.、]"), "")
    }

    private fun Element.headingText(): String = selectFirst(".mw-headline")?.cleanText().orEmpty().ifBlank { cleanText() }

    private fun Element.cleanText(): String {
        val copy = clone()
        copy.select("style,script,.mw-editsection").remove()
        return copy.text().replace(Regex("\\s+"), " ").trim()
    }

    private data class ParseContext(
        val category: String,
        val group: String?,
        val section: String?
    ) {
        val character: String? = if (category == "角色音乐") group else null
        val albumTitle: String? = if (category == "音乐专辑") group else null
    }
}
