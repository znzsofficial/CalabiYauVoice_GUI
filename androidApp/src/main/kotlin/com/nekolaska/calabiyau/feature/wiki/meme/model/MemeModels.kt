package com.nekolaska.calabiyau.feature.wiki.meme.model

const val MEME_PAGE_NAME = "梗百科"
const val MEME_PAGE_URL = "https://wiki.biligame.com/klbq/%E6%A2%97%E7%99%BE%E7%A7%91"

data class MemePage(
    val officialIssues: List<MemeOfficialIssue>,
    val editorEntries: List<MemeEntry>
)

data class MemeOfficialIssue(
    val title: String,
    val imageUrl: String?
)

data class MemeEntry(
    val title: String,
    val description: String
)
