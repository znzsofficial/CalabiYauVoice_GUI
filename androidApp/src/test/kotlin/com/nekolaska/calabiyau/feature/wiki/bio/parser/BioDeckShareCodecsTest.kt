package com.nekolaska.calabiyau.feature.wiki.bio.parser

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BioDeckShareCodecsTest {
    @Test
    fun roundTripsBothFactionsWithOfficialSuffixAndMissingPadding() {
        for (faction in listOf("超弦体", "晶源体")) {
            val code = BioDeckShareCodecs.encodeShareCode(listOf(0, 3, 127), faction)
            assertTrue(code.endsWith("**"))
            val unpadded = code.removeSuffix("**").trimEnd('=')
            assertEquals(code, BioDeckShareCodecs.normalizeShareCode("提示｜卡组｜$unpadded**"))
            assertEquals(
                listOf("a", "b", "c") to faction,
                BioDeckShareCodecs.decodeShareCode(unpadded, faction, mapOf(0 to "a", 3 to "b", 127 to "c"))
            )
            val padded = BioDeckShareCodecs.encodeShareCode(listOf(0), faction)
            assertTrue(padded.endsWith("=**"))
            val missingPadding = padded.removeSuffix("**").trimEnd('=')
            assertEquals(padded, BioDeckShareCodecs.normalizeShareCode(missingPadding))
            assertEquals(listOf("a") to faction, BioDeckShareCodecs.decodeShareCode(missingPadding, faction, mapOf(0 to "a")))
        }
    }

    @Test
    fun rejectsGarbageAndInvalidProtocolInBothDisplayAndImport() {
        val invalid = listOf(
            "", "ThisIsNotADeckCode", b64("not-deck-payload"),
            b64("000A|0002|1"), b64("000b|0002|1"), b64("000B|0004|1"),
            b64("000B|0002|-1"), b64("000B|0002|+1"), b64("000B|0002|0x1"),
            b64("000B|0002|XYZ"), b64("000B|0002|"), b64("000B|0002| 1"),
            b64("000B|0002|1|extra"), b64("000B|0002|" + "F".repeat(1025)),
            "A".repeat(100_000), b64("000B|0002|1") + "=", "!!!!" + b64("000B|0002|1"),
            b64("000B|0002|1").dropLast(2) + "F=", b64("000B|0002|1").chunked(4).joinToString(" ")
        )
        for (code in invalid) {
            assertEquals("", BioDeckShareCodecs.normalizeShareCode(code), code.take(80))
            assertFailsWith<IllegalArgumentException>(code.take(80)) {
                BioDeckShareCodecs.decodeShareCode(code, "超弦体", mapOf(0 to "known"))
            }
        }
    }

    @Test
    fun unknownBitOrBlankCardIdRejectsWholeDeckRatherThanDroppingCards() {
        val code = BioDeckShareCodecs.encodeShareCode(listOf(0, 1), "超弦体")
        for (mapping in listOf(mapOf(0 to "known"), mapOf(0 to "known", 1 to ""))) {
            assertFailsWith<IllegalArgumentException> {
                BioDeckShareCodecs.decodeShareCode(code, "超弦体", mapping)
            }
        }
    }

    @Test
    fun rejectsWrongFactionAndInvalidEncodingIndexes() {
        val code = BioDeckShareCodecs.encodeShareCode(listOf(1), "晶源体")
        assertFailsWith<IllegalArgumentException> {
            BioDeckShareCodecs.decodeShareCode(code, "超弦体", mapOf(1 to "known"))
        }
        for (indexes in listOf(emptyList(), listOf(-1), listOf(1, -1), listOf(4096), listOf(Int.MAX_VALUE))) {
            assertFailsWith<IllegalArgumentException> {
                BioDeckShareCodecs.encodeShareCode(indexes, "超弦体")
            }
        }
        assertFailsWith<IllegalArgumentException> {
            BioDeckShareCodecs.encodeShareCode(listOf(1), "未知阵营")
        }
    }

    @Test
    fun acceptsZeroLowercaseHexAndSafetyBoundaryWithoutLargeAllocations() {
        assertEquals(emptyList<String>() to "超弦体", BioDeckShareCodecs.decodeShareCode(b64("000B|0002|0"), "超弦体", emptyMap()))
        assertEquals(listOf("a", "b", "c", "d") to "超弦体", BioDeckShareCodecs.decodeShareCode(b64("000B|0002|f"), "超弦体", (0..3).associateWith { ('a' + it).toString() }))
        val code = BioDeckShareCodecs.encodeShareCode(listOf(4095), "超弦体")
        assertEquals(listOf("last") to "超弦体", BioDeckShareCodecs.decodeShareCode(code, "超弦体", mapOf(4095 to "last")))
    }

    private fun b64(payload: String) = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
}
