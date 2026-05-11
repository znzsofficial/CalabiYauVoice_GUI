package com.nekolaska.calabiyau.feature.wiki.item.model

enum class Quality(val level: Int, val displayName: String) {
    EXQUISITE(2, "精致"),
    SUPERIOR(3, "卓越"),
    PERFECT(4, "完美"),
    LEGENDARY(5, "传说");

    companion object {
        fun fromLevel(level: String): Quality? = level.toIntOrNull()?.let { value ->
            entries.find { it.level == value }
        }
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
