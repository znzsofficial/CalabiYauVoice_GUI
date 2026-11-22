package com.nekolaska.calabiyau.feature.wiki.extraction.equipment.parser

import com.nekolaska.calabiyau.core.wiki.HtmlText
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.EquipmentKind
import com.nekolaska.calabiyau.feature.wiki.extraction.equipment.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

internal class EquipmentFormatException(detail: String) : IllegalArgumentException(detail)

internal object EquipmentParser {
    private const val WIKI_BASE = "https://wiki.biligame.com/klbq/"
    private val statLine = Regex("""^(\S+)\s+(.+)$""")
    private val magazineSize = Regex("""\d+\s*发""")
    private val gridSize = Regex("""\d+\s*×\s*\d+""")

    private fun invalid(reason: String): Nothing = throw EquipmentFormatException(reason)
    private fun ensure(condition: Boolean, reason: String) { if (!condition) invalid(reason) }
    private fun String.present(field: String) = takeIf(String::isNotBlank) ?: invalid(field)
    private fun String.number(field: String) = takeIf { it.toDoubleOrNull()?.isFinite() == true } ?: invalid(field)
    private fun Element.only(selector: String) = select(selector).singleOrNull() ?: invalid(selector)

    // The sort slot is corroboration, not a series ID. The gun module exposes the exact same
    // (name, weaponType, sortId) for each weapon.line; armor explicitly sets param1 to the suit.
    // Never join on a filename, a shared prefix, adjacent cards or arithmetic on a variant ID.
    private data class Group(val name: String, val category: String, val gunSlot: Int?)

    fun parse(kind: EquipmentKind, html: String): EquipmentPage {
        val doc = Jsoup.parse(html, WIKI_BASE, Parser.htmlParser().setTrackPosition(true))
        ensure(doc.select(".redirectMsg, .error").isEmpty(), "重定向或模板错误")
        val gallery = doc.only(".gallerygrid")
        ensure(gallery.hasClass(kind.gallery) && EquipmentKind.entries.none { it != kind && gallery.hasClass(it.gallery) }, "图鉴类型不符")
        // Jsoup can otherwise repair and accept a string cut inside the final card.
        ensure(gallery.endSourceRange().isTracked && !gallery.endSourceRange().isImplicit, "图鉴截断")
        val count = Regex("""^共\s*(\d+)\s*${kind.countUnit}""").find(gallery.previousElementSibling()?.text().orEmpty())
            ?.groupValues?.get(1)?.toIntOrNull() ?: invalid("缺少${kind.title}总数")
        val cards = gallery.children().filter { it.hasClass("sec-card") }
        ensure(count > 0 && count == cards.size && count == doc.select(".sec-card").size &&
            count == doc.select(".gallerygrid-item").size, "${kind.title}数量与声明不符")

        fun options(group: Int): Map<String, String> {
            val elements = doc.select(".cardSelectOption[data-group=$group]")
            val values = elements.associate { option ->
                val key = option.attr("data-option")
                ensure(key.startsWith("$group|"), "筛选组不符")
                key.substringAfter('|').present("筛选值为空") to option.text().present("筛选名为空")
            }
            ensure(values.isNotEmpty() && values.size == elements.size, "筛选声明不完整")
            return values
        }
        val categories = options(1).keys
        val qualities = options(2).map { (raw, label) -> EquipmentQuality(raw.toIntOrNull() ?: invalid("品质码异常"), label) }
        val byCode = qualities.associateBy { it.code }
        ensure(byCode.size == qualities.size, "品质码重复")
        val ids = mutableSetOf<Int>()
        val groups = linkedMapOf<Group, MutableList<EquipmentVariant>>()
        cards.forEach { card ->
            val name = card.only(".sec-card-name").text().present("名称缺失")
            val category = card.attr("data-param1")
            ensure(category in categories, "$name 分类不符")
            if (kind == EquipmentKind.ARMOR) ensure(category == name, "$name 套装不符")
            val slot = card.attr("data-sort-default").toIntOrNull() ?: invalid("$name 缺少排序组")
            val group = Group(name, category, slot.takeIf { kind == EquipmentKind.GUNS })
            val item = parseCard(kind, card, category, byCode)
            val variants = groups.getOrPut(group) { mutableListOf() }
            ensure(ids.add(item.id) && variants.none { it.quality == item.quality }, "$name 品质或编号重复")
            variants += item
        }
        val qualityOrder = qualities.map { it.code }
        val series = groups.map { (group, cardsInGroup) ->
            // Length-prefix user-readable fields so even punctuation in names cannot collide.
            val key = "${group.gunSlot}:${group.category.length}:${group.category}:${group.name}"
            EquipmentSeries(key, group.name, group.category, cardsInGroup.sortedBy { qualityOrder.indexOf(it.quality.code) })
        }
        return EquipmentPage(series, categories.filter { cat -> series.any { it.category == cat } },
            qualities.filter { q -> series.any { s -> s.variants.any { it.quality == q } } })
    }

