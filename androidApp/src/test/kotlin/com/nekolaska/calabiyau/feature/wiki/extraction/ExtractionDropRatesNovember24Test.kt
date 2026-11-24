package com.nekolaska.calabiyau.feature.wiki.extraction

import com.nekolaska.calabiyau.core.cache.OfflineCache
import com.nekolaska.calabiyau.core.cache.fetchValidatedCachePayload
import com.nekolaska.calabiyau.feature.wiki.extraction.api.ExtractionDropRateApi
import com.nekolaska.calabiyau.feature.wiki.extraction.model.*
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionContainerParser
import com.nekolaska.calabiyau.feature.wiki.extraction.parser.ExtractionDropRateParser as Parser
import com.nekolaska.calabiyau.feature.wiki.extraction.source.DROP_RATE_INDEX_TITLE
import com.nekolaska.calabiyau.feature.wiki.extraction.source.DropRateDirectory
import com.nekolaska.calabiyau.feature.wiki.extraction.source.dropRateShardTitle
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.math.BigDecimal
import kotlin.test.*

class ExtractionDropRatesNovember24Test {
    private val manifest = """{"n":8,"index":{"切割器":7,"巧克力":7,"警探":6},"q":{"切割器":3,"巧克力":1,"警探":[1,8]}}"""
    private val index = assertNotNull(Parser.index(manifest))
    private fun fixture(part: Int) = checkNotNull(javaClass.getResource("/fixtures/extraction/drop_rates_20261124_$part.json")).readText(Charsets.UTF_8)
    private fun rows(name: String): List<DropRate> {
        val key = index.getValue(name)
        return assertNotNull(Parser.shard(fixture(key.number), key)).getValue(name)
    }

    @Test fun preservesSourceDifficultyPoolAndQualityWithoutAttachingContainerIds() {
        val cutter = rows("切割器")
        // Disordered input and a tie: sort actual decimals stably; don't use source name as a key.
        val ranked = selectDropRates(cutter.drop(2) + cutter.take(2), 3, false)
        assertEquals(listOf("晶骸核心（阶段 6·升变）", "晶骸核心（阶段 5·升变）", "商务手提箱", "潮流旅行箱", "晶骸核心（阶段 6·基点）"),
            ranked.rows.map { it.rate.source })
        assertEquals(listOf("0.936%", "0.739%", "0.191%", "0.191%", "0.048%"), ranked.rows.map { it.rate.displayPercent })
        val both = selectDropRates(cutter, 3, true).rows.filter { it.rate.source == "战车" }
        assertEquals(listOf("容器", "晶源体"), both.map { it.rate.type })
        val anonymous = cutter.take(2).map { it.copy(source = "晶骸核心") }
        val repeated = selectDropRates(anonymous + anonymous.first(), 3, true).rows
        assertEquals(3, repeated.map { it.key }.distinct().size)
        assertTrue(repeated.all { it.rate.source == "晶骸核心" && it.rate.pool == "掉落池" })

        val police = rows("警探")
        assertEquals(listOf("0.008%"), selectDropRates(police, 8, false).rows.map { it.rate.displayPercent })
        assertEquals("0.005%", selectDropRates(police, 2, false).rows.single().rate.displayPercent)
        assertFalse(selectDropRates(police, 2, false).allBelowThreshold)
        assertTrue(selectDropRates(police, 7, false).rows.isEmpty(), "[1,8] in the index does not assert a Q7 rate")
        assertEquals("0%", selectDropRates(police, 8, true).rows.last().rate.displayPercent)
    }

