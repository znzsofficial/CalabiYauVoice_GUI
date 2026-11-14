package com.nekolaska.calabiyau.feature.wiki.gamesettings.model

const val PC_GAME_SETTINGS_PAGE_NAME = "游戏设置PC端"
const val PC_GAME_SETTINGS_PAGE_URL = "https://wiki.biligame.com/klbq/游戏设置PC端"

enum class PcSettingsView(val label: String) {
    SETTINGS("设置详解"), RECOMMENDATIONS("推荐速查"), KNOWLEDGE("原理科普")
}

data class PcSettingField(val label: String, val value: String)

sealed interface PcSettingsBlock {
    val id: String
    val subsection: String

    data class Text(
        override val id: String,
        override val subsection: String,
        val text: String,
        val listDepth: Int = 0
    ) : PcSettingsBlock

    data class Setting(
        override val id: String,
        override val subsection: String,
        val name: String,
        val fields: List<PcSettingField>,
        val recommendationCategory: String = "",
        val isQuickReference: Boolean = false
    ) : PcSettingsBlock {
        fun field(label: String): String? = fields.firstOrNull { it.label == label }?.value
    }

    /** Explanatory table rows are reference data, not configurable settings or recommendations. */
    data class Reference(
        override val id: String,
        override val subsection: String,
        val nameLabel: String,
        val name: String,
        val fields: List<PcSettingField>
    ) : PcSettingsBlock
}

data class PcSettingsSection(
    val id: String,
    val title: String,
    val view: PcSettingsView,
    val blocks: List<PcSettingsBlock>
)

data class PcGameSettingsPage(val sections: List<PcSettingsSection> = emptyList()) {
    fun isEmpty(): Boolean = sections.all { it.blocks.isEmpty() }
    val settings: List<PcSettingsBlock.Setting>
        get() = sections.flatMap { it.blocks }.filterIsInstance<PcSettingsBlock.Setting>()
}

/** Filtering is local, preserves source identities and includes evidence from every displayed field. */
fun PcGameSettingsPage.filteredSections(
    view: PcSettingsView,
    sectionId: String = "",
    query: String = ""
): List<PcSettingsSection> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return sections.filter { it.view == view && (sectionId.isEmpty() || it.id == sectionId) }
        .mapNotNull { section ->
            val blocks = section.blocks.filter { block ->
                val body = when (block) {
                    is PcSettingsBlock.Text -> block.text
                    is PcSettingsBlock.Setting -> buildString {
                        append(block.name).append(' ').append(block.recommendationCategory)
                        block.fields.forEach { append(' ').append(it.label).append(' ').append(it.value) }
                    }
                    is PcSettingsBlock.Reference -> buildString {
                        append(block.nameLabel).append(' ').append(block.name)
                        block.fields.forEach { append(' ').append(it.label).append(' ').append(it.value) }
                    }
                }
                val searchable = "${section.title} ${block.subsection} $body"
                terms.all { searchable.contains(it, ignoreCase = true) }
            }
            section.copy(blocks = blocks).takeIf { blocks.isNotEmpty() }
        }
}
