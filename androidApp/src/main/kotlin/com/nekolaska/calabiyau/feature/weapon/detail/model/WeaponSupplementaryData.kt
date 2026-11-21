package com.nekolaska.calabiyau.feature.weapon.detail.model

data class WeaponDataValue(val label: String, val value: String)

data class WeaponDataSection(val title: String, val values: List<WeaponDataValue>, val notice: String = "")

/** The rendered raster preview, not the original SVG. */
data class WeaponDiagram(val title: String, val previewUrl: String, val aspectRatio: Float)

data class WeaponAugmentOption(val title: String, val energyCost: Int?, val changes: String)
data class WeaponAugmentGroup(val title: String, val options: List<WeaponAugmentOption>)
data class WeaponAugmentMode(val mode: String, val groups: List<WeaponAugmentGroup>)

data class WeaponDamageCoefficients(
    val baseDamage: String,
    val head: String,
    val upper: String,
    val lower: String
)
