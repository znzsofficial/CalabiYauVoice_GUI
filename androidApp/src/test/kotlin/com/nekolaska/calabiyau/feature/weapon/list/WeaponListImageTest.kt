package com.nekolaska.calabiyau.feature.weapon.list

import kotlin.test.Test
import kotlin.test.assertEquals

class WeaponListImageTest {

    @Test
    fun primaryWeaponUsesWeaponNameBeforeLegacyUserName() {
        val weapon = WeaponListApi.WeaponInfo(
            name = "北极星",
            user = "星绘",
            type = "自动步枪",
            description = "",
            wikiUrl = "",
            imageUrl = null
        )

        assertEquals(
            listOf("北极星-weapon.png", "星绘-weapon.png"),
            weaponImageFileNames(weapon, WeaponListApi.WeaponCategory.PRIMARY)
        )
    }

    @Test
    fun nonPrimaryWeaponUsesWeaponPrefix() {
        val weapon = WeaponListApi.WeaponInfo(
            name = "忍锋",
            user = "",
            type = "近战武器",
            description = "",
            wikiUrl = "",
            imageUrl = null
        )

        assertEquals(
            listOf("武器-忍锋.png"),
            weaponImageFileNames(weapon, WeaponListApi.WeaponCategory.MELEE)
        )
    }
}