    @Test fun allTinyIncludesTheFiveHighestOfThisQualityEvenWhenAnotherQualityHasCommonDrops() {
        val common = rows("警探").first()
        val tiny = listOf("0", "0.001", "0.004", "0.003", "0", "0.002", "0.004").mapIndexed { i, rate ->
            common.copy(source = "来源$i", quality = 8, percent = BigDecimal(rate))
        }
        val result = selectDropRates(tiny + common, 8, false)
        assertTrue(result.allBelowThreshold)
        assertEquals(7, result.total)
        assertEquals(listOf("来源2", "来源6", "来源3", "来源5", "来源1"), result.rows.map { it.rate.source })
        assertEquals(7, selectDropRates(tiny + common, 8, true).rows.size)
    }

    @Test fun rejectsMalformedOrMisdirectedRevisionsAndTruncatedShards() {
        val title = dropRateShardTitle(7)
        val raw = fixture(7)
        assertEquals(raw, Parser.content(envelope(title, raw), title))
        assertNull(Parser.content(envelope(dropRateShardTitle(0), raw), title))
        assertNull(Parser.content("<html>blocked</html>", title))
        assertNull(Parser.content("""{"query":{"pages":[{"title":"$title","missing":true}]}}""", title))
        val key = index.getValue("切割器")
        for (broken in listOf(fixture(6), "{}", """{"切割器":[],"巧克力":[]}""", raw.replace("巧克力", "其他"),
            raw.replace("0.936", "null"), raw.replace("0.936", "\"0.936\""), raw.replace("0.936", "100.01"),
            raw.replace("0.936", "-1"), raw.replace("\"rate\"", "\"weight\""), raw.replace("\"掉落池\"", "\"\""))) {
            assertNull(Parser.shard(broken, key))
        }
        assertNull(Parser.index(manifest.replace(":7", ":8")))
        assertNull(Parser.index(manifest.replace("[1,8]", "[8,1]")))
        val unclassified = assertNotNull(Parser.shard(raw.replace("\"q\":3", "\"q\":null"), key)).getValue("切割器")
        assertTrue(selectDropRates(unclassified, 3, true).rows.isEmpty())
        assertEquals(7, selectDropRates(unclassified, null, true).total)
    }

    private fun envelope(title: String, text: String): String = buildJsonObject {
        putJsonObject("query") { putJsonArray("pages") { addJsonObject {
            put("title", title)
            putJsonArray("revisions") { addJsonObject { putJsonObject("slots") { putJsonObject("main") { put("content", text) } } } }
        } } }
    }.toString()

    private inner class Store : DropRateDirectory {
        val seven = dropRateShardTitle(7)
        val six = dropRateShardTitle(6)
        val live = mutableMapOf(DROP_RATE_INDEX_TITLE to manifest, seven to fixture(7), six to fixture(6))
        val disk = mutableMapOf<String, Pair<Long, String>>()
        val requests = mutableListOf<String>()
        var clock = 1000L
        private var bindings: String? = null
        override suspend fun readBindings() = bindings
        override suspend fun reserveBindings(): suspend (String) -> Unit = { bindings = it }
        fun api() = ExtractionDropRateApi(this, { clock }) { file, cacheOnly, _, valid ->
            fun cached() = disk[file.cacheKey]?.let { (at, raw) -> OfflineCache.CacheResult(raw, true, clock - at) }
            if (cacheOnly) cached() else fetchValidatedCachePayload(online = true,
                network = { requests += file.title; live[file.title]?.let { envelope(file.title, it) } }, read = { cached() },
                publish = { disk[file.cacheKey] = clock to it }, validate = valid)
        }
    }

