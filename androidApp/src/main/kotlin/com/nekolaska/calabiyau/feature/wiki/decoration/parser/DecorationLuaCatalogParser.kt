package com.nekolaska.calabiyau.feature.wiki.decoration.parser

import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationArtwork
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogSource
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationImageFile
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationParseResult
import com.nekolaska.calabiyau.feature.wiki.decoration.model.RawDecorationItem
import com.nekolaska.calabiyau.feature.wiki.decoration.model.RawDecorationPreview
import com.nekolaska.calabiyau.feature.wiki.decoration.model.RawDecorationSection

/** Joins the verified complete directory and ways tables; filename rules come from captured renderers. */
internal object DecorationLuaCatalogParser {
    fun parse(policy: DecorationCatalogSource.Lua, itemsText: String, waysText: String): DecorationParseResult {
        val data = DecorationModuleParser.parse(itemsText)
        val ways = DecorationWaysParser.parse(waysText)
        val issues = (data.issues + ways.issues).toMutableList()
        if (data.items.size < policy.minimumItems) issues += "条目数量 ${data.items.size} 小于已核验基线 ${policy.minimumItems}"
        val ordered = if (policy.sortById) data.items.sortedBy { it.id } else data.items
        val items = ordered.mapNotNull { row ->
            if (row.id !in ways.ways) issues += "缺少途径记录: ${row.id}"
            if (policy.artwork == DecorationArtwork.VEHICLE && row.specialDescription == null) issues += "${row.id}: 缺少载具外观效果字段"
            val prefix = when (policy.artwork) {
                DecorationArtwork.FILE_FIELD -> null
                DecorationArtwork.CHAT -> "聊天气泡"
                DecorationArtwork.HEADGEAR -> "头套"
                DecorationArtwork.ACTION -> "超弦体动作图标"
                DecorationArtwork.ROOM -> "房间外观"
                DecorationArtwork.AVATAR -> "头像框移动端"
                DecorationArtwork.VEHICLE -> "载具外观"
                DecorationArtwork.FIGURE -> "道具图标"
                DecorationArtwork.NAMECARD -> "个性名片"
            }
            val file = if (prefix == null) row.fileName else "${prefix}_${row.id}.png"
            if (file.isNullOrBlank() || !file.contains('.')) {
                issues += "${row.id}: 缺少有效图片文件"
                return@mapNotNull null
            }
            val previews = when (policy.artwork) {
                DecorationArtwork.ROOM -> listOf(
                    RawDecorationPreview("好友列表", DecorationImageFile("房间外观_${row.id}_FriendTitleBg.png")),
                    RawDecorationPreview("按钮样式", DecorationImageFile("房间外观_${row.id}_BtnBg.png"))
                )
                DecorationArtwork.ACTION -> listOf(RawDecorationPreview("动作预览", DecorationImageFile("${row.rawName}.gif")))
                else -> emptyList()
            }
            val image = DecorationImageFile(file)
            RawDecorationItem(row.id, row.name, row.quality, row.description, ways.ways[row.id].orEmpty(),
                image, image, previews, row.specialDescription.orEmpty(), policy.platform)
        }
        return DecorationParseResult(if (items.isEmpty()) emptyList() else listOf(RawDecorationSection("默认", items)), issues)
    }
}
