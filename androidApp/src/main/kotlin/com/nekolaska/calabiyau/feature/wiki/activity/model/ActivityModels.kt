package com.nekolaska.calabiyau.feature.wiki.activity.model

data class ActivityEntry(
    val title: String,
    val startTime: String,
    val endTime: String,
    val description: String,
    val imageUrl: String? = null,
    val wikiUrl: String = ACTIVITY_PAGE_URL
)

const val ACTIVITY_PAGE_URL = "https://wiki.biligame.com/klbq/%E6%B4%BB%E5%8A%A8"