    @Test fun loadsOnlySelectedShardReusesSiblingsAndPreservesGoodDiskOnFailedRefresh() = runBlocking { with(Store()) {
        val api = api()
        assertIs<ApiResult.Error>(api.fetch("切割器", cacheOnly = true))
        assertTrue(requests.isEmpty())
        assertEquals(rows("切割器"), assertIs<ApiResult.Success<List<DropRate>>>(api.fetch("切割器")).value)
        assertEquals(listOf(DROP_RATE_INDEX_TITLE, seven), requests)
        assertEquals(rows("巧克力"), assertIs<ApiResult.Success<List<DropRate>>>(api.fetch("巧克力")).value)
        assertTrue(assertIs<ApiResult.Success<List<DropRate>>>(api.fetch("未收录")).value.isEmpty())
        assertEquals(2, requests.size)

        val shardKey = disk.keys.single { it != DROP_RATE_INDEX_TITLE }
        val good = disk.getValue(shardKey)
        live[seven] = "{}"
        clock += 42
        val offline = assertIs<ApiResult.Success<List<DropRate>>>(api.fetch("切割器", forceRefresh = true))
        assertTrue(offline.isOffline); assertEquals(42L, offline.cacheAgeMs); assertEquals(rows("切割器"), offline.value)
        assertEquals(good, disk[shardKey])
        clock += 3
        val reopened = assertIs<ApiResult.Success<List<DropRate>>>(api.fetch("巧克力"))
        assertTrue(reopened.isOffline); assertEquals(45L, reopened.cacheAgeMs)
        live[six] = "{}"
        assertEquals(ErrorKind.PARSE, assertIs<ApiResult.Error>(api.fetch("警探")).kind)
        live[six] = fixture(6)
        assertFalse(assertIs<ApiResult.Success<List<DropRate>>>(api.fetch("警探")).isOffline)
        assertEquals(listOf(DROP_RATE_INDEX_TITLE, seven, DROP_RATE_INDEX_TITLE, seven, seven, six, six), requests)
        assertTrue(assertIs<ApiResult.Success<List<DropRate>>>(api.fetch("警探", cacheOnly = true)).isOffline)
        assertEquals(7, requests.size)
        live[seven] = fixture(7).replace("0.936", "0.937")
        assertFalse(assertIs<ApiResult.Success<List<DropRate>>>(api.fetch("巧克力")).isOffline)
        val sibling = assertIs<ApiResult.Success<List<DropRate>>>(api.fetch("切割器"))
        assertFalse(sibling.isOffline); assertEquals("0.937%", sibling.value.first().displayPercent)
        assertEquals(8, requests.size) // Recovered shard is shared again.
    } }

    @Test fun changedOrMissingIndexKeepsOnlyPreviouslyVerifiedMembersAcrossRestarts() = runBlocking { with(Store()) {
        val api = api()
        assertIs<ApiResult.Success<*>>(api.fetch("切割器"))
        clock += 400
        live[DROP_RATE_INDEX_TITLE] = manifest.replace("\"切割器\":7", "\"切割器\":6,\"新物品\":7")
        live.remove(six) // Cutover incomplete: the old item has moved, the new one has never been validated.
        fun checkOld(name: String, value: ApiResult<List<DropRate>>) {
            val old = assertIs<ApiResult.Success<List<DropRate>>>(value)
            assertEquals(rows(name), old.value); assertTrue(old.isOffline); assertEquals(clock - 1000L, old.cacheAgeMs)
        }
        checkOld("切割器", api.fetch("切割器", forceRefresh = true))
        val cold = api()
        checkOld("巧克力", cold.fetch("巧克力", cacheOnly = true)) // Same number, changed name set.
        checkOld("切割器", cold.fetch("切割器", cacheOnly = true)) // Different number.
        assertIs<ApiResult.Error>(cold.fetch("新物品"))
        live.clear(); disk.remove(DROP_RATE_INDEX_TITLE); clock += 200
        checkOld("切割器", cold.fetch("切割器", forceRefresh = true)) // No usable manifest at all.
        checkOld("巧克力", api().fetch("巧克力", cacheOnly = true))
        assertIs<ApiResult.Error>(cold.fetch("新物品", cacheOnly = true))
        Unit
    } }

