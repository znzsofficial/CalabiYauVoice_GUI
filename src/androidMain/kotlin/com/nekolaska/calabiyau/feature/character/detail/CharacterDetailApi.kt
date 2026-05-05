package com.nekolaska.calabiyau.feature.character.detail

import android.text.Html
import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import com.nekolaska.calabiyau.core.wiki.WikiParseLogger
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * 角色详情 API（Android）。
 *
 * 通过 MediaWiki parse API 获取角色页面的 wikitext，
 * 解析 `{{超弦体|...}}` 模板参数提取角色信息。
 */
object CharacterDetailApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_BASE = "https://wiki.biligame.com"
    private const val SETTING_PAGE = "超弦体设定"

    @Volatile
    private var cachedExtras: Map<String, CharacterExtraInfo>? = null

    /** 角色详情信息 */
    data class CharacterDetail(
        val name: String,
        val englishName: String,
        val japaneseName: String,
        val gender: String,
        val role: String,
        val faction: String,
        val summary: String,
        val identity: String,
        val activeArea: String,
        val height: String,
        val weight: String,
        val birthday: String,
        val age: String,
        val cnVoiceActor: String,
        val jpVoiceActor: String,
        val weaponName: String,
        val weaponType: String,
        val weaponIntro: String,
        val traits: String,
        val hobbies: String,
        val diet: String,
        val quote: String,
        val description: String,
        val observerQuote: String,
        val avatarUrl: String?,
        val subPages: List<SubPage>,
        val skills: List<SkillInfo>,
        val stories: List<StoryEntry>,
        val positionName: String = "",
        val positionDuty: String = "",
        val settingSummary: String = "",
        val settingObserverQuote: String = "",
        val lifeInfo: List<InfoPair> = emptyList(),
        val relations: List<InfoPair> = emptyList(),
        val updateHistory: List<UpdateEntry> = emptyList()
    )

    data class InfoPair(
        val label: String,
        val value: String
    )

    private data class CharacterExtraInfo(
        val positionName: String = "",
        val positionDuty: String = "",
        val settingSummary: String = "",
        val settingObserverQuote: String = "",
        val lifeInfo: List<InfoPair> = emptyList(),
        val relations: List<InfoPair> = emptyList()
    )

    private data class AggregatePageSourceResult(
        val html: String,
        val isFromCache: Boolean,
        val ageMs: Long
    )

    /** 更新改动历史条目 */
    data class UpdateEntry(
        val date: String,
        val changes: List<String>
    )

    data class SubPage(
        val title: String,
        val displayName: String,
        val wikiUrl: String
    )

    /** 技能信息 */
    data class SkillInfo(
        val slot: String,
        val name: String,
        val description: String,
        val videoUrl: String?,
        val iconUrl: String? = null
    )

    /** 角色故事/相关剧情条目 */
    data class StoryEntry(
        val title: String,
        val imageFileName: String,
        val pageUrl: String,
        val imageUrl: String?,
        val section: String
    )


    /**
     * 获取角色详情。
     * @param characterName 角色名（如"米雪儿·李"）
     */
    suspend fun fetchCharacterDetail(
        characterName: String,
        forceRefresh: Boolean = false
    ): ApiResult<CharacterDetail> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(characterName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=wikitext|text&format=json"

                val result = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.CHARACTER_DETAIL,
                    key = characterName,
                    forceRefresh = forceRefresh
                ) { WikiEngine.safeGet(url) }
                    ?: return@withContext ApiResult.Error(
                        "请求失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )
                val body = result.payload

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val parseObj = json["parse"]?.jsonObject
                val wikitext = parseObj?.get("wikitext")
                    ?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error(
                        "无法获取页面内容",
                        kind = ErrorKind.PARSE
                    )

                val detail = parseCharacterWikitext(characterName, wikitext)
                    ?: return@withContext ApiResult.Error(
                        "未找到角色信息模板",
                        kind = ErrorKind.NOT_FOUND
                    )

                val extras = fetchCharacterExtras(forceRefresh)
                val extraInfo = extras[normalizeCharacterName(characterName)]
                    ?: extras[characterName]
                    ?: CharacterExtraInfo()

                // 从渲染 HTML 解析改动历史
                val html = parseObj["text"]
                    ?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                val history = if (html != null) parseUpdateHistory(html) else emptyList()

                ApiResult.Success(
                    mergeExtraInfo(detail, extraInfo).copy(updateHistory = history),
                    isOffline = result.isFromCache,
                    cacheAgeMs = result.ageMs
                )
            } catch (e: Exception) {
                ApiResult.Error("获取角色详情失败: ${e.message}", kind = e.toErrorKind())
            }
        }

    /**
     * 直接从各角色页面 `{{超弦体}}` / `{{晶源体}}` 表格模板中批量解析定位。
     * 不再依赖“超弦体定位”聚合页，避免聚合页结构变动导致所有角色定位串到同一类。
     */
    suspend fun fetchCharacterPositions(
        characterNames: Collection<String>,
        forceRefresh: Boolean = false
    ): Map<String, String> = withContext(Dispatchers.IO) {
        coroutineScope {
            characterNames
                .filter { it.isNotBlank() }
                .distinct()
                .map { characterName ->
                    async {
                        runCatching {
                            val encoded = URLEncoder.encode(characterName, "UTF-8")
                            val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
                            val result = OfflineCache.fetchWithCache(
                                type = OfflineCache.Type.CHARACTER_DETAIL,
                                key = characterName,
                                forceRefresh = forceRefresh
                            ) { WikiEngine.safeGet(url) } ?: return@runCatching null

                            val json = SharedJson.parseToJsonElement(result.payload).jsonObject
                            val wikitext = json["parse"]?.jsonObject
                                ?.get("wikitext")?.jsonObject
                                ?.get("*")?.jsonPrimitive?.content
                                ?: return@runCatching null

                            parseCharacterPositionFromWikitext(wikitext)?.let { position ->
                                characterName to position
                            }
                        }.getOrNull()
                    }
                }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }
    }

    /**
     * 从 wikitext 解析 `{{超弦体|...}}` 或 `{{晶源体|...}}` 模板参数。
     */
    private fun parseCharacterWikitext(name: String, wikitext: String): CharacterDetail? {
        // 先尝试超弦体模板，再尝试晶源体模板
        val isCrystal: Boolean
        val templateContent: String
        val superstring = extractTemplate(wikitext, "超弦体")
        if (superstring != null) {
            templateContent = superstring
            isCrystal = false
        } else {
            templateContent = extractTemplate(wikitext, "晶源体") ?: return null
            isCrystal = true
        }

        val params = parseTemplateParams(templateContent)

        // 提取 {{页顶导航 ... }} 中的子页面
        val navContent = extractTemplate(wikitext, "页顶导航")
        val subPages = if (navContent != null) parseSubPages(navContent) else emptyList()

        val avatarUrl = fetchAvatarUrl(name)
        val skills = parseSkills(name, wikitext, isCrystal)
        val stories = parseStories(wikitext)
        val position = parseCharacterPositionFromWikitext(wikitext).orEmpty()

        // 晶源体的声优字段是合并的 "声优"，需拆分中/日
        val voiceRaw = if (isCrystal) clean(params["声优"]) else ""
        val cnVoice: String
        val jpVoice: String
        if (isCrystal && voiceRaw.isNotEmpty()) {
            // 格式如 "中文：王美心\n日文：佐々木未来"
            cnVoice = voiceRaw.lineSequence()
                .firstOrNull { it.startsWith("中文") }?.substringAfter("：")?.trim() ?: voiceRaw
            jpVoice = voiceRaw.lineSequence()
                .firstOrNull { it.startsWith("日文") }?.substringAfter("：")?.trim() ?: ""
        } else {
            cnVoice = clean(params["中文声优"])
            jpVoice = clean(params["日文声优"])
        }

        return CharacterDetail(
            name = name,
            englishName = clean(params["英文名"]),
            japaneseName = clean(params["日文名"]),
            gender = clean(params["性别"]),
            role = if (isCrystal) "晶源体" else position.ifBlank { clean(params["定位"]) },
            faction = if (isCrystal) "晶源体" else clean(params["阵营"]),
            summary = clean(params["角色简介"].takeUnless { it.isNullOrBlank() } ?: params["简介"]),
            identity = clean(params["身份"]),
            activeArea = clean(params["活动区域"]),
            height = clean(params["身高"]),
            weight = clean(params["体重"]),
            birthday = clean(params["生日"]),
            age = clean(params["年龄"]),
            cnVoiceActor = cnVoice,
            jpVoiceActor = jpVoice,
            weaponName = clean(params["武器名称"]),
            weaponType = clean(params["武器类型"]),
            weaponIntro = clean(params["武器介绍"]),
            traits = clean(params["超弦体特性"].takeUnless { it.isNullOrBlank() } ?: params["晶源体特性"]),
            hobbies = clean(params["兴趣爱好"]),
            diet = clean(params["饮食习惯"]),
            quote = clean(params["个性语录"]),
            description = clean(params["简介"]),
            observerQuote = clean(params["观测语录"]),
            avatarUrl = avatarUrl,
            subPages = subPages,
            skills = skills,
            stories = stories
        )
    }

    private fun mergeExtraInfo(
        detail: CharacterDetail,
        extraInfo: CharacterExtraInfo
    ): CharacterDetail {
        val mergedLifeInfo = extraInfo.lifeInfo.filterNot { extra ->
            val normalizedValue = normalizeText(extra.value)
            when (extra.label) {
                "生日" -> normalizedValue == normalizeText(detail.birthday)
                "年龄" -> normalizedValue == normalizeText(detail.age)
                "身高" -> normalizedValue == normalizeText(detail.height)
                "体重" -> normalizedValue == normalizeText(detail.weight)
                "活动区域" -> normalizedValue == normalizeText(detail.activeArea)
                "兴趣爱好" -> normalizedValue == normalizeText(detail.hobbies)
                "饮食习惯" -> normalizedValue == normalizeText(detail.diet)
                "个性语录" -> normalizedValue == normalizeText(detail.quote)
                else -> false
            }
        }

        return detail.copy(
            positionName = extraInfo.positionName.ifBlank { detail.role },
            positionDuty = extraInfo.positionDuty,
            settingSummary = extraInfo.settingSummary.takeUnless { normalizeText(it) == normalizeText(detail.summary) || normalizeText(it) == normalizeText(detail.description) }.orEmpty(),
            settingObserverQuote = extraInfo.settingObserverQuote.takeUnless { normalizeText(it) == normalizeText(detail.observerQuote) }.orEmpty(),
            lifeInfo = mergedLifeInfo,
            relations = extraInfo.relations
        )
    }

    private suspend fun fetchCharacterExtras(forceRefresh: Boolean): Map<String, CharacterExtraInfo> {
        if (!forceRefresh) {
            cachedExtras?.let { return it }
        }

        val settingHtml = fetchAggregatePageHtml(SETTING_PAGE, "character_setting", forceRefresh)?.html.orEmpty()

        val merged = mutableMapOf<String, CharacterExtraInfo>()
        parseSettingExtras(settingHtml).forEach { (name, info) ->
            merged[name] = merged[name].merge(info)
        }

        cachedExtras = merged
        return merged
    }

    private suspend fun fetchAggregatePageHtml(
        pageName: String,
        cacheKey: String,
        forceRefresh: Boolean
    ): AggregatePageSourceResult? {
        val encoded = URLEncoder.encode(pageName, "UTF-8")
        val url = "$API?action=parse&page=$encoded&prop=text&format=json"
        val result = OfflineCache.fetchWithCache(
            type = OfflineCache.Type.CHARACTER_DETAIL,
            key = cacheKey,
            forceRefresh = forceRefresh
        ) { WikiEngine.safeGet(url) } ?: return null

        val root = SharedJson.parseToJsonElement(result.payload).jsonObject
        val html = root["parse"]?.jsonObject?.get("text")
            ?.jsonObject?.get("*")?.jsonPrimitive?.content
            ?: return null
        return AggregatePageSourceResult(
            html = html,
            isFromCache = result.isFromCache,
            ageMs = result.ageMs
        )
    }

    private fun parsePositionExtras(html: String): Map<String, CharacterExtraInfo> {
        if (html.isBlank()) return emptyMap()

        val document = Jsoup.parse(html)
        val result = mutableMapOf<String, CharacterExtraInfo>()
        val rootChildren = document.select(".mw-parser-output").firstOrNull()?.children().orEmpty()

        rootChildren.forEachIndexed { index, element ->
            val headline = element.selectFirst("span.mw-headline") ?: return@forEachIndexed
            if (!element.tagName().matches(Regex("h[1-6]"))) return@forEachIndexed

            val positionName = headline.text().trim()
            if (positionName !in POSITION_NAMES) return@forEachIndexed

            val blockElements = rootChildren.drop(index + 1).takeWhile { child ->
                !child.tagName().matches(Regex("h[1-6]")) &&
                    child.id() != "toc" &&
                    !child.hasClass("printfooter") &&
                    !child.hasClass("catlinks")
            }
            val duty = blockElements
                .flatMap { it.select("li") }
                .firstOrNull { it.text().contains("定位职责") }
                ?.text()
                ?.substringAfter("定位职责：", "")
                ?.trim()
                .orEmpty()

            blockElements.flatMap { it.select("a[title]") }.forEach { linkElement ->
                val displayName = linkElement.attr("title").trim()
                if (displayName.isBlank() || displayName == positionName || displayName == "编辑") return@forEach
                if (displayName.contains(':') || displayName.endsWith(".png", ignoreCase = true)) return@forEach
                val key = normalizeCharacterName(displayName)
                result[key] = result[key].merge(
                    CharacterExtraInfo(
                        positionName = positionName,
                        positionDuty = duty
                    )
                )
            }
        }

        return WikiParseLogger.finishMap("CharacterDetailApi.parsePositionExtras", result, html)
    }

    private fun parseCharacterPositionFromWikitext(wikitext: String): String? {
        extractTemplate(wikitext, "超弦体")?.let { templateContent ->
            val position = clean(parseTemplateParams(templateContent)["定位"])
            if (position.isNotBlank()) return position
        }
        if (extractTemplate(wikitext, "晶源体") != null) return "晶源体"
        return null
    }

    private fun parseSettingExtras(html: String): Map<String, CharacterExtraInfo> {
        if (html.isBlank()) return emptyMap()

        val document = Jsoup.parse(html)
        val result = document.select(".klbq-role-info")
            .mapNotNull { block -> parseSingleSettingBlock(block) }
            .toMap()

        return WikiParseLogger.finishMap("CharacterDetailApi.parseSettingExtras", result, html)
    }

    private fun parseSingleSettingBlock(block: org.jsoup.nodes.Element): Pair<String, CharacterExtraInfo>? {
        val nameElement = block.selectFirst(".name")
        val rawName = nameElement?.ownText()?.trim().orEmpty()
        val fallbackName = nameElement?.text()?.trim().orEmpty().substringBefore(' ')
        val name = rawName.ifBlank { fallbackName }
        if (name.isBlank()) return null

        val relations = block.select(".data p span")
            .flatMap { span ->
                cleanHtml(span.html())
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()
            }
            .mapNotNull { line ->
                val separatorIndex = line.indexOf('：')
                if (separatorIndex <= 0) return@mapNotNull null
                val label = line.substring(0, separatorIndex).trim()
                val value = line.substring(separatorIndex + 1).trim().trim('、', '，', ',', ' ')
                if (label.isBlank() || value.isBlank() || label == "中文CV" || label == "日文CV") null
                else InfoPair(label, value)
            }

        val infoMap = linkedMapOf<String, String>()
        block.select("dl > div").forEach { item ->
            val label = item.selectFirst("dt")?.text()?.removeSuffix("：")?.trim().orEmpty()
            val value = item.selectFirst("dd")?.let { cleanHtml(it.html()) }.orEmpty()
            if (label.isNotBlank() && value.isNotBlank()) {
                infoMap[label] = value
            }
        }

        val summary = infoMap.remove("简介").orEmpty()
        val observerQuote = infoMap.remove("观测语录").orEmpty()
        infoMap.remove("萌点")

        val lifeInfo = infoMap.map { (label, value) -> InfoPair(label, value) }
        return normalizeCharacterName(name) to CharacterExtraInfo(
            settingSummary = summary,
            settingObserverQuote = observerQuote,
            lifeInfo = lifeInfo,
            relations = relations
        )
    }

    private fun CharacterExtraInfo?.merge(other: CharacterExtraInfo): CharacterExtraInfo {
        val base = this ?: CharacterExtraInfo()
        return CharacterExtraInfo(
            positionName = other.positionName.ifBlank { base.positionName },
            positionDuty = other.positionDuty.ifBlank { base.positionDuty },
            settingSummary = other.settingSummary.ifBlank { base.settingSummary },
            settingObserverQuote = other.settingObserverQuote.ifBlank { base.settingObserverQuote },
            lifeInfo = other.lifeInfo.ifEmpty { base.lifeInfo },
            relations = other.relations.ifEmpty { base.relations }
        )
    }

    private fun cleanHtml(raw: String): String {
        if (raw.isBlank()) return ""
        val normalized = raw
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace("&nbsp;", " ")
        return Html.fromHtml(normalized, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("￼", "")
            .replace("\uFFFC", "")
            .replace('\u00A0', ' ')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun normalizeCharacterName(name: String): String {
        return name
            .replace("·", "")
            .replace("・", "")
            .replace(" ", "")
            .trim()
    }

    private fun normalizeText(text: String): String {
        return text
            .replace(Regex("""\s+"""), "")
            .replace("：", ":")
            .trim()
    }

    private val POSITION_NAMES = setOf("决斗", "守护", "支援", "先锋", "控场")

    /** 清理 Wiki 标记 */
    private fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("\\{\\{黑幕\\|([^}]*?)\\}\\}"), "$1")       // {{黑幕|text}} → text
            .replace(Regex("\\{\\{#info:[^}]*?\\}\\}"), "")              // {{#info:...|note}} → 移除
            .replace(Regex("<br\\s*/?>\\n?"), "\n")                       // <br /> → 换行
            .replace(Regex("'''([^']*?)'''"), "$1")                       // '''粗体''' → 纯文本
            .replace(Regex("''([^']*?)''"), "$1")                         // ''斜体'' → 纯文本
            .replace(Regex("""\[\[[^|\]]*?\|([^\]]*?)\]\]"""), "$1")   // [[链接|显示]] → 显示
            .replace(Regex("""\[\[([^\]]*?)\]\]"""), "$1")              // [[链接]] → 链接
            .replace(Regex("<[^>]+>"), "")                                // HTML 标签移除
            .trim()
    }

    // ────────────────────────────────────────
    //  技能解析
    // ────────────────────────────────────────

    private val SKILL_SLOTS = listOf(
        Triple(1, "Q", "主动技能"),
        Triple(2, "P", "被动技能"),
        Triple(3, "X", "终极技能"),
        Triple(4, "E", "战术技能")
    )

    private fun parseSkills(characterName: String, wikitext: String, isCrystal: Boolean = false): List<SkillInfo> {
        val skills: List<SkillInfo>
        // 技能图片编号映射：超弦体 Q=1,P=2,X=3,E=4；晶源体 Q=1,X=3
        val slotToNum = mapOf("Q" to 1, "P" to 2, "X" to 3, "E" to 4)

        if (isCrystal) {
            val skillTemplate = extractTemplate(wikitext, "晶源体技能") ?: return emptyList()
            val params = parseTemplateParams(skillTemplate)
            val result = mutableListOf<SkillInfo>()
            val active = clean(params["主动技能解析"])
            if (active.isNotBlank()) {
                val videoRaw = params["主动技能视频演示"] ?: ""
                val videoUrl = Regex("url=([^}]+)").find(videoRaw)?.groupValues?.get(1)?.trim()
                result.add(SkillInfo(slot = "Q", name = "主动技能", description = active, videoUrl = videoUrl))
            }
            val ultimate = clean(params["终极技能解析"])
            if (ultimate.isNotBlank()) {
                val videoRaw = params["终极技能视频演示"] ?: ""
                val videoUrl = Regex("url=([^}]+)").find(videoRaw)?.groupValues?.get(1)?.trim()
                result.add(SkillInfo(slot = "X", name = "终极技能", description = ultimate, videoUrl = videoUrl))
            }
            skills = result
        } else {
            val skillTemplate = extractTemplate(wikitext, "角色技能") ?: return emptyList()
            val params = parseTemplateParams(skillTemplate)
            skills = SKILL_SLOTS.mapNotNull { (num, slot, name) ->
                val desc = clean(params["技能${num}解析"])
                if (desc.isBlank()) return@mapNotNull null
                val videoRaw = params["技能${num}视频演示"] ?: ""
                val videoUrl = Regex("url=([^}]+)").find(videoRaw)?.groupValues?.get(1)?.trim()
                SkillInfo(slot = slot, name = name, description = desc, videoUrl = videoUrl)
            }
        }

        // 批量获取技能图片 URL：{characterName}技能{N}.png
        if (skills.isEmpty()) return skills
        val fileNames = skills.map { "${characterName}技能${slotToNum[it.slot] ?: 1}.png" }
        val imageUrls = fetchImageUrls(fileNames)
        return skills.mapIndexed { i, skill ->
            skill.copy(iconUrl = imageUrls[fileNames[i]])
        }
    }

    // ────────────────────────────────────────
    //  角色故事解析
    // ────────────────────────────────────────

    private val NAV_CHARA_REGEX = Regex(
        """\{\{NavChara\|([^|]+)\|([^|]+)\|([^|}]+)"""
    )

    private fun parseStories(wikitext: String): List<StoryEntry> {
        val entries = mutableListOf<StoryEntry>()
        // 按区域分割
        val storySection = extractSection(wikitext, "角色故事")
        val relatedSection = extractSection(wikitext, "相关剧情")

        fun parseSection(text: String?, section: String) {
            if (text.isNullOrBlank()) return
            NAV_CHARA_REGEX.findAll(text).forEach { match ->
                val imageFile = match.groupValues[1].trim()
                val pagePath = match.groupValues[2].trim()
                val title = match.groupValues[3].trim()
                val encoded = URLEncoder.encode(pagePath, "UTF-8").replace("+", "%20")
                entries.add(
                    StoryEntry(
                        title = title,
                        imageFileName = imageFile,
                        pageUrl = "$WIKI_BASE/klbq/$encoded",
                        imageUrl = null, // 稍后批量获取
                        section = section
                    )
                )
            }
        }
        parseSection(storySection, "角色故事")
        parseSection(relatedSection, "相关剧情")

        // 批量获取封面图 URL
        if (entries.isNotEmpty()) {
            val imageUrls = fetchImageUrls(entries.map { it.imageFileName })
            return entries.map { entry ->
                entry.copy(imageUrl = imageUrls[entry.imageFileName])
            }
        }
        return entries
    }

    /** 提取 === 标题 === 下的内容，直到下一个 === 或文末 */
    private fun extractSection(wikitext: String, heading: String): String? {
        val pattern = Regex("===\\s*" + Regex.escape(heading) + "\\s*===")
        val match = pattern.find(wikitext) ?: return null
        val start = match.range.last + 1
        val nextHeading = Regex("===\\s*[^=]+\\s*===").find(wikitext, start)
        val end = nextHeading?.range?.first ?: wikitext.length
        return wikitext.substring(start, end)
    }

    /** 批量获取图片 URL（最多 50 个） */
    private fun fetchImageUrls(fileNames: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val titles = fileNames.joinToString("|") { "文件:$it" }
            val encoded = URLEncoder.encode(titles, "UTF-8")
            val url = "$API?action=query&titles=$encoded&prop=imageinfo&iiprop=url&format=json"
            val body = WikiEngine.safeGet(url) ?: return result
            val json = SharedJson.parseToJsonElement(body).jsonObject
            json["query"]?.jsonObject?.get("pages")?.jsonObject?.values?.forEach { page ->
                val pageObj = page.jsonObject
                val pageTitle = pageObj["title"]?.jsonPrimitive?.content ?: return@forEach
                val imageUrl = pageObj["imageinfo"]
                    ?.let { it as? JsonArray }
                    ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                if (imageUrl != null) {
                    // 去掉 "文件:" 前缀还原文件名
                    val fileName = pageTitle.removePrefix("文件:")
                    result[fileName] = imageUrl
                }
            }
        } catch (_: Exception) { }
        return result
    }

    /**
     * 提取指定名称的模板内容（处理嵌套大括号）。
     */
    private fun extractTemplate(wikitext: String, templateName: String): String? {
        val startMarker = "{{$templateName"
        var searchFrom = 0
        while (true) {
            val startIdx = wikitext.indexOf(startMarker, searchFrom)
            if (startIdx == -1) return null
            // 确保模板名后紧跟 | 或 \n 或 }}，避免前缀误匹配（如 "角色技能" 匹配 "角色技能/数值"）
            val afterIdx = startIdx + startMarker.length
            if (afterIdx < wikitext.length) {
                val nextChar = wikitext[afterIdx]
                if (nextChar != '|' && nextChar != '\n' && nextChar != '}' && nextChar != '\r') {
                    searchFrom = afterIdx
                    continue
                }
            }

            var depth = 0
            var i = startIdx
            while (i < wikitext.length - 1) {
                if (wikitext[i] == '{' && wikitext[i + 1] == '{') {
                    depth++; i += 2
                } else if (wikitext[i] == '}' && wikitext[i + 1] == '}') {
                    depth--
                    if (depth == 0) {
                        return wikitext.substring(startIdx + startMarker.length, i).trimStart()
                    }
                    i += 2
                } else {
                    i++
                }
            }
            return null
        }
    }

    /**
     * 解析模板参数 `|key=value` 对。
     * 跳过包含嵌套模板的值中的 `|`。
     */
    private fun parseTemplateParams(content: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val parts = splitTemplateParams(content)
        for (part in parts) {
            val eqIdx = part.indexOf('=')
            if (eqIdx > 0) {
                val key = part.substring(0, eqIdx).trim()
                val value = part.substring(eqIdx + 1).trim()
                if (key.isNotEmpty()) {
                    params[key] = value
                }
            }
        }
        return params
    }

    /**
     * 按 `|` 分割模板参数，但跳过嵌套 `{{...}}` 内的 `|`。
     */
    private fun splitTemplateParams(content: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (i in content.indices) {
            val c = content[i]
            if (i < content.length - 1 && c == '{' && content[i + 1] == '{') {
                depth++
                current.append(c)
            } else if (i < content.length - 1 && c == '}' && content[i + 1] == '}') {
                depth--
                current.append(c)
            } else if (c == '|' && depth == 0) {
                parts.add(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    /**
     * 从 {{页顶导航|...}} 解析子页面列表。
     */
    private fun parseSubPages(navContent: String): List<SubPage> {
        val pages = mutableListOf<SubPage>()
        navContent.split("|").forEach { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx > 0) {
                val pageTitle = part.substring(0, eqIdx).trim()
                val displayName = part.substring(eqIdx + 1).trim()
                if (pageTitle.isNotBlank() && displayName.isNotBlank()) {
                    val encoded = URLEncoder.encode(pageTitle, "UTF-8").replace("+", "%20")
                    pages.add(
                        SubPage(
                            title = pageTitle,
                            displayName = displayName,
                            wikiUrl = "$WIKI_BASE/klbq/$encoded"
                        )
                    )
                }
            }
        }
        return pages
    }

    /**
     * 获取角色头像 URL。
     */
    private fun fetchAvatarUrl(characterName: String): String? {
        return try {
            val fileTitle = URLEncoder.encode("文件:${characterName}头像.png", "UTF-8")
            val url = "$API?action=query&titles=$fileTitle&prop=imageinfo&iiprop=url&format=json"
            val body = WikiEngine.safeGet(url) ?: return null
            val json = SharedJson.parseToJsonElement(body).jsonObject
            json["query"]?.jsonObject?.get("pages")?.jsonObject?.values
                ?.firstOrNull()?.jsonObject?.get("imageinfo")
                ?.let { it as? JsonArray }
                ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    // ────────────────────────────────────────
    //  更新改动历史解析（从渲染 HTML）
    // ────────────────────────────────────────

    /**
     * 从渲染后的 HTML 中解析更新改动历史。
     * 结构：`<div class="alert alert-warning">日期</div>` 后跟 `<ul><li>...</li></ul>`
     */
    private fun parseUpdateHistory(html: String): List<UpdateEntry> {
        val document = Jsoup.parse(html)
        val headline = document.getElementById("更新改动历史") ?: return emptyList()
        val sectionStart = headline.parent()?.takeIf { it.tagName().matches(Regex("h[1-6]")) } ?: headline
        val sectionElements = generateSequence(sectionStart.nextElementSibling()) { it.nextElementSibling() }
            .takeWhile { !it.tagName().matches(Regex("h[1-6]")) }
            .toList()
        if (sectionElements.isEmpty()) return emptyList()

        val wrapper = Jsoup.parse("<div id='update-history-wrapper'></div>")
        val container = wrapper.getElementById("update-history-wrapper") ?: return emptyList()
        sectionElements.forEach { container.appendChild(it.clone()) }

        val entries = mutableListOf<UpdateEntry>()
        var currentDate = ""
        var currentChanges = mutableListOf<String>()

        fun flushEntry() {
            if (currentDate.isNotBlank() && currentChanges.isNotEmpty()) {
                entries += UpdateEntry(date = currentDate, changes = currentChanges.toList())
            }
            currentDate = ""
            currentChanges = mutableListOf()
        }

        container.getAllElements()
            .drop(1)
            .forEach { element ->
                when {
                    element.tagName() == "div" && element.hasClass("alert") && element.hasClass("alert-warning") -> {
                        flushEntry()
                        currentDate = cleanHtml(element.html())
                    }

                    element.tagName() == "li" && currentDate.isNotBlank() -> {
                        cleanHtml(element.html())
                            .takeIf { it.isNotBlank() }
                            ?.let { currentChanges += it }
                    }
                }
            }

        flushEntry()
        return entries
    }

}
