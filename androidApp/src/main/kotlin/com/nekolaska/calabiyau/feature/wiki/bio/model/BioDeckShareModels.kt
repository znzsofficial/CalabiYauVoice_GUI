package com.nekolaska.calabiyau.feature.wiki.bio.model

data class DeckCardOption(
    val name: String,
    val cardId: String,
    val quality: String,
    val isDefault: Boolean,
    val index: Int
) {
    val normalizedQuality: String
        get() = normalizeDeckQuality(quality)
}

data class SubmitDeckPayload(
    val deckName: String,
    val author: String,
    val intro: String,
    val faction: String,
    val selectedCardIds: List<String>
)

data class SubmitDeckResult(
    val title: String,
    val isPending: Boolean
)

fun normalizeDeckQuality(raw: String): String = when {
    raw.contains("完") -> "完美"
    raw.contains("卓") -> "卓越"
    raw.contains("精") -> "精致"
    else -> raw
}