    @Test fun cancelledSelectedShardIsRetryableAndDoesNotRequestAnotherShard() = runBlocking {
        var attempts = 0
        val api = ExtractionDropRateApi(Store()) { (title), _, _, _ ->
            val content = when (title) {
                DROP_RATE_INDEX_TITLE -> manifest
                dropRateShardTitle(7) -> { if (++attempts == 1) throw CancellationException("left item"); fixture(7) }
                else -> error("Unexpected shard $title")
            }
            OfflineCache.CacheResult(envelope(title, content), false, 0)
        }
        assertFailsWith<CancellationException> { api.fetch("切割器") }
        assertEquals(rows("巧克力"), assertIs<ApiResult.Success<List<DropRate>>>(api.fetch("巧克力")).value)
        assertEquals(2, attempts)
    }

    @Test fun newItemOpensRatesAndBackRestoresOriginalCandidateExploration() {
        val first = DropNavigation().openItem(ExtractionDropItem("切割器", "Q3 卓越", 3)).showItemRates(false)
        val detail = first.openContainer("container:1", "切割器", "Q3 卓越")
        val second = detail.openItem(ExtractionDropItem("警探", "Q8 特殊", 8))
        assertTrue(second.current.showItemRates)
        assertEquals(first, second.back().back())
        assertFalse(second.back().current.showItemRates)
        assertEquals(first.history.size, first.showItemRates(true).history.size)
    }

    @Test fun currentSnapshotMatchesEveryNativeItemQualityAndAllTwelveBossStageNames() {
        val directory = System.getenv("EXTRACTION_RATES_NOV24_DIR")
        assumeTrue("Set EXTRACTION_RATES_NOV24_DIR to the local November 24 snapshot", !directory.isNullOrBlank())
        fun read(file: String) = File(directory!!, file).readText(Charsets.UTF_8)
        val current = assertNotNull(Parser.index(read("index.json")))
        assertEquals(360, current.size)
        val shards = current.values.distinctBy { it.number }.sortedBy { it.number }
        assertEquals((0..7).toList(), shards.map { it.number })
        val all = shards.flatMap { part ->
            val raw = read("${part.number}.json")
            val parsed = assertNotNull(Parser.shard(raw, part))
            val json = SharedJson.parseToJsonElement(raw).jsonObject
            parsed.forEach { (name, rows) ->
                val expected = json.getValue(name).jsonArray.map { it.jsonObject }
                assertEquals(expected.map { it.getValue("rate").jsonPrimitive.content.toBigDecimal() }, rows.map { it.percent })
                assertTrue(rows.zipWithNext().all { (a, b) -> a.percent >= b.percent }, name)
                rows.groupBy { it.quality }.forEach { (q, group) ->
                    assertEquals(group, selectDropRates(rows, q, true).rows.map { it.rate })
                }
            }
            parsed.toList()
        }.toMap()
        val rows = all.values.flatten()
        assertEquals(13_779, rows.size)
        assertEquals(5_892, rows.count { it.percent < BigDecimal("0.005") })
        assertEquals(557, rows.count { it.percent.compareTo(BigDecimal("0.005")) == 0 })
        val cores = rows.filter { it.type == "Boss阶段" }.groupingBy { it.source }.eachCount()
        val names = (1..6).flatMap { n -> listOf("基点", "升变").map { "晶骸核心（阶段 $n·$it）" } }
        assertEquals(names.associateWith { 166 }, cores)
        assertEquals("0.936%", all.getValue("切割器").first().displayPercent)
        val containerTable = ExtractionContainerParser.parseResult(read("table.html"))
        assertTrue(containerTable.canCache, containerTable.issues.toString())
        val native = buildDropLookup(containerTable.entries).keys.map { it.name to it.qualityCode }.toSet()
        assertEquals(528, native.size)
        assertEquals(native, all.flatMap { (name, rates) -> rates.map { name to it.quality } }.toSet())
        val paper = selectDropRates(all.getValue("纸飞机"), 4, false)
        assertEquals(12, paper.total); assertEquals(5, paper.rows.size)
        assertTrue(paper.rows.all { it.rate.percent.signum() == 0 })
    }
}
