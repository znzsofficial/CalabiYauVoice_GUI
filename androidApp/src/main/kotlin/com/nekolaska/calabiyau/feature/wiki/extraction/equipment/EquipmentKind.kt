package com.nekolaska.calabiyau.feature.wiki.extraction.equipment

import com.nekolaska.calabiyau.feature.wiki.extraction.model.EXTRACTION_MODE_PAGE

internal enum class EquipmentKind(val title: String, val gallery: String, val countUnit: String) {
    GUNS("枪械图鉴", "sec-weapon-gallery", "把枪械"),
    ARMOR("防具图鉴", "sec-armor-gallery", "件防具");

    val pageName get() = "$EXTRACTION_MODE_PAGE/$title"
    val cacheKey get() = "extraction_equipment_${name.lowercase()}_v1"
}
