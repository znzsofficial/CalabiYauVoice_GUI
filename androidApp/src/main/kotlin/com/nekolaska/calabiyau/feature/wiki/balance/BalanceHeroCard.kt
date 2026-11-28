package com.nekolaska.calabiyau.feature.wiki.balance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.TacticalBadge
import com.nekolaska.calabiyau.core.ui.TacticalCard
import com.nekolaska.calabiyau.core.ui.TacticalSpecTile
import com.nekolaska.calabiyau.core.ui.TacticalStyle
import com.nekolaska.calabiyau.core.ui.tacticalTabular
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceMetric
import com.nekolaska.calabiyau.feature.wiki.balance.model.CharacterMeta
import com.nekolaska.calabiyau.feature.wiki.balance.model.HeroBalanceData

/** Mobile-first reading order: identity + win rate, followed by four labelled tactical metric tiles. */
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
    val colors = MaterialTheme.colorScheme
    val topRank = rank <= 3
    TacticalCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true },
        compact = true,
        highlighted = topRank,
        accentColor = if (topRank) colors.primary else colors.outlineVariant.copy(alpha = 0.65f)
    ) {
        BoxWithConstraints(Modifier.padding(14.dp)) {
            val compact = maxWidth < (280 * LocalDensity.current.fontScale).dp
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (compact) {
                    HeroIdentity(rank, hero, meta, positionName, topRank)
                    BalancePrimaryMetric(
                        field = BalanceMetric.WIN_RATE,
                        current = hero.winRate,
                        baseline = baseline?.winRate,
                        hasCompare = hasCompare,
                        isHighlight = highlightField == BalanceMetric.WIN_RATE
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeroIdentity(rank, hero, meta, positionName, topRank, Modifier.weight(1f))
                        BalancePrimaryMetric(
                            field = BalanceMetric.WIN_RATE,
                            current = hero.winRate,
                            baseline = baseline?.winRate,
                            hasCompare = hasCompare,
                            isHighlight = highlightField == BalanceMetric.WIN_RATE,
                            alignment = Alignment.End
                        )
                    }
                }

                HorizontalDivider(color = TacticalStyle.dividerColor())

                val metrics = listOf(BalanceMetric.SELECT_RATE, BalanceMetric.KD, BalanceMetric.DAMAGE, BalanceMetric.SCORE)
                // Four columns normally; two columns for narrow screens or large accessibility fonts.
                metrics.chunked(if (compact) 2 else 4).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                            val difference = if (hasCompare) metric.difference(current, previous) else null
                            TacticalSpecTile(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                label = metric.label,
                                value = metric.format(current),
                                highlighted = highlightField == metric,
                                tabular = true,
                                supportingText = if (hasCompare) "对比 ${metric.format(previous)}" else null,
                                deltaText = difference,
                                deltaColor = deltaColor(difference)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroIdentity(
    rank: Int,
    hero: HeroBalanceData,
    meta: CharacterMeta?,
    positionName: String,
    topRank: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val frameShape = TacticalStyle.iconFrameShape
    val frameBorder = BorderStroke(
        1.dp,
        if (topRank) colors.primary.copy(alpha = 0.45f) else colors.outlineVariant.copy(alpha = 0.50f)
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(48.dp)
                .clip(frameShape)
                .background(colors.surfaceContainerHigh)
                .border(frameBorder, frameShape),
            contentAlignment = Alignment.Center
        ) {
            // Always visible under Coil while a portrait loads or if its CDN fails.
            Text(
                hero.heroName.take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant
            )
            if (!meta?.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = meta.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                meta?.name ?: hero.heroName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TacticalBadge(
                    text = "#$rank",
                    highlighted = topRank,
                    tabular = true
                )
                TacticalBadge(
                    text = positionName.ifBlank { "超弦体" }
                )
            }
        }
    }
}

@Composable
private fun BalancePrimaryMetric(
    field: BalanceMetric,
    current: Double?,
    baseline: Double?,
    hasCompare: Boolean,
    isHighlight: Boolean,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    val colors = MaterialTheme.colorScheme
    val difference = field.difference(current, baseline)
    Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            field.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isHighlight) colors.primary else colors.onSurfaceVariant,
            fontWeight = if (isHighlight) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            field.format(current),
            style = MaterialTheme.typography.headlineSmall.tacticalTabular(),
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) colors.primary else colors.onSurface
        )
        if (hasCompare) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "对比 ${field.format(baseline)}",
                    style = MaterialTheme.typography.labelSmall.tacticalTabular(),
                    color = colors.onSurfaceVariant
                )
                if (difference != null) {
                    Text(
                        difference,
                        style = MaterialTheme.typography.labelSmall.tacticalTabular(),
                        fontWeight = FontWeight.SemiBold,
                        color = deltaColor(difference)
                    )
                }
            }
        }
    }
}

@Composable
private fun deltaColor(difference: String?) = when {
    difference == null -> MaterialTheme.colorScheme.onSurfaceVariant
    difference.startsWith("+") -> MaterialTheme.colorScheme.tertiary
    difference.startsWith("−") -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
