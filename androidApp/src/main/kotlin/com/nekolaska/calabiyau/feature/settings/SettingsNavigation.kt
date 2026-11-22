package com.nekolaska.calabiyau.feature.settings

internal enum class SettingsPage(val title: String, val parent: SettingsPage? = null) {
    HOME("设置"),
    QUICK_ENTRIES("首页快捷入口", HOME),
    APPEARANCE("外观与壁纸", HOME),
    DOWNLOADS("下载与存储", HOME),
    WIKI("Wiki 与账号", HOME),
    ABOUT("关于与更新", HOME),
    COLORS("配色细节", APPEARANCE),
    WALLPAPER("壁纸", APPEARANCE),
    STORAGE("存储空间", DOWNLOADS);
}

internal class SettingsNavigation private constructor(private val pages: List<SettingsPage>) {
    constructor() : this(listOf(SettingsPage.HOME))

    val current get() = pages.last()
    val previous get() = pages.getOrNull(pages.lastIndex - 1)
    operator fun contains(page: SettingsPage): Boolean = page in pages

    // AnimatedContent keeps the outgoing page alive: its late callbacks cannot change the stack.
    fun open(from: SettingsPage, to: SettingsPage): SettingsNavigation =
        if (current == from && to.parent == from) SettingsNavigation(pages + to) else this

    fun pop(from: SettingsPage): SettingsNavigation =
        if (current == from && pages.size > 1) SettingsNavigation(pages.dropLast(1)) else this

    fun save(): List<String> = pages.map { it.name }

    companion object {
        fun restore(names: List<String>): SettingsNavigation {
            var path = SettingsNavigation()
            if (names.firstOrNull() == path.current.name) {
                for (name in names.drop(1)) {
                    val child = SettingsPage.entries.find { it.name == name && it.parent == path.current } ?: break
                    path = path.open(path.current, child)
                }
            }
            return path
        }
    }
}
