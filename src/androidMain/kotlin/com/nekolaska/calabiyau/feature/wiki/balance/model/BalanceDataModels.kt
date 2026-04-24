package com.nekolaska.calabiyau.feature.wiki.balance.model

/** 筛选选项（code + 显示名） */
data class FilterOption(val code: String, val name: String)

/** 角色元信息 */
data class CharacterMeta(
    val code: String,
    val name: String,
    val positionCode: String,
    val campCode: Int,
    val imageUrl: String
)

/** 职业元信息 */
data class PositionMeta(
    val code: String,
    val name: String,
    val imageUrl: String
)

/** 设置选项集合 */
data class BalanceSettings(
    val modes: List<FilterOption>,
    val maps: List<FilterOption>,
    val ranks: List<FilterOption>,
    val seasons: List<FilterOption>,
    val positions: List<PositionMeta>,
    val characters: List<CharacterMeta>
)

/** 单个角色的平衡数据 */
data class HeroBalanceData(
    val id: Int,
    val heroName: String,
    val winRate: Double,
    val selectRate: Double,
    val kd: Double,
    val damageAve: Double,
    val score: Double
)

/** 平衡数据结果（进攻方/防守方） */
data class BalanceResult(
    val attackers: List<HeroBalanceData>,
    val defenders: List<HeroBalanceData>
)
