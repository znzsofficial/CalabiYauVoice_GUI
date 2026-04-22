package com.nekolaska.calabiyau.data

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URLEncoder

object StringerTalentApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val PAGE_NAME = "超弦体天赋"
    private const val WIKI_URL = "https://wiki.biligame.com/klbq/%E8%B6%85%E5%BC%A6%E4%BD%93%E5%A4%A9%E8%B5%8B"

    data class TalentPage(
        val title: String,
        val wikiUrl: String,
        val sections: List<TalentSection>
    )

    data class TalentSection(
        val title: String,
        val items: List<TalentItem>
    )

    data class TalentItem(
        val name: String,
        val unlockLevel: String,
        val maxLevel: String,
        val details: List<String>,
        val imageUrl: String?
    )

    private var cachedPage: TalentPage? = null
    private val json = SharedJson

    fun clearMemoryCache() {
        cachedPage = null
    }

    suspend fun fetch(forceRefresh: Boolean = false): ApiResult<TalentPage> {
        if (!forceRefresh) {
            cachedPage?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }

    private suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<TalentPage> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(PAGE_NAME, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=text&format=json"
                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.GAME_MODES,
                    key = "stringer_talent_page",
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) } ?: return@withContext ApiResult.Error(
                    "获取超弦体天赋失败，且无离线缓存",
                    kind = ErrorKind.NETWORK
                )

                val root = json.parseToJsonElement(result.json).jsonObject
                val html = root["parse"]?.jsonObject
                    ?.get("text")?.jsonObject
                    ?.get("*")?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error("解析超弦体天赋 HTML 失败", kind = ErrorKind.PARSE)

                val page = parseHtml(html)
                if (page.sections.isEmpty()) {
                    ApiResult.Error("未找到超弦体天赋数据", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(page, isOffline = result.isFromCache, cacheAgeMs = result.ageMs)
                }
            } catch (e: Exception) {
                ApiResult.Error("获取超弦体天赋失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    private fun parseHtml(html: String): TalentPage {
        val document = Jsoup.parse(html)
        val content = document.selectFirst(".mw-parser-output") ?: document.body()
        val sections = mutableListOf<TalentSection>()

        var currentTitle: String? = null
        var currentItems = mutableListOf<TalentItem>()

        fun flushSection() {
            val title = currentTitle ?: return
            if (currentItems.isNotEmpty()) {
                sections += TalentSection(title = title, items = currentItems.toList())
            }
            currentItems = mutableListOf()
        }

        content.children().forEach { element ->
            when (element.tagName()) {
                "h2", "h3" -> {
                    val title = element.selectFirst(".mw-headline")
                        ?.text()
                        ?.replace(" ", "")
                        ?.trim()
                        .orEmpty()
                    if (title in setOf("机能", "生存", "续航", "输出")) {
                        flushSection()
                        currentTitle = title
                    }
                }

                "table" -> {
                    if (currentTitle == null) return@forEach
                    if (looksLikeTalentTable(element)) {
                        currentItems += parseTalentRows(element)
                    }
                }

                "div" -> {
                    if (currentTitle == null) return@forEach
                    element.select("table").forEach { table ->
                        if (looksLikeTalentTable(table)) {
                            currentItems += parseTalentRows(table)
                        }
                    }
                }
            }
        }

        flushSection()
        return TalentPage(
            title = PAGE_NAME,
            wikiUrl = WIKI_URL,
            sections = sections
        )
    }

    private fun parseTalentRows(table: org.jsoup.nodes.Element): List<TalentItem> {
        return table.select("tr").drop(1).mapNotNull { row ->
            val cells = row.select("th,td")
            if (cells.size < 4) return@mapNotNull null

            val nameCell = cells[0]
            val rawName = nameCell.text().trim()
            val name = rawName.substringAfterLast(".png", rawName).trim().ifBlank { rawName }
            val imageUrl = nameCell.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
            val unlockLevel = cells[1].text().trim()
            val maxLevel = cells[2].text().trim()
            val detailText = cells[3].text().trim()
            val details = splitTalentDetails(detailText)

            TalentItem(
                name = name,
                unlockLevel = unlockLevel,
                maxLevel = maxLevel,
                details = details.ifEmpty { listOf(detailText) },
                imageUrl = imageUrl
            )
        }
    }

    private fun looksLikeTalentTable(table: org.jsoup.nodes.Element): Boolean {
        val headers = table.select("tr").firstOrNull()?.select("th,td")
            ?.map { it.text().replace(" ", "").trim() }
            .orEmpty()
        return headers.any { it.contains("解锁等级") } &&
            headers.any { it.contains("最大等级") } &&
            headers.any { it.contains("天赋等级详情") }
    }

    private fun splitTalentDetails(text: String): List<String> {
        val normalized = text.replace("：", ":")
        val matches = Regex("(\\d+)级:").findAll(normalized).toList()
        if (matches.isEmpty()) return listOf(text)

        return matches.mapIndexed { index, match ->
            val start = match.range.first
            val end = matches.getOrNull(index + 1)?.range?.first ?: normalized.length
            normalized.substring(start, end).trim()
        }
    }
}