package com.nekolaska.calabiyau.core.wiki

import com.nekolaska.calabiyau.core.wiki.LuaTableParser.LuaValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LuaTableParserTest {

    @Test
    fun parsesMixedArrayAndObjectEntries() {
        val values = LuaTableParser.parseReturnArray(
            """
            return {
              -- line comment
              { id = 101, name = "猫头", quality = 4, desc = "第一行<br />第二行",
                spdesc = [[特殊<br>说明]], get = { "商城", "活动" }, enabled = true },
              { ["name"] = "无id" },
              --[[ block comment ]]
              { id = 102, name = '单引号', quality = -1, extra = nil }
            }
            """.trimIndent()
        )

        assertEquals(3, values.size)
        val first = values[0] as LuaValue.LuaObject
        assertEquals(101, (first.fields["id"] as LuaValue.LuaNumber).value)
        assertEquals("猫头", (first.fields["name"] as LuaValue.LuaString).value)
        assertEquals("第一行<br />第二行", (first.fields["desc"] as LuaValue.LuaString).value)
        assertEquals("特殊<br>说明", (first.fields["spdesc"] as LuaValue.LuaString).value)
        assertTrue((first.fields["enabled"] as LuaValue.LuaBoolean).value)
        val get = first.fields["get"] as LuaValue.LuaArray
        assertEquals(listOf("商城", "活动"), get.values.map { (it as LuaValue.LuaString).value })

        val second = values[1] as LuaValue.LuaObject
        assertEquals("无id", (second.fields["name"] as LuaValue.LuaString).value)
        assertTrue("id" !in second.fields)

        val third = values[2] as LuaValue.LuaObject
        assertEquals(-1, (third.fields["quality"] as LuaValue.LuaNumber).value)
        assertEquals(LuaValue.LuaNil, third.fields["extra"])
    }

    @Test
    fun unterminatedStringThrows() {
        assertFailsWith<IllegalStateException> {
            LuaTableParser.parseReturnArray("""return { name = "猫头 }""")
        }
    }

    @Test
    fun nonArrayReturnYieldsEmptyList() {
        assertTrue(LuaTableParser.parseReturnArray("return 1").isEmpty())
    }
}
