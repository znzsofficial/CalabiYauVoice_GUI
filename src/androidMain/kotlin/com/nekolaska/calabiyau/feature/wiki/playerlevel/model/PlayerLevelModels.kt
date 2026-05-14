package com.nekolaska.calabiyau.feature.wiki.playerlevel.model

const val PLAYER_LEVEL_PAGE_NAME = "玩家等级"
const val PLAYER_LEVEL_PAGE_URL = "https://wiki.biligame.com/klbq/%E7%8E%A9%E5%AE%B6%E7%AD%89%E7%BA%A7"

data class PlayerLevelPage(
    val title: String,
    val wikiUrl: String,
    val intro: String,
    val levels: List<PlayerLevelEntry>,
    val rewards: List<PlayerLevelReward>,
    val note: String?
)

data class PlayerLevelEntry(
    val level: Int,
    val requiredExp: Int?,
    val frameImageUrl: String?,
    val frameName: String?
)

data class PlayerLevelReward(
    val level: Int,
    val items: List<PlayerLevelRewardItem>,
    val weapons: List<PlayerLevelWeapon>
)

data class PlayerLevelRewardItem(
    val name: String,
    val count: Int?,
    val quality: Int?,
    val iconUrl: String?
)

data class PlayerLevelWeapon(
    val name: String,
    val wikiUrl: String?
)
