package com.nekolaska.calabiyau.feature.wiki.item.model

enum class Quality(val level: Int, val displayName: String) {
    EXQUISITE(2, "精致"),
    SUPERIOR(3, "卓越"),
    PERFECT(4, "完美"),
    LEGENDARY(5, "传说");

    companion object {
        fun fromLevel(level: Int): Quality? = entries.find { it.level == level }
        fun fromLevel(level: String): Quality? = level.toIntOrNull()?.let(::fromLevel)
    }
}

data class ItemInfo(
    val name: String,
    val category: String,
    val quality: Quality?,
    val qualityName: String,
    val description: String,
    val iconUrl: String?
)
