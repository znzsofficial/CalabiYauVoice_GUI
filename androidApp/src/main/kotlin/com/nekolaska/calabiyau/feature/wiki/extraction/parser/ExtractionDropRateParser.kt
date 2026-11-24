package com.nekolaska.calabiyau.feature.wiki.extraction.parser

import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropRate
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropRateShard
import data.SharedJson
import kotlinx.serialization.json.*
import java.math.BigDecimal

internal object ExtractionDropRateParser {
    /** formatversion=2 revisions response; never accept another valid JSON page under this cache key. */
    fun content(payload: String, title: String): String? {
        val root = obj(payload) ?: return null
        if ("error" in root) return null
        val pages = (root["query"] as? JsonObject)?.get("pages") as? JsonArray ?: return null
        val page = pages.singleOrNull() as? JsonObject ?: return null
        if (page.text("title") != title || "missing" in page || "invalid" in page) return null
        val rev = (page["revisions"] as? JsonArray)?.singleOrNull() as? JsonObject ?: return null
        val main = (rev["slots"] as? JsonObject)?.get("main") as? JsonObject ?: return null
        return main.text("content")
    }

    fun index(text: String): Map<String, DropRateShard>? {
        val root = obj(text) ?: return null
        val count = root["n"].integer() ?: return null
        val index = root["index"] as? JsonObject ?: return null
        if (index.isEmpty() || index.keys.any { it.isBlank() }) return null
        val assignments = index.mapValues { (_, value) -> value.integer()?.takeIf { it in 0 until count } ?: return null }
        // q is optional Widget dropdown metadata (a code or [min,max]), never evidence for a rate
        // or for the existence of each intermediate quality. Reject orphaned index metadata.
        if ("q" in root) {
            val qualities = root["q"] as? JsonObject ?: return null
            if (!assignments.keys.containsAll(qualities.keys)) return null
            for (q in qualities.values) {
                if (q == JsonNull || q.integer()?.let { it >= 0 } == true) continue
                if (q !is JsonArray || q.size != 2) return null
                val lo = q[0].integer() ?: return null
                val hi = q[1].integer() ?: return null
                if (lo !in 0..hi) return null
            }
        }
        val shards = assignments.keys.groupBy(assignments::getValue).mapValues { (id, names) -> DropRateShard(id, names.toSet()) }
        return assignments.mapValues { shards.getValue(it.value) }
    }

    fun shard(text: String, expected: DropRateShard): Map<String, List<DropRate>>? {
        val root = obj(text) ?: return null
        if (root.isEmpty() || root.keys != expected.names) return null
        return root.mapValues { (_, value) ->
            val rows = value as? JsonArray ?: return null
            if (rows.isEmpty()) return null
            rows.map { element ->
                val row = element as? JsonObject ?: return null
                val rate = row["rate"] as? JsonPrimitive ?: return null
                val percent = rate.takeUnless { it.isString }?.content?.toBigDecimalOrNull() ?: return null
                if (percent.signum() < 0 || percent > BigDecimal.valueOf(100)) return null
                val q = row["q"]
                val quality = if (q == null || q == JsonNull) null else q.integer()?.takeIf { it >= 0 } ?: return null
                DropRate(row.text("src") ?: return null, row.text("type") ?: return null,
                    quality, row.text("pool") ?: return null, percent)
            }
        }
    }

    private fun obj(text: String): JsonObject? = runCatching { SharedJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
    private fun JsonElement?.integer() = (this as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
}
