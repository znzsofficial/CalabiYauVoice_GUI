package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.ui.LoadState
import com.nekolaska.calabiyau.core.ui.OfflineBanner
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropRate
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropRateSelection

// FlowRow uses spacing to decide whether both labels fit, but doesn't charge the gap to a
// source name on a line of its own. SpaceBetween then puts the percentage at the trailing edge.
private val rateColumns = object : Arrangement.Horizontal by Arrangement.SpaceBetween {
    override val spacing = 12.dp
}

internal fun LazyListScope.dropRateItems(
    state: LoadState<List<DropRate>>, selection: DropRateSelection, showAll: Boolean, onShowAll: (Boolean) -> Unit
) {
    item("rate-info") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("每抽一件时抽中该物品的概率。它不等于开箱至少获得一件的概率。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.isOffline && !state.isShowingCachedPrefetch) OfflineBanner(ageMs = state.cacheAgeMs)
            if (state.error != null || (state.isOffline && !state.isShowingCachedPrefetch)) {
                DropErrorBanner(if (state.error != null && state.data.isEmpty()) "单件抽中率暂不可用" else "单件抽中率未能更新", "重试") {
                    state.reload(forceRefresh = true)
                }
            }
            if (state.isShowingCachedPrefetch || state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (selection.total > 0) {
                val summary = when {
                    selection.allBelowThreshold && !showAll -> "所有单件率均低于 0.005%，列出前 ${selection.rows.size} 条"
                    selection.rows.size < selection.total -> "${selection.rows.size} 条来源记录，已隐藏 ${selection.total - selection.rows.size} 条低于 0.005% 的记录"
                    else -> "${selection.total} 条来源记录"
                }
                Text(summary, style = MaterialTheme.typography.labelMedium)
                if (selection.rows.any { it.rate.percent.signum() == 0 }) {
                    Text("Wiki 的概率精确到小数点后三位，显示 0% 不表示一定不会掉落。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showAll || selection.rows.size < selection.total) TextButton(onClick = { onShowAll(!showAll) }) {
                    Text(if (showAll) "恢复默认显示" else "显示全部 ${selection.total} 条")
                }
            } else if (!state.isLoading && state.error == null && !state.isShowingCachedPrefetch) {
                Text(if (state.isOffline) "缓存中未列出该品质的单件抽中率。" else "Wiki 未列出该品质的单件抽中率。",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    items(selection.rows, key = { "rate:${it.key}" }, contentType = { "drop-rate" }) { (_, rate) ->
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxWidth().dropItemMotion(this).semantics(mergeDescendants = true) {}, shape = smoothCornerShape(12.dp), color = colors.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Honor the measured label and system font size. When both fit, all rates end on
                // the same column; otherwise wrap the whole percentage below the source name.
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = rateColumns,
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(rate.source, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(rate.displayPercent, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold, color = colors.primary, softWrap = false)
                }
                Text(listOf(rate.type, rate.pool, rate.quality?.let { "Q$it" } ?: "品质未标注").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
