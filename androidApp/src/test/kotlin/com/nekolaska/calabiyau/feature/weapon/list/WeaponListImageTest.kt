package com.nekolaska.calabiyau.feature.weapon.list

import kotlin.test.Test
import kotlin.test.assertEquals

class WeaponListImageTest {
    @Test
    fun parsesAllUsersFromAskResponse() {
        val weapons = WeaponListApi.parseWeapons("""
            {"query":{"results":{"静风":{"printouts":{"使用者":[" 令 ","令",{"fulltext":"米雪儿·李"},null,""],"类型":["自动步枪"]}}}}}
        """)
        assertEquals(listOf("令", "米雪儿·李"), weapons.single().users)
    }

    @Test
    fun primaryWeaponUsesWeaponNameBeforeLegacyUserName() {
        val weapon = WeaponListApi.WeaponInfo(
            name = "北极星",
            users = listOf("星绘"),
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
            users = emptyList(),
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

    @Test
    fun universalWeaponDoesNotUseAnotherWeaponsImage() {
        val weapon = WeaponListApi.WeaponInfo(
            name = "静风",
            users = listOf("令", "芙拉薇娅", "米雪儿·李"),
            type = "自动步枪",
            description = "",
            wikiUrl = "",
            imageUrl = null
        )

        assertEquals(
            listOf("静风-weapon.png"),
            weaponImageFileNames(weapon, WeaponListApi.WeaponCategory.PRIMARY)
        )
        assertEquals("令 等 3 名角色", weapon.displayUsers)
    }
}
