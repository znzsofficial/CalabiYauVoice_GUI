package com.nekolaska.calabiyau.feature.wiki.navigation.parser

import com.nekolaska.calabiyau.feature.wiki.navigation.model.NavItem
import com.nekolaska.calabiyau.feature.wiki.navigation.model.NavSection
import java.net.URLEncoder

object NavigationMenuParsers {

    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"

    private val targetSections = listOf("首页", "角色", "武器", "地图", "玩法", "其他")

    fun parseSidebar(raw: String): List<NavSection> {
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
                depthStack.keys.filter { it > depth }.toList().forEach { depthStack.remove(it) }
            }

        return targetSections.mapNotNull { title ->
            val root = sectionRoots[title] ?: return@mapNotNull null
            NavSection(title = root.title, items = root.children.map { it.toImmutable() })
        }
    }

    private data class MutableNavNode(
        val title: String,
        val url: String?,
        val children: MutableList<MutableNavNode> = mutableListOf()
    )

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
