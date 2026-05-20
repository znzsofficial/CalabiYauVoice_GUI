package com.nekolaska.calabiyau.feature.wiki.collaboration.model

const val COLLABORATION_PAGE_NAME = "联动"
const val COLLABORATION_PAGE_URL = "https://wiki.biligame.com/klbq/%E8%81%94%E5%8A%A8"

data class CollaborationPage(
    val timelineYears: List<CollaborationTimelineYear>,
    val events: List<CollaborationEvent>
)

data class CollaborationTimelineYear(
    val year: String,
    val items: List<CollaborationTimelineItem>
)

data class CollaborationTimelineItem(
    val date: String,
    val title: String
)

data class CollaborationEvent(
    val title: String,
    val sectionTitle: String?,
    val publishInfo: String?,
    val date: String?,
    val theme: String?,
    val content: String,
    val imageUrls: List<String>
)
