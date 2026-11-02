package com.nekolaska.calabiyau.feature.weapon.detail.model

data class WeaponDataValue(val label: String, val value: String)

data class WeaponDataSection(val title: String, val values: List<WeaponDataValue>, val notice: String = "")

data class WeaponDamageCoefficients(
    val baseDamage: String,
    val head: String,
    val upper: String,
    val lower: String
)
