package com.nekolaska.calabiyau.feature.wiki.interactionitem.model

import com.nekolaska.calabiyau.feature.wiki.item.model.Quality

const val INTERACTION_ITEM_PAGE_NAME = "好友"
const val INTERACTION_ITEM_PAGE_URL = "https://wiki.biligame.com/klbq/%E5%A5%BD%E5%8F%8B"

data class InteractionItemInfo(
    val name: String,
    val quality: Quality?,
    val qualityName: String,
    val description: String,
    val obtainMethod: String,
    val iconUrl: String?
)
