package com.nekolaska.calabiyau.data

import data.ApiResult
import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val stories: List<StoryEntry>,
        val updateHistory: List<UpdateEntry> = emptyList()
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
    suspend fun fetchCharacterDetail(characterName: String): ApiResult<CharacterDetail> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(characterName, "UTF-8")
                val url = "$API?action=parse&page=$encoded&prop=wikitext|text&format=json"

                val (body, isOffline) = OfflineCache.fetchWithCache(
                    type = OfflineCache.Type.CHARACTER_DETAIL,
                    key = characterName
                ) { WikiEngine.safeGet(url) }
                    ?: return@withContext ApiResult.Error("请求失败，且无离线缓存")

                val json = SharedJson.parseToJsonElement(body).jsonObject
                val parseObj = json["parse"]?.jsonObject
                val wikitext = parseObj?.get("wikitext")
                    ?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                    ?: return@withContext ApiResult.Error("无法获取页面内容")

                val detail = parseCharacterWikitext(characterName, wikitext)
                    ?: return@withContext ApiResult.Error("未找到角色信息模板")

                // 从渲染 HTML 解析改动历史
                val html = parseObj["text"]
                    ?.jsonObject?.get("*")
                    ?.jsonPrimitive?.content
                val history = if (html != null) parseUpdateHistory(html) else emptyList()

                ApiResult.Success(detail.copy(updateHistory = history), isOffline = isOffline)
            } catch (e: Exception) {
                ApiResult.Error("获取角色详情失败: ${e.message}")
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
            role = if (isCrystal) "晶源体" else clean(params["定位"]),
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
            val body = WikiEngine.safeGet(url) ?: return null
            val json = SharedJson.parseToJsonElement(body).jsonObject
            json["query"]?.jsonObject?.get("pages")?.jsonObject?.values
                ?.firstOrNull()?.jsonObject?.get("imageinfo")
                ?.let { it as? kotlinx.serialization.json.JsonArray }
                ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    // ────────────────────────────────────────
    //  更新改动历史解析（从渲染 HTML）
    // ────────────────────────────────────────

    private val UPDATE_DATE_REGEX = Regex(
        """<div\s+class="alert\s+alert-warning"[^>]*>([^<]+)</div>"""
    )
    private val LI_REGEX = Regex("""<li>(.+?)</li>""", RegexOption.DOT_MATCHES_ALL)
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")

    /**
     * 从渲染后的 HTML 中解析更新改动历史。
     * 结构：`<div class="alert alert-warning">日期</div>` 后跟 `<ul><li>...</li></ul>`
     */
    private fun parseUpdateHistory(html: String): List<UpdateEntry> {
        // 定位到"更新改动历史"区域
        val sectionStart = html.indexOf("id=\"更新改动历史\"")
        if (sectionStart == -1) return emptyList()
        val sectionHtml = html.substring(sectionStart)

        val entries = mutableListOf<UpdateEntry>()
        val dateMatches = UPDATE_DATE_REGEX.findAll(sectionHtml).toList()

        for ((i, dateMatch) in dateMatches.withIndex()) {
            val date = dateMatch.groupValues[1].trim()
            // 取当前日期到下一个日期之间的内容
            val contentStart = dateMatch.range.last + 1
            val contentEnd = if (i + 1 < dateMatches.size) dateMatches[i + 1].range.first
            else sectionHtml.length
            val block = sectionHtml.substring(contentStart, contentEnd)

            val changes = LI_REGEX.findAll(block).map { li ->
                li.groupValues[1]
                    .replace(HTML_TAG_REGEX, "")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .trim()
            }.filter { it.isNotBlank() }.toList()

            if (changes.isNotEmpty()) {
                entries.add(UpdateEntry(date = date, changes = changes))
            }
        }
        return entries
    }

}
