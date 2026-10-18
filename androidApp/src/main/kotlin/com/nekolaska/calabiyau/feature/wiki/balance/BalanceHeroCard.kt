package com.nekolaska.calabiyau.feature.wiki.balance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceMetric
import com.nekolaska.calabiyau.feature.wiki.balance.model.CharacterMeta
import com.nekolaska.calabiyau.feature.wiki.balance.model.HeroBalanceData

/** Mobile-first reading order: identity + win rate, followed by four labelled metrics. */
@Composable
internal fun BalanceHeroCard(
    modifier: Modifier = Modifier,
    rank: Int,
    hero: HeroBalanceData,
    compareHero: HeroBalanceData?,
    meta: CharacterMeta?,
    positionName: String,
    highlightField: BalanceMetric,
    hasCompare: Boolean,
    scoreAvailable: Boolean,
    compareScoreAvailable: Boolean
) {
    // An absent/zero-filled comparison record is not a measured baseline.
    val baseline = compareHero?.takeIf { it.winRate > 0 }
    Surface(
        modifier = modifier.fillMaxWidth().semantics { isTraversalGroup = true },
        shape = smoothCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints(Modifier.padding(16.dp)) {
            val compact = maxWidth < (280 * LocalDensity.current.fontScale).dp
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (compact) {
                    HeroIdentity(rank, hero, meta, positionName)
                    BalanceCardMetric(
                        field = BalanceMetric.WIN_RATE, current = hero.winRate, baseline = baseline?.winRate,
                        hasCompare = hasCompare, isHighlight = highlightField == BalanceMetric.WIN_RATE,
                        prominent = true
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HeroIdentity(rank, hero, meta, positionName, Modifier.weight(1f))
                        BalanceCardMetric(
                            field = BalanceMetric.WIN_RATE, current = hero.winRate, baseline = baseline?.winRate,
                            hasCompare = hasCompare, isHighlight = highlightField == BalanceMetric.WIN_RATE,
                            prominent = true, alignment = Alignment.End
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                val metrics = listOf(BalanceMetric.SELECT_RATE, BalanceMetric.KD, BalanceMetric.DAMAGE, BalanceMetric.SCORE)
                // Four columns normally; two columns for narrow screens or large accessibility fonts.
                metrics.chunked(if (compact) 2 else 4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { metric ->
                            val current = when (metric) {
                                BalanceMetric.SELECT_RATE -> hero.selectRate
                                BalanceMetric.KD -> hero.kd
                                BalanceMetric.DAMAGE -> hero.damageAve
                                BalanceMetric.SCORE -> hero.score.takeIf { scoreAvailable }
                                BalanceMetric.WIN_RATE -> hero.winRate
                            }
                            val previous = baseline?.let {
                                when (metric) {
                                    BalanceMetric.SELECT_RATE -> it.selectRate
                                    BalanceMetric.KD -> it.kd
                                    BalanceMetric.DAMAGE -> it.damageAve
                                    BalanceMetric.SCORE -> it.score.takeIf { compareScoreAvailable }
                                    BalanceMetric.WIN_RATE -> it.winRate
                                }
                            }
                            BalanceCardMetric(
                                modifier = Modifier.weight(1f), field = metric, current = current, baseline = previous,
                                hasCompare = hasCompare, isHighlight = highlightField == metric
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroIdentity(rank: Int, hero: HeroBalanceData, meta: CharacterMeta?, positionName: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(48.dp).clip(smoothCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            // Always visible under Coil while a portrait loads or if its CDN fails.
            Text(hero.heroName.take(1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!meta?.imageUrl.isNullOrBlank()) {
                AsyncImage(model = meta.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(meta?.name ?: hero.heroName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("#$rank · ${positionName.ifBlank { "超弦体" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BalanceCardMetric(
    modifier: Modifier = Modifier,
    field: BalanceMetric,
    current: Double?,
    baseline: Double?,
    hasCompare: Boolean,
    isHighlight: Boolean,
    prominent: Boolean = false,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    val difference = field.difference(current, baseline)
    Column(modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(field.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            field.format(current),
            style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            fontWeight = if (prominent || isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (hasCompare) {
            Text("原 ${field.format(baseline)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (difference != null) {
                Text(
                    difference, style = MaterialTheme.typography.labelSmall,
                    color = when {
                        difference.startsWith("+") -> MaterialTheme.colorScheme.tertiary
                        difference.startsWith("−") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
