package com.nekolaska.calabiyau.feature.wiki.activity.model

data class ActivityEntry(
    val title: String,
    val startTime: String,
    val endTime: String,
    val description: String,
    val imageUrl: String? = null,
    val wikiUrl: String = ACTIVITY_PAGE_URL,
    val detailPageTitle: String? = null
)

internal sealed interface ActivityDetailBlock {
    data class Heading(val text: String) : ActivityDetailBlock
    data class Paragraph(val text: String) : ActivityDetailBlock
    data class TableRow(val cells: List<String>, val isHeader: Boolean) : ActivityDetailBlock
    data class Image(val label: String, val url: String) : ActivityDetailBlock
    /** A populated Wiki component whose interaction or table layout is part of its meaning. */
    data class WikiSection(val title: String, val anchor: String) : ActivityDetailBlock
}

const val ACTIVITY_PAGE_URL = "https://wiki.biligame.com/klbq/%E6%B4%BB%E5%8A%A8"
