package com.nekolaska.calabiyau.feature.wiki.bio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.CatalogEmptyResults
import com.nekolaska.calabiyau.core.ui.SelectionOptionRow
import com.nekolaska.calabiyau.core.ui.WikiSelectionSheet
import com.nekolaska.calabiyau.core.ui.catalogKeys
import com.nekolaska.calabiyau.feature.wiki.bio.model.DeckCardOption
import com.nekolaska.calabiyau.feature.wiki.bio.model.normalizeDeckQuality

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BioDeckCardSelectorSheet(
    cards: List<DeckCardOption>,
    selectedIds: List<String>,
    images: Map<String, String?>,
    onDismiss: () -> Unit,
    onToggle: (DeckCardOption) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val uniqueKeyedCards = remember(cards) {
        val keys = catalogKeys(cards) { "${it.cardId}#${it.index}#${it.name}" }
        cards.zip(keys)
    }
    val filteredCards = remember(uniqueKeyedCards, query) {
        val term = query.trim()
        if (term.isEmpty()) uniqueKeyedCards
        else uniqueKeyedCards.filter { (card, _) ->
            card.name.contains(term, ignoreCase = true) ||
                normalizeDeckQuality(card.quality).contains(term, ignoreCase = true)
        }
    }

    WikiSelectionSheet(
        title = "选择卡牌（已选 ${selectedIds.size}）",
        onDismiss = onDismiss,
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = "搜索卡牌名 / 品质…"
    ) {
        if (filteredCards.isEmpty()) {
            item(key = "empty") {
                CatalogEmptyResults(
                    message = if (cards.isEmpty()) "当前阵营无可选卡牌" else "没有匹配的卡牌"
                )
            }
        }
        items(filteredCards, key = { (_, uniqueKey) -> uniqueKey }, contentType = { "deck-card-option" }) { (card, _) ->
            val quality = normalizeDeckQuality(card.quality)
            val rarityLevel = when (quality) {
                "完美" -> 4
                "卓越" -> 3
                "精致" -> 2
                else -> 0
            }
            val qualityColor = bioRarityColor(rarityLevel)
            val imageUrl = images[card.cardId]
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                SelectionOptionRow(
                    label = card.name,
                    selected = card.cardId in selectedIds,
                    onClick = { onToggle(card) },
                    leading = {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = AppShapes.chip,
                            color = qualityColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, qualityColor.copy(alpha = 0.35f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = card.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = qualityColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                )
                if (quality.isNotBlank() || card.index < 0) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.chip)
                            .clickable { onToggle(card) }
                            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                    ) {
                        if (quality.isNotBlank()) {
                            Surface(
                                shape = AppShapes.chip,
                                color = qualityColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, qualityColor.copy(alpha = 0.32f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(qualityColor, CircleShape)
                                    )
                                    Text(
                                        text = quality,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        if (card.index < 0) {
                            Text(
                                text = "可加入卡组，暂不能生成分享码",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }
        }
    }
}
