package com.nekolaska.calabiyau.feature.wiki.mechanics.model

import com.nekolaska.calabiyau.core.wiki.reading.GuideText
import com.nekolaska.calabiyau.core.wiki.reading.GuideField

enum class MechanicsKind(val pageName: String) {
    STRINGIFY("弦化"), ROLES("超弦体定位"), AUGMENTATION("弦能增幅网络");
    val url get() = "https://wiki.biligame.com/klbq/$pageName"
    val cacheKey get() = "mechanics_${name.lowercase()}_v1"
}

sealed interface MechanicsBlock {
    val id: String
    data class Text(override val id: String, val content: GuideText, val depth: Int = 0, val marker: String? = null) : MechanicsBlock
    data class Heading(override val id: String, val title: String) : MechanicsBlock
    data class Image(override val id: String, val label: String, val thumbnail: String?, val fileUrl: String?) : MechanicsBlock
    data class Video(override val id: String, val url: String, val label: String) : MechanicsBlock
    data class Character(override val id: String, val name: String, val label: String, val portrait: String?) : MechanicsBlock
    data class Record(override val id: String, val title: GuideText, val fields: List<GuideField>) : MechanicsBlock
}
data class MechanicsSection(val id: String, val title: String, val blocks: List<MechanicsBlock>)
data class MechanicsPage(val sections: List<MechanicsSection> = emptyList()) {
    fun isEmpty() = sections.isEmpty()
}
data class MechanicsParseResult(val page: MechanicsPage, val issues: List<String>) {
    val canCache get() = !page.isEmpty() && issues.isEmpty()
}
