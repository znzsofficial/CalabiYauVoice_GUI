package com.nekolaska.calabiyau.feature.wiki.balance.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BalanceDataParsersTest {

    @Test
    fun buildsPayloadAndParsesSettings() {
        val payload = BalanceDataParsers.buildBalancePayload(
            chartId = "1",
            ideToken = "token",
            modeCode = "boom",
            mapCode = "404",
            rankCodes = listOf("gold", "diamond"),
            season1Code = "s1",
            season2Code = "s2"
        )
        assertTrue(payload.contains("\"mode\":\"boom\""))
        assertTrue(payload.contains("\"rank\":[\"gold\",\"diamond\"]"))

        val settings = BalanceDataParsers.parseSettings(
            """
            {
              "code": 0,
              "data": {
                "value": {
                  "setting": {
                    "mode": [{"content": "{\"code\":\"boom\",\"name\":\"爆破\"}"}],
                    "map": [{"content": "{\"code\":\"404\",\"name\":\"404基地\"}"}],
                    "rank": [{"content": "{\"code\":\"gold\",\"name\":\"黄金\"}"}],
                    "season": [{"content": "{\"code\":\"s1\",\"name\":\"S1\"}"}]
                  },
                  "role_list": {
                    "position": [{"content": "{\"position_code\":\"duel\",\"position_name\":\"对枪\",\"position_image\":\"https://x/p.png\"}"}],
                    "role_list": [{"content": "{\"character_code\":\"xin\",\"character_name\":\"信\",\"position_code\":\"duel\",\"camp_code\":1,\"character_image\":\"https://x/c.png\"}"}]
                  }
                }
              }
            }
            """.trimIndent()
        )

        assertEquals("爆破", settings.modes.single().name)
        assertEquals("对枪", settings.positions.single().name)
        assertEquals("信", settings.characters.single().name)
    }

    @Test
    fun parseBalanceResultReadsSidesAndCompareMap() {
        val result = BalanceDataParsers.parseBalanceResult(
            """
            {
              "jData": {
                "iRet": "0",
                "data1": {
                  "side1": [{"id":1,"heroName":"信","winRate":0.5,"selectRate":0.2,"kd":1.1,"damageAve":100,"score":80}],
                  "side2": [{"id":2,"heroName":"明","winRate":0.4}]
                },
                "data2": {
                  "side1": {"1": {"id":1,"heroName":"信","winRate":0.6}},
                  "side2": [{"id":2,"heroName":"明","winRate":0.3}]
                }
              }
            }
            """.trimIndent()
        )

        assertEquals("信", result.attackers.single().heroName)
        assertEquals("明", result.defenders.single().heroName)
        assertEquals(0.6, result.compareData?.attackers?.getValue(1)?.winRate)
        assertEquals("明", result.compareData?.defenders?.getValue(2)?.heroName)
    }

    @Test
    fun parseSettingsThrowsOnServerError() {
        assertFailsWith<IllegalStateException> {
            BalanceDataParsers.parseSettings("""{"code":1,"msg":"失败"}""")
        }
    }
}
