package com.nekolaska.calabiyau.feature.weapon.skin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeaponSkinFilterApiTest {

    @Test
    fun parsesLegacyDivsortRows() {
        val skins = WeaponSkinFilterApi.parseWeaponSkinHtml(
            html = """
            |- class="divsort" data-param1="独舞" data-param2="4" data-param3="限时商城" data-param4="480" data-param5="无"
            | <img alt="武器外观 1.png" src="https://patchwiki.biligame.com/images/klbq/thumb/a/ab/a.png/70px-a.png" srcset="https://patchwiki.biligame.com/images/klbq/a/ab/a.png 1.5x"/><br /><a>独舞</a>：回味时光
            | 这是一段足够长的武器外观介绍文字用于解析
            """.trimIndent(),
            weaponMeta = mapOf("独舞" to WeaponSkinFilterApi.WeaponMeta("主武器", "微型冲锋枪"))
        )

        val skin = skins.single()
        assertEquals("独舞：回味时光", skin.name)
        assertEquals("独舞", skin.weapon)
        assertEquals("主武器", skin.weaponCategory)
        assertEquals("微型冲锋枪", skin.weaponType)
        assertEquals(WeaponSkinFilterApi.Quality.PERFECT, skin.quality)
        assertEquals(listOf("限时商城"), skin.sources)
        assertEquals("480", skin.crystalCost)
        assertEquals("", skin.baseCost)
        assertEquals("https://patchwiki.biligame.com/images/klbq/a/ab/a.png", skin.fullImageUrl)
        assertTrue(skin.description.contains("武器外观介绍"))
    }
}
