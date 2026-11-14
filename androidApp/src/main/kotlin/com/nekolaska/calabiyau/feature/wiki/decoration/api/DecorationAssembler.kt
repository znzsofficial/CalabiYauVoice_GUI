package com.nekolaska.calabiyau.feature.wiki.decoration.api

import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationCatalogPolicy
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationImageFile
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationItem
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationPreview
import com.nekolaska.calabiyau.feature.wiki.decoration.model.DecorationSection
import com.nekolaska.calabiyau.feature.wiki.decoration.model.RawDecorationSection

/** Both complete Lua directories and HTML directories have already established their own identities/text. */
internal object DecorationAssembler {
    fun unresolvedImageFiles(sections: List<RawDecorationSection>): Set<String> = sections
        .flatMap { it.items }.flatMap { it.imageFiles }
        .filter { it.originalUrl.isBlank() && it.fileName.isNotBlank() }.map { it.fileName }.toSet()

    fun assemble(
        sections: List<RawDecorationSection>,
        policy: DecorationCatalogPolicy,
        imageUrls: Map<String, String> = emptyMap(),
        notice: String? = null
    ): List<DecorationSection> = sections.filter { it.items.isNotEmpty() }.mapIndexed { index, section ->
        val uniqueItems = section.items.distinct()
        fun identity(raw: com.nekolaska.calabiyau.feature.wiki.decoration.model.RawDecorationItem) = keyOf(
            listOf(policy.pageName, raw.platform, section.key.removeSuffix("#0"), raw.id.toString(),
                raw.icon.fileName.replace('_', ' '), raw.name)
        )
        val identityCounts = uniqueItems.groupingBy(::identity).eachCount()
        DecorationSection(section.title, uniqueItems.map { raw ->
            // Length-prefixed components cannot collide on delimiter characters. File spaces/underscores are MediaWiki aliases.
            val baseKey = identity(raw)
            val key = if (identityCounts.getValue(baseKey) == 1) baseKey else baseKey + keyOf(
                listOf(raw.quality.toString(), raw.description, raw.specialDescription, raw.source,
                    raw.icon.fileName, raw.icon.originalUrl, raw.image.fileName, raw.image.originalUrl) +
                    raw.extraPreviews.flatMap { listOf(it.label, it.image.fileName, it.image.originalUrl) }
            )
            DecorationItem(raw.id, raw.name, raw.quality, raw.description, raw.specialDescription, raw.source,
                raw.icon.resolve(imageUrls), raw.image.resolve(imageUrls), raw.extraPreviews.map { preview ->
                    DecorationPreview(preview.label, preview.image.resolve(imageUrls))
                }, raw.platform, raw.icon.fileName, key)
        }, if (index == 0) notice else null)
    }

    private fun keyOf(parts: List<String>) = parts.joinToString("") { "${it.length}:$it" }

    private fun DecorationImageFile.resolve(urls: Map<String, String>): String = originalUrl.ifBlank {
        urls[fileName] ?: urls[fileName.replace('_', ' ')] ?: urls[fileName.replace(' ', '_')].orEmpty()
    }
}
