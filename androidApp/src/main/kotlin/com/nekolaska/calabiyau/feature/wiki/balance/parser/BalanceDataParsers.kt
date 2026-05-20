package com.nekolaska.calabiyau.feature.wiki.balance.parser

import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceResult
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceSettings
import com.nekolaska.calabiyau.feature.wiki.balance.model.CharacterMeta
import com.nekolaska.calabiyau.feature.wiki.balance.model.FilterOption
import com.nekolaska.calabiyau.feature.wiki.balance.model.HeroBalanceData
import com.nekolaska.calabiyau.feature.wiki.balance.model.PositionMeta
import data.SharedJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object BalanceDataParsers {

    fun buildBalancePayload(
        chartId: String,
        ideToken: String,
        modeCode: String,
        mapCode: String,
        rankCodes: List<String>,
        season1Code: String,
        season2Code: String
    ): String {
        return buildJsonObject {
            put("iChartId", JsonPrimitive(chartId))
            put("iSubChartId", JsonPrimitive(chartId))
            put("sIdeToken", JsonPrimitive(ideToken))
            put("mode", JsonPrimitive(modeCode))
            put("map", JsonPrimitive(mapCode))
            put("rank", buildJsonArray { rankCodes.forEach { add(JsonPrimitive(it)) } })
            put("season1", JsonPrimitive(season1Code))
            put("season2", JsonPrimitive(season2Code))
        }.toString()
    }

    fun parseSettings(body: String): BalanceSettings {
        val json = SharedJson
        val root = json.parseToJsonElement(body).jsonObject
        if (root["code"]?.jsonPrimitive?.intOrNull != 0) {
            val msg = root["msg"]?.jsonPrimitive?.contentOrNull ?: "未知错误"
            throw IllegalStateException("SERVER_MSG:$msg")
        }

        val value = root["data"]!!.jsonObject["value"]!!.jsonObject
        val setting = value["setting"]!!.jsonObject
        val roleList = value["role_list"]!!.jsonObject

        fun parseOptions(arr: JsonArray): List<FilterOption> = arr.map { item ->
            val content = json.parseToJsonElement(
                item.jsonObject["content"]!!.jsonPrimitive.content
            ).jsonObject
            FilterOption(
                code = content["code"]!!.jsonPrimitive.content,
                name = content["name"]!!.jsonPrimitive.content
            )
        }

        val modes = parseOptions(setting["mode"]!!.jsonArray)
        val maps = parseOptions(setting["map"]!!.jsonArray)
        val ranks = parseOptions(setting["rank"]!!.jsonArray)
        val seasons = parseOptions(setting["season"]!!.jsonArray)

        val positions = roleList["position"]!!.jsonArray.map { item ->
            val content = json.parseToJsonElement(
                item.jsonObject["content"]!!.jsonPrimitive.content
            ).jsonObject
            PositionMeta(
                code = content["position_code"]!!.jsonPrimitive.content,
                name = content["position_name"]!!.jsonPrimitive.content,
                imageUrl = content["position_image"]!!.jsonPrimitive.content
            )
        }

        val characters = roleList["role_list"]!!.jsonArray.map { item ->
            val content = json.parseToJsonElement(
                item.jsonObject["content"]!!.jsonPrimitive.content
            ).jsonObject
            CharacterMeta(
                code = content["character_code"]!!.jsonPrimitive.content,
                name = content["character_name"]!!.jsonPrimitive.content,
                positionCode = content["position_code"]!!.jsonPrimitive.content,
                campCode = content["camp_code"]!!.jsonPrimitive.intOrNull ?: 0,
                imageUrl = content["character_image"]!!.jsonPrimitive.content
            )
        }

        return BalanceSettings(modes, maps, ranks, seasons, positions, characters)
    }

    fun parseBalanceResult(body: String): BalanceResult {
        val root = SharedJson.parseToJsonElement(body).jsonObject
        val jData = root["jData"]?.jsonObject
            ?: throw IllegalStateException("返回数据格式异常")

        val iRet = jData["iRet"]?.jsonPrimitive?.contentOrNull
        if (iRet != "0") {
            val msg = jData["sMsg"]?.jsonPrimitive?.contentOrNull ?: "查询失败"
            throw NoSuchElementException(msg)
        }

        val data1 = jData["data1"]!!.jsonObject

        fun parseHeroList(arr: JsonArray): List<HeroBalanceData> = arr.map { elem ->
            val obj = elem.jsonObject
            HeroBalanceData(
                id = obj["id"]!!.jsonPrimitive.int,
                heroName = obj["heroName"]!!.jsonPrimitive.content,
                winRate = obj["winRate"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                selectRate = obj["selectRate"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                kd = obj["kd"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                damageAve = obj["damageAve"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                score = obj["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            )
        }

        val side1 = data1["side1"]?.jsonArray?.let { parseHeroList(it) } ?: emptyList()
        val side2 = data1["side2"]?.jsonArray?.let { parseHeroList(it) } ?: emptyList()

        return BalanceResult(attackers = side1, defenders = side2)
    }
}
