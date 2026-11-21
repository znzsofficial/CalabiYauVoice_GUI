package com.nekolaska.calabiyau.feature.wiki.beginner.model

import com.nekolaska.calabiyau.core.wiki.reading.GuideText
import com.nekolaska.calabiyau.core.wiki.reading.GuideField

const val BEGINNER_GUIDE_PAGE = "新人入坑指南"
const val BEGINNER_GUIDE_URL = "https://wiki.biligame.com/klbq/新人入坑指南"

sealed interface GuideBlock {
    val id: String
    data class Heading(override val id: String, val title: String) : GuideBlock
    data class Term(override val id: String, val content: GuideText) : GuideBlock
    data class Paragraph(
        override val id: String, val content: GuideText, val depth: Int = 0,
        val marker: String? = null, val notice: Boolean = false
    ) : GuideBlock
    data class Record(override val id: String, val title: GuideText, val fields: List<GuideField>) : GuideBlock
}

data class GuideSection(val id: String, val title: String, val blocks: List<GuideBlock>)
data class BeginnerGuidePage(val sections: List<GuideSection> = emptyList()) {
    fun isEmpty(): Boolean = sections.isEmpty()
}
data class BeginnerGuideParseResult(val page: BeginnerGuidePage, val issues: List<String>) {
    val canCache: Boolean get() = issues.isEmpty() && !page.isEmpty()
}
