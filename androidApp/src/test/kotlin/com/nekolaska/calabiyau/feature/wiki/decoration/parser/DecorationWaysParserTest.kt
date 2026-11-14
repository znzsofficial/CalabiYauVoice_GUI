package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecorationWaysParserTest {
    @Test
    fun stringsArraysRemarksAndExpiredPricesRemainReadable() {
        val result = DecorationWaysParser.parse("""return {
          [70001] = "挑战赛S7赛季大师段位奖励",
          [90008] = {"<del>限时商城-600晶核</del>", "喵蛙种子同捆套装960晶核", "限时商城-480晶核"},
          [1] = {get={"[[商城|商城入口]]", "[https://example.com 活动]"},remark="旧价<s>100</s><br>现价80"},
          [2] = {}
        }""")
        assertTrue(result.issues.isEmpty(), result.issues.toString())
        assertEquals("挑战赛S7赛季大师段位奖励", result.ways[70001])
        assertEquals("【已失效：限时商城-600晶核】\n喵蛙种子同捆套装960晶核\n限时商城-480晶核", result.ways[90008])
        assertEquals("商城入口\n活动\n备注：旧价【已失效：100】\n现价80", result.ways[1])
        assertEquals("", result.ways[2], "Explicit empty record differs from a missing ID")
    }

    @Test
    fun conflictingOrMalformedWaysCannotBecomeValidEmptyOverrides() {
        for (text in listOf("return {[1]={'甲'},[1]={'乙'}}", "return {[1]={false}}", "return {[1]={remark='缺get'}}")) {
            assertTrue(DecorationWaysParser.parse(text).issues.isNotEmpty())
        }
    }
}
