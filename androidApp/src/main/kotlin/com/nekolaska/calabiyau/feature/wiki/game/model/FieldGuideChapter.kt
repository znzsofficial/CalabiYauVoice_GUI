package com.nekolaska.calabiyau.feature.wiki.game.model

import com.nekolaska.calabiyau.core.wiki.reading.GuideText

/** One H2 from a gameplay article. Each table row is a separate reading/list item. */
data class FieldGuideChapter(val title: String, val blocks: List<FieldGuideBlock>)

data class FieldGuideBlock(
    val kind: Kind,
    val content: GuideText,
    val headingLevel: Int = 0,
    /** On a caption only: number of following reading blocks that belong to this table. */
    val captionedBlockCount: Int = 0
) {
    enum class Kind { HEADING, TEXT, RECORD }
}

internal data class FieldGuideSection(val label: String, val blocks: IntRange, val depth: Int)

/** Source order is also menu order. A caption is a leaf of the current article heading, never a parent. */
internal fun FieldGuideChapter.sections(): List<FieldGuideSection> = buildList {
    val parents = mutableListOf<Pair<Int, Int>>() // HTML heading level and its index in this outline
    blocks.forEachIndexed { index, block ->
        if (block.headingLevel > 0) {
            while (parents.isNotEmpty() && parents.last().first >= block.headingLevel) {
                val sectionIndex = parents.removeAt(parents.lastIndex).second
                val section = get(sectionIndex)
                set(sectionIndex, section.copy(blocks = section.blocks.first until index))
            }
            add(FieldGuideSection(block.content.text, index..blocks.lastIndex, parents.size))
            parents.add(block.headingLevel to lastIndex)
        } else if (block.captionedBlockCount > 0) {
            val label = block.content.text
            val parentLabel = parents.lastOrNull()?.let { get(it.second).label } ?: title
            if (label != parentLabel) add(FieldGuideSection(label, index..(index + block.captionedBlockCount), parents.size))
        }
    }
}

internal fun List<FieldGuideSection>.sectionAt(blockIndex: Int): FieldGuideSection? = lastOrNull { blockIndex in it.blocks }
