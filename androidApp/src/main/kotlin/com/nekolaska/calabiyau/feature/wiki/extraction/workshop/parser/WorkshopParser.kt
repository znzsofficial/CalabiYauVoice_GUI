package com.nekolaska.calabiyau.feature.wiki.extraction.workshop.parser

import com.nekolaska.calabiyau.core.wiki.HtmlText
import com.nekolaska.calabiyau.feature.wiki.extraction.workshop.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal object WorkshopParser {
    private val sectionTitles = setOf("舱室总览", "建造配方", "舱室升级", "派驻角色", "装修与收藏")
    private val decorationTitles = setOf("家具", "套装", "赛季珍藏品")

    fun parse(html: String, wikitext: String): WorkshopPage {
        val root = Jsoup.parse(html, "https://wiki.biligame.com/klbq/").select(".mw-parser-output").singleOrNull()
            ?: broken("缺少工坊正文")
        verifyWorkshop(root.select(".redirectMsg, .redirectText, .error, .scribunto-error").isEmpty(), "工坊页面异常")
        val (intro, sections) = WorkshopSection("工坊", root.children().toList()).split("h2")
        val byTitle = sections.associateBy { it.title }
        verifyWorkshop(byTitle.keys == sectionTitles, "工坊章节不完整")

        val overview = byTitle.getValue("舱室总览")
        val roomCards = overview.cards().map { it.card(requiredDetails = true) }
        val roomNames = roomCards.map { it.name }.toSet()
        verifyWorkshop(roomNames.size == roomCards.size, "舱室重复")
        checkCount(overview, "个?\\s*舱室", roomNames.size, required = true)

        val (recipeIntro, stations) = byTitle.getValue("建造配方").split("h3")
        verifyWorkshop(stations.isNotEmpty() && stations.all { it.title in roomNames }, "配方舱室不完整")
        val recipes = stations.flatMap { station ->
            val categories = station.split("h4").second
            verifyWorkshop(categories.isNotEmpty(), "${station.title} 缺少配方分类")
            categories.flatMap { category -> category.cards().map { it.recipe(station.title, category.title) } }
        }
        verifyWorkshop(recipes.distinctBy { it.id }.size == recipes.size, "配方重复")

        val (upgradeIntro, ladders) = byTitle.getValue("舱室升级").split("h3")
        verifyWorkshop(ladders.map { it.title }.toSet() == roomNames, "升级表和舱室总览不符")
        val upgrades = ladders.associate { ladder ->
            val rows = ladder.rows("sec-ws-ladder", "sec-ws-lv").map { it.upgrade() }
            verifyWorkshop(rows.map { levelNumber(it.level) } == (1..rows.size).toList(), "${ladder.title} 升级等级不连续")
            ladder.title to rows
        }
        for (recipe in recipes) {
            val levels = upgrades.getValue(recipe.room).map { levelNumber(it.level) }
            verifyWorkshop(recipe.tiers.all { levelNumber(it.roomLevel) in levels }, "${recipe.name} 的所需等级不在舱室升级表")
        }

        val (roster, bonusSections) = byTitle.getValue("派驻角色").split("h3")
        val staff = roster.cards().map { it.card(requiredDetails = true) }
        verifyWorkshop(staff.distinctBy { it.name }.size == staff.size, "派驻角色重复")
        val bonus = bonusSections.singleOrNull()?.takeIf { it.title == "派驻增益" } ?: broken("缺少派驻增益")
        val buffs = parseBuffs(bonus.nodes.singleOrNull { it.normalName() == "table" } ?: broken("缺少派驻增益表"))
        val buffNames = buffs.map { it.name }.toSet()
        verifyWorkshop(buffNames.size == buffs.size && staff.all { card -> card.subtitle.split('、').all { it.trim() in buffNames } }, "角色和派驻增益表不符")

        val groups = byTitle.getValue("装修与收藏").split("h3").second
        verifyWorkshop(groups.map { it.title }.toSet() == decorationTitles, "装修与收藏章节不完整")
        val decorations = groups.map { section ->
            val cards = section.cards().map { it.card() }
            verifyWorkshop(cards.distinctBy { it.name }.size == cards.size, "${section.title} 条目重复")
            checkCount(section, "件", cards.size, required = section.title != "套装")
            DecorationGroup(section.title, section.requiredNotes(), cards)
        }

        val page = WorkshopPage(intro.requiredNotes(), recipeIntro.requiredNotes(), overview.requiredNotes(),
            upgradeIntro.requiredNotes(), roster.requiredNotes(), recipes,
            roomCards.map { WorkshopRoom(it, upgrades.getValue(it.name)) }, staff, buffs, decorations)
        val cardCount = roomCards.size + recipes.size + staff.size + decorations.sumOf { it.cards.size }
        verifyWorkshop(root.select(".sec-ws-card").size == cardCount && root.select(".sec-ws-lv").size == upgrades.values.sumOf { it.size } &&
            root.select(".sec-ws-card__tier").size == recipes.sumOf { it.tiers.size }, "工坊存在未归属的条目")
        checkWorkshopDeclarations(wikitext, page)
        return page
    }

    private fun WorkshopSection.requiredNotes() = notes().ifBlank { broken("$title 缺少说明") }

    private fun checkCount(section: WorkshopSection, unit: String, actual: Int, required: Boolean) {
        val counts = Regex("共\\s*(\\d+)\\s*$unit").findAll(section.notes()).map { it.groupValues[1].toIntOrNull() }.toList()
        verifyWorkshop((!required || counts.isNotEmpty()) && counts.all { it == actual }, "${section.title} 的公布数量与条目不符")
    }

    private fun parseBuffs(table: Element): List<WorkshopBuff> {
        val rows = table.select("tr")
        val header = rows.firstOrNull()?.children() ?: broken("空增益表")
        val labels = header.map { it.text() }
        verifyWorkshop(labels.size == 3 && labels.toSet() == setOf("增益", "效果", "适用舱室") && header.all { it.normalName() == "th" }, "增益表表头不符")
        return rows.drop(1).map { row ->
            val cells = row.children()
            verifyWorkshop(cells.size == labels.size && cells.all { it.normalName() == "td" && it.text().isNotBlank() && it.attr("rowspan") in setOf("", "1") && it.attr("colspan") in setOf("", "1") }, "增益表内容不完整")
            fun value(label: String) = HtmlText.detail(cells[labels.indexOf(label)])
            WorkshopBuff(value("增益"), value("效果"), value("适用舱室"))
        }.ifEmpty { broken("增益表无内容") }
    }
}
