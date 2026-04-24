package com.nekolaska.calabiyau.feature.wiki.decoration.model

/** 单个装饰条目 */
data class DecorationItem(
    val id: Int,
    val name: String,
    val quality: Int,
    val description: String,
    val specialDescription: String,
    val source: String,
    val iconUrl: String,
    val imageUrl: String,
    val extraPreviews: List<DecorationPreview> = emptyList()
)

data class DecorationPreview(
    val label: String,
    val imageUrl: String
)

/** 按分类分组 */
data class DecorationSection(
    val title: String,
    val items: List<DecorationItem>
)
