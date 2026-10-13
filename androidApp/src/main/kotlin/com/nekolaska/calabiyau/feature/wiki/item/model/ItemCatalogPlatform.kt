package com.nekolaska.calabiyau.feature.wiki.item.model

/** Each Wiki page is its own catalog, with isolated memory/disk caches. */
enum class ItemCatalogPlatform(
    val pageName: String,
    val title: String,
    val cacheKey: String,
    val snapshotName: String
) {
    PC("功能道具图鉴PC端", "PC 道具图鉴", "item_catalog_pc_v1", "items_pc.html"),
    MOBILE("功能道具图鉴移动端", "移动端道具图鉴", "item_catalog_mobile_v1", "items_mobile.html")
}
