package com.nekolaska.calabiyau.feature.wiki.bio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.*
import com.nekolaska.calabiyau.feature.wiki.bio.model.*

@Composable
internal fun BioDeckCardSelectorSheet(
    cards: List<DeckCardOption>, selectedIds: List<String>, images: Map<String, String?>,
    onDismiss: () -> Unit, onToggle: (DeckCardOption) -> Unit
) {
    WikiSelectionSheet(title = "选择卡牌", onDismiss = onDismiss) {
        if (cards.isEmpty()) item(key = "empty") { CatalogEmptyResults(message = "当前阵营无可选卡牌") }
        items(cards, key = { "${it.cardId}#${it.index}" }, contentType = { "deck-card-option" }) { card ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SelectionOptionRow(label = card.name, selected = card.cardId in selectedIds, onClick = { onToggle(card) },
                    leading = {
                        AsyncImage(model = images[card.cardId], contentDescription = null,
                            modifier = Modifier.size(48.dp), contentScale = ContentScale.Crop)
                    })
                Text(normalizeDeckQuality(card.quality), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
                if (card.index < 0) Text("可加入卡组，暂不能生成分享码", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
            }
        }
    }
}
