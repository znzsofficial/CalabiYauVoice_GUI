package com.nekolaska.calabiyau.data

import data.ApiResult
import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * Wiki 导航菜单提取 API（Android）。
 *
 * 数据来源：MediaWiki allmessages.sidebar
 * 接口示例：
 * https://wiki.biligame.com/klbq/api.php?action=query&meta=allmessages&ammessages=sidebar&format=json
 */
object NavigationMenuApi {

    private const val API = "https://wiki.biligame.com/klbq/api.php"
    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    private val targetSections = listOf("首页", "角色", "武器", "地图", "玩法", "其他")

    data class NavSection(
        val title: String,
        val items: List<NavItem>
    )

    data class NavItem(
        val title: String,
        val url: String?,
        val children: List<NavItem> = emptyList()
    )


    suspend fun fetchNavigationSections(): ApiResult<List<NavSection>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API?action=query&meta=allmessages&ammessages=sidebar&format=json"
            val (body, _) = OfflineCache.fetchWithCache(
                type = OfflineCache.Type.NAVIGATION,
                key = "sidebar"
            ) { WikiEngine.safeGet(url) }
                ?: return@withContext ApiResult.Error("请求导航数据失败，且无离线缓存")
            val root = SharedJson.parseToJsonElement(body).jsonObject
            val sidebar = root["query"]
                ?.jsonObject?.get("allmessages")
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("*")
                ?.jsonPrimitive?.content
                ?: return@withContext ApiResult.Error("未找到 sidebar 数据")

            val sections = parseSidebar(sidebar)
            if (sections.isEmpty()) {
                ApiResult.Error("导航菜单为空")
            } else {
                ApiResult.Success(sections)
            }
        } catch (e: Exception) {
            ApiResult.Error("解析导航失败: ${e.message}")
        }
    }

    private data class MutableNavNode(
        val title: String,
        val url: String?,
        val children: MutableList<MutableNavNode> = mutableListOf()
    )

    private fun parseSidebar(raw: String): List<NavSection> {
        val sectionRoots = linkedMapOf<String, MutableNavNode>()
        val depthStack = mutableMapOf<Int, MutableNavNode>()

        raw.lineSequence()
            .map { it.trimEnd() }
            .filter { it.startsWith("*") }
            .forEach { line ->
                val depth = line.takeWhile { it == '*' }.length
                val payload = line.drop(depth).trim()
                if (payload.isBlank()) return@forEach

                val (target, display) = parseTargetAndDisplay(payload)
                val pageUrl = target?.let(::toWikiUrl)

                if (depth == 1) {
                    val sectionTitle = normalizeSectionTitle(display)
                    if (sectionTitle != null) {
                        val node = MutableNavNode(sectionTitle, null)
                        sectionRoots[sectionTitle] = node
                        depthStack.clear()
                        depthStack[1] = node
                    } else {
                        depthStack.clear()
                    }
                    return@forEach
                }

                val parent = depthStack[depth - 1] ?: return@forEach
                val child = MutableNavNode(display, pageUrl)
                parent.children += child
                depthStack[depth] = child
                // 清理更深层缓存，避免跨分支串层
                depthStack.keys.filter { it > depth }.toList().forEach { depthStack.remove(it) }
            }

        return targetSections.mapNotNull { title ->
            val root = sectionRoots[title] ?: return@mapNotNull null
            NavSection(title = root.title, items = root.children.map { it.toImmutable() })
        }
    }

    private fun parseTargetAndDisplay(payload: String): Pair<String?, String> {
        val parts = payload.split("|", limit = 2)
        return if (parts.size == 2) {
            val target = parts[0].trim().takeIf { it.isNotBlank() }
            val display = parts[1].trim().ifBlank { parts[0].trim() }
            target to display
        } else {
            val text = payload.trim()
            text to text
        }
    }

    private fun normalizeSectionTitle(title: String): String? {
        val t = title.trim()
        return targetSections.firstOrNull { it == t }
    }

    private fun toWikiUrl(target: String): String {
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target
        }
        val encoded = URLEncoder.encode(target, "UTF-8")
            .replace("+", "%20")
        return "$WIKI_BASE$encoded"
    }

    private fun MutableNavNode.toImmutable(): NavItem {
        return NavItem(
            title = title,
            url = url,
            children = children.map { it.toImmutable() }
        )
    }

}
