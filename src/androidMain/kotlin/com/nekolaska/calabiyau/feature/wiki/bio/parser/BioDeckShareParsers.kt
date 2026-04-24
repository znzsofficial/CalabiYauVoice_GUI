package com.nekolaska.calabiyau.feature.wiki.bio.parser

import com.nekolaska.calabiyau.feature.wiki.bio.model.DeckCardOption
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

object BioDeckShareParsers {

    fun parseFactionCards(arr: JsonArray): List<DeckCardOption> {
        return arr.mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    val name = element["name"]?.jsonPrimitive?.content.orEmpty().trim()
                    if (name.isBlank()) return@mapNotNull null
                    DeckCardOption(
                        name = name,
                        cardId = element["cardid"]?.jsonPrimitive?.content.orEmpty().trim(),
                        quality = element["quality"]?.jsonPrimitive?.content.orEmpty().trim(),
                        isDefault = element["default"]?.let { boolOf(it) } ?: false,
                        index = element["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
                    )
                }
                else -> null
            }
        }
    }

    private fun boolOf(el: JsonElement): Boolean {
        return el.jsonPrimitive.content.equals("true", ignoreCase = true)
    }
}
