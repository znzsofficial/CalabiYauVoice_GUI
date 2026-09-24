package com.nekolaska.calabiyau.feature.weapon.list

import com.nekolaska.calabiyau.core.wiki.fetchBatchImageUrls
import kotlinx.coroutines.runBlocking
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
            listOf("武器-忍锋.png", "忍锋-weapon.png"),
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

    @Test
    fun fetchBatchImageUrlsResolvesRedirectsToOriginalRequestedNames() = runBlocking {
        val mockApiResponse = """
            {
              "query": {
                "redirects": [
                  {"from": "文件:武器-大剑.png", "to": "文件:武器外观图鉴 15701001.png"},
                  {"from": "文件:武器-战镰.png", "to": "文件:武器外观图鉴 15401001.png"}
                ],
                "pages": {
                  "101": {
                    "pageid": 101,
                    "title": "文件:武器外观图鉴 15701001.png",
                    "imageinfo": [{"url": "https://patchwiki.biligame.com/dajian.png"}]
                  },
                  "102": {
                    "pageid": 102,
                    "title": "文件:武器外观图鉴 15401001.png",
                    "imageinfo": [{"url": "https://patchwiki.biligame.com/zhanlian.png"}]
                  }
                }
              }
            }
        """.trimIndent()

        val result = fetchBatchImageUrls(listOf("武器-大剑.png", "武器-战镰.png")) { _ ->
            mockApiResponse
        }

        // 调用方传的是 "武器-大剑.png" 和 "武器-战镰.png"，应当能直接取到重定向目标的 URL
        assertEquals("https://patchwiki.biligame.com/dajian.png", result["武器-大剑.png"])
        assertEquals("https://patchwiki.biligame.com/zhanlian.png", result["武器-战镰.png"])
        // 同时目标文件名也能取到
        assertEquals("https://patchwiki.biligame.com/dajian.png", result["武器外观图鉴 15701001.png"])
    }
}