    private fun parseCard(kind: EquipmentKind, card: Element, category: String, byCode: Map<Int, EquipmentQuality>): EquipmentVariant {
        val id = card.attr("data-sort-id").toIntOrNull()?.takeIf { it > 0 } ?: invalid("装备编号缺失")
        val code = card.attr("data-quality").toIntOrNull() ?: invalid("$id 缺少品质")
        val quality = byCode[code] ?: invalid("$id 品质未声明")
        val qTag = card.only(".sec-card-tags .sec-qtag")
        ensure(card.attr("data-param2") == code.toString() && qTag.attr("data-q") == code.toString() && qTag.text() == quality.name, "$id 品质不符")
        val value = card.only(".sec-card-value").text().substringAfter("价值 ", "").present("$id 缺少价值")
        val numericValue = card.attr("data-sort-value").toLongOrNull() ?: invalid("$id 价值异常")
        ensure(value.replace(",", "").toLongOrNull() == numericValue, "$id 价值不符")
        val weight = card.attr("data-sort-weight").number("$id 缺少重量")
        val time = card.attr("data-sort-time").number("$id 缺少搜刮时间")
        val rate = card.attr("data-sort-rate").number("$id 缺少出现率").toDouble()
        val loot = HtmlText.detail(card.selectFirst(".sec-card-loot"))
        ensure(rate <= 0 || loot.startsWith("出现率 ") && '%' in loot, "$id 出现率不完整")
        val description = card.only(".sec-card-desc").clone().apply { select(".sec-card-title").remove() }
        val stats = linkedMapOf<String, String>()
        card.only(".sec-card-stats").children().forEach { span ->
            val parts = statLine.matchEntire(span.text()) ?: invalid("$id 属性缺少数值")
            ensure(stats.put(parts.groupValues[1], parts.groupValues[2]) == null, "$id 属性重复")
        }
        val core = when (kind) {
            EquipmentKind.GUNS -> parseGun(card, stats)
            EquipmentKind.ARMOR -> parseArmor(card, stats)
        }
        val tagElements = card.select(".sec-card-tags .sec-card-tag:not(.sec-qtag)")
        val tags = tagElements.filterNot { it.hasAttr("data-tag") }.map { it.text().present("$id 标签缺失") }
        val sourceTags = tagElements.filter { it.hasAttr("data-tag") }.groupBy({ it.attr("data-tag") }, { it.text().present("$id 标签缺失") })
            .mapValues { it.value.joinToString("\n") }
        ensure(if (kind == EquipmentKind.GUNS) category in tags && tags.size >= 2 else "防具" in tags, "$id 装备标签不符")
        if (kind == EquipmentKind.GUNS && time.toDouble() > 0) ensure("搜刮 " in sourceTags["用途"].orEmpty(), "$id 搜刮信息缺失")
        // Red links and unuploaded pictures carry no usable artwork; all other variant data survives.
        val img = card.selectFirst(".sec-card-img img")?.takeUnless { it.closest(".new") != null }
        val imageUrl = img?.let { el -> sequenceOf("data-src", "src").filter { el.attr(it).isNotBlank() }.map { el.absUrl(it) }
            .firstOrNull { it.startsWith("https://") || it.startsWith("http://") } }
        return EquipmentVariant(id, quality, imageUrl, HtmlText.detail(description).present("$id 缺少描述"),
            core, stats - core.fields.keys, value, tags, sourceTags, loot, weight, time, numericValue, rate)
    }

    private fun parseGun(card: Element, fields: Map<String, String>): EquipmentStats.Gun {
        val rawDamage = card.attr("data-sort-dmg").number("缺少枪械伤害排序").toDouble()
        ensure(card.attr("data-sort-armor").isEmpty() && card.attr("data-sort-hp").isEmpty(), "枪械混入防具数据")
        fun stat(label: String) = fields[label]?.number(label) ?: invalid(label)
        // The rendering module rounds chips to one decimal; its sort key retains the raw number.
        val damage = stat("伤害")
        ensure(kotlin.math.abs(rawDamage - damage.toDouble()) <= 0.050001, "枪械伤害不符")
        return EquipmentStats.Gun(damage, stat("射速"), fields["弹匣"]?.takeIf(magazineSize::matches) ?: invalid("弹匣"),
            stat("散射控制"), stat("后坐力控制"), stat("操控速度"))
    }

    private fun parseArmor(card: Element, fields: Map<String, String>): EquipmentStats.Armor {
        ensure(card.attr("data-sort-dmg").isEmpty(), "防具混入枪械数据")
        // The armor renderer suppresses zero chips, but publishes explicit armor/health sort values.
        // Monster defense has no such backing field: a missing chip stays unknown.
        fun primary(label: String, attr: String): String {
            val raw = card.attr(attr).number(label)
            val text = fields[label] ?: return raw.takeIf { it.toDouble() == 0.0 } ?: invalid(label)
            ensure(text.toDoubleOrNull() == raw.toDouble(), "$label 不符")
            return text
        }
        val weight = fields["重量"] ?: invalid("重量")
        ensure(weight.replace(",", "").toDoubleOrNull() == card.attr("data-sort-weight").toDoubleOrNull(), "防具重量不符")
        return EquipmentStats.Armor(primary("护甲", "data-sort-armor"), primary("生命", "data-sort-hp"),
            fields["对怪物减伤"], fields["占格"]?.takeIf(gridSize::matches) ?: invalid("占格"), weight)
    }
}
