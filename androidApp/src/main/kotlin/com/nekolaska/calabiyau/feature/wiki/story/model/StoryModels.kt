package com.nekolaska.calabiyau.feature.wiki.story.model

const val STORY_PAGE_NAME = "剧情故事"
const val STORY_PAGE_URL = "https://wiki.biligame.com/klbq/%E5%89%A7%E6%83%85%E6%95%85%E4%BA%8B"

data class StoryEntry(
    val title: String,
    val url: String,
    val imageFileName: String? = null,
    val imageUrl: String? = null
)

data class StorySection(
    val title: String,
    val description: String? = null,
    val entries: List<StoryEntry>
)
