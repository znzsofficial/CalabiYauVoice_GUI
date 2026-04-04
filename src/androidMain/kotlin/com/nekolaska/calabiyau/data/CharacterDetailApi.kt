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
        val role: String,           // 定位
        val faction: String,        // 阵营
        val summary: String,        // 角色简介
        val identity: String,       // 身份
        val height: String,
        val weight: String,
        val birthday: String,
        val age: String,
        val cnVoiceActor: String,   // 中文声优
        val jpVoiceActor: String,   // 日文声优
        val weaponName: String,
        val weaponType: String,
        val weaponIntro: String,
        val traits: String,         // 超弦体特性
        val hobbies: String,        // 兴趣爱好
        val diet: String,           // 饮食习惯
        val quote: String,          // 个性语录
        val description: String,    // 简介
        val observerQuote: String,  // 观测语录
        val avatarUrl: String?,     // 头像图片 URL
        val subPages: List<SubPage> // 子页面（语音、誓约、画廊等）
    )

    data class SubPage(
        val title: String,
        val displayName: String,
        val wikiUrl: String
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

        return CharacterDetail(
            name = name,
            englishName = params["英文名"] ?: "",
            japaneseName = params["日文名"] ?: "",
            gender = params["性别"] ?: "",
            role = params["定位"] ?: "",
            faction = params["阵营"] ?: "",
            summary = params["角色简介"] ?: "",
            identity = params["身份"] ?: "",
            height = params["身高"] ?: "",
            weight = params["体重"] ?: "",
            birthday = params["生日"] ?: "",
            age = params["年龄"] ?: "",
            cnVoiceActor = params["中文声优"] ?: "",
            jpVoiceActor = params["日文声优"] ?: "",
            weaponName = params["武器名称"] ?: "",
            weaponType = params["武器类型"] ?: "",
            weaponIntro = params["武器介绍"] ?: "",
            traits = params["超弦体特性"] ?: "",
            hobbies = params["兴趣爱好"] ?: "",
            diet = params["饮食习惯"] ?: "",
            quote = params["个性语录"] ?: "",
            description = params["简介"] ?: "",
            observerQuote = params["观测语录"] ?: "",
            avatarUrl = avatarUrl,
            subPages = subPages
        )
    }

    /**
     * 提取指定名称的模板内容（处理嵌套大括号）。
     */
    private fun extractTemplate(wikitext: String, templateName: String): String? {
        val startMarker = "{{$templateName"
        val startIdx = wikitext.indexOf(startMarker)
        if (startIdx == -1) return null

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
