package com.nekolaska.calabiyau.feature.wiki.submission.model

const val SUBMISSION_PAGE_NAME = "投稿作品"
const val SUBMISSION_PAGE_URL = "https://wiki.biligame.com/klbq/%E6%8A%95%E7%A8%BF%E4%BD%9C%E5%93%81"

data class SubmissionPage(
    val entries: List<SubmissionEntry>
)

data class SubmissionEntry(
    val title: String,
    val date: String,
    val author: String,
    val type: String,
    val topic: String,
    val wikiUrl: String
)
