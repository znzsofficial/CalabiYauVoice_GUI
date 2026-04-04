package com.nekolaska.calabiyau.data

import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
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
        val stories: List<StoryEntry>
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
        val videoUrl: String?
    )

    /** 角色故事/相关剧情条目 */
    data class StoryEntry(
        val title: String,
        val imageFileName: String,
        val pageUrl: String,
        val imageUrl: String?,
        val section: String
    )

    sealed interface ApiResult<out T> {
        data class Success<T>(val value: T) : ApiResult<T>
        data class Error(val message: String) : ApiResult<Nothing>
    }

    /**
     * 获取角色详情。
     * @param characterName 角色名（如"米雪儿·李"）
     */
    suspend fun fetchCharacterDetail(characterName: String): ApiResult<CharacterDetail> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(characterName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=wikitext&format=json"
                val body = httpGet(url) ?: return@withContext ApiResult.Error("请求失败")

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val wikitext = json["parse"]
                    ?.jsonObject?.get("wikitext")
                    ?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error("无法获取页面内容")

                val detail = parseCharacterWikitext(characterName, wikitext)
                    ?: return@withContext ApiResult.Error("未找到角色信息模板")

                ApiResult.Success(detail)
            } catch (e: Exception) {
                ApiResult.Error("获取角色详情失败: ${e.message}")
            }
        }

    /**
     * 从 wikitext 解析 `{{超弦体|...}}` 模板参数。
     */
    private fun parseCharacterWikitext(name: String, wikitext: String): CharacterDetail? {
        // 提取 {{超弦体 ... }} 模板块
        val templateContent = extractTemplate(wikitext, "超弦体") ?: return null

        // 解析模板参数
        val params = parseTemplateParams(templateContent)

        // 提取 {{页顶导航 ... }} 中的子页面
        val navContent = extractTemplate(wikitext, "页顶导航")
        val subPages = if (navContent != null) parseSubPages(navContent) else emptyList()

        // 获取头像 URL
        val avatarUrl = fetchAvatarUrl(name)

        // 解析技能
        val skills = parseSkills(wikitext)

        // 解析角色故事 & 相关剧情
        val stories = parseStories(wikitext)

        return CharacterDetail(
            name = name,
            englishName = clean(params["英文名"]),
            japaneseName = clean(params["日文名"]),
            gender = clean(params["性别"]),
            role = clean(params["定位"]),
            faction = clean(params["阵营"]),
            summary = clean(params["角色简介"]),
            identity = clean(params["身份"]),
            activeArea = clean(params["活动区域"]),
            height = clean(params["身高"]),
            weight = clean(params["体重"]),
            birthday = clean(params["生日"]),
            age = clean(params["年龄"]),
            cnVoiceActor = clean(params["中文声优"]),
            jpVoiceActor = clean(params["日文声优"]),
            weaponName = clean(params["武器名称"]),
            weaponType = clean(params["武器类型"]),
            weaponIntro = clean(params["武器介绍"]),
            traits = clean(params["超弦体特性"]),
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

    /** 清理 Wiki 标记 */
    private fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("\\{\\{黑幕\\|([^}]*?)\\}\\}"), "$1")       // {{黑幕|text}} → text
            .replace(Regex("\\{\\{#info:[^}]*?\\}\\}"), "")              // {{#info:...|note}} → 移除
            .replace(Regex("<br\\s*/?>\\n?"), "\n")                       // <br /> → 换行
            .replace(Regex("'''([^']*?)'''"), "$1")                       // '''粗体''' → 纯文本
            .replace(Regex("''([^']*?)''"), "$1")                         // ''斜体'' → 纯文本
            .replace(Regex("\\[\\[[^|\\]]*?\\|([^\\]]*?)\\]\\]"), "$1")   // [[链接|显示]] → 显示
            .replace(Regex("\\[\\[([^\\]]*?)\\]\\]"), "$1")              // [[链接]] → 链接
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

    private fun parseSkills(wikitext: String): List<SkillInfo> {
        val skillTemplate = extractTemplate(wikitext, "角色技能") ?: return emptyList()
        val params = parseTemplateParams(skillTemplate)
        return SKILL_SLOTS.mapNotNull { (num, slot, name) ->
            val desc = clean(params["技能${num}解析"])
            if (desc.isBlank()) return@mapNotNull null
            val videoRaw = params["技能${num}视频演示"] ?: ""
            val videoUrl = Regex("url=([^}]+)").find(videoRaw)?.groupValues?.get(1)?.trim()
            SkillInfo(slot = slot, name = name, description = desc, videoUrl = videoUrl)
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
            val body = httpGet(url) ?: return result
            val json = SharedJson.parseToJsonElement(body).jsonObject
            json["query"]?.jsonObject?.get("pages")?.jsonObject?.values?.forEach { page ->
                val pageObj = page.jsonObject
                val pageTitle = pageObj["title"]?.jsonPrimitive?.content ?: return@forEach
                val imageUrl = pageObj["imageinfo"]
                    ?.let { it as? kotlinx.serialization.json.JsonArray }
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
            val body = httpGet(url) ?: return null
            val json = SharedJson.parseToJsonElement(body).jsonObject
            json["query"]?.jsonObject?.get("pages")?.jsonObject?.values
                ?.firstOrNull()?.jsonObject?.get("imageinfo")
                ?.let { it as? kotlinx.serialization.json.JsonArray }
                ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String? {
        val request = Request.Builder().url(url).build()
        WikiEngine.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.string()
        }
    }
}
