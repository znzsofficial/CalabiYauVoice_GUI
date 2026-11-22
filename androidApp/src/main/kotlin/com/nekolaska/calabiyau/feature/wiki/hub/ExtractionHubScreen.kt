package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropTask
import com.nekolaska.calabiyau.feature.wiki.extraction.model.EXTRACTION_MODE_PAGE
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionCatalogKind
import com.nekolaska.calabiyau.feature.wiki.extraction.model.extractionArticleUrl
import com.nekolaska.calabiyau.feature.wiki.extraction.source.ExtractionCatalogRemoteSource

internal val extractionEquipmentLinks = listOf(
    ExtractionCatalogKind.LOOT.let { TopicLink(it.title, it.pageName, WikiRoute.ExtractionCatalog(it)) },
    TopicLink("枪械图鉴", "$EXTRACTION_MODE_PAGE/枪械图鉴", WikiRoute.ExtractionGuns),
    TopicLink("防具图鉴", "$EXTRACTION_MODE_PAGE/防具图鉴", WikiRoute.ExtractionArmor),
    ExtractionCatalogKind.ATTACHMENTS.let { TopicLink(it.title, it.pageName, WikiRoute.ExtractionCatalog(it)) }
)

internal val extractionGuideLinks = listOf(
    TopicLink("模式介绍", EXTRACTION_MODE_PAGE, WikiRoute.ExtractionMode),
    TopicLink("工坊", "$EXTRACTION_MODE_PAGE/工坊", WikiRoute.ExtractionWorkshop),
    TopicLink("掉落表格", ExtractionCatalogRemoteSource.CONTAINERS_PAGE, null)
)

internal data class TopicLink(val label: String, val article: String, val route: WikiRoute?) {
    val originalUrl = extractionArticleUrl(article)
}

/** Opening the directory itself does not fetch any catalogs. */
@Composable
internal fun ExtractionHubScreen(
    onBack: () -> Unit,
    onNavigateTo: (WikiRoute) -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    backdrop: Backdrop
) {
    fun open(link: TopicLink) = link.route?.let(onNavigateTo) ?: onOpenWikiUrl(link.originalUrl)
    AggregatePageScaffold(title = "弦溯战线", onBack = onBack, backdrop = backdrop) { insets, _ ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = AppSpacing.screen, end = AppSpacing.screen, top = AppSpacing.medium, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)
            ) {
                item("lookup") {
                    TopicSection("掉落查询", accent = true) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val minTile = 120.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
                            if (maxWidth >= minTile * 2 + AppSpacing.medium) {
                                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                                    DropTask.entries.forEach { task -> DropAction(task, Modifier.weight(1f)) { onNavigateTo(task.toWikiRoute()) } }
                                }
                            } else Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                                DropTask.entries.forEach { task -> DropAction(task, Modifier.fillMaxWidth()) { onNavigateTo(task.toWikiRoute()) } }
                            }
                        }
                    }
                }
                item("equipment") { TopicDirectory("物资与装备", extractionEquipmentLinks, ::open) }
                item("guides") { TopicDirectory("玩法资料", extractionGuideLinks, ::open) }
            }
        }
    }
}

@Composable
private fun TopicSection(title: String, accent: Boolean = false, content: @Composable () -> Unit) {
    Surface(shape = AppShapes.card, color = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = AppSpacing.large)) {
            Text(title, Modifier.semantics { heading() }.padding(vertical = AppSpacing.xSmall), style = AppTextStyles.sectionTitle)
            if (accent) Spacer(Modifier.height(AppSpacing.small))
            content()
        }
    }
}

@Composable
private fun DropAction(task: DropTask, modifier: Modifier, open: () -> Unit) {
    FilledTonalButton(onClick = open, modifier = modifier.heightIn(min = 52.dp), shape = AppShapes.compactCard,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Icon(if (task == DropTask.ITEMS) Icons.Outlined.Search else Icons.Outlined.LocationOn, null, Modifier.size(20.dp))
        Spacer(Modifier.width(AppSpacing.medium))
        Text(task.title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun TopicDirectory(title: String, links: List<TopicLink>, open: (TopicLink) -> Unit) {
    TopicSection(title) {
        links.forEachIndexed { index, link ->
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { open(link) }.heightIn(min = 48.dp)
                .padding(vertical = AppSpacing.small), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                Icon(topicIcon(link.route), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(link.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (link.route == null) {
                    Text("Wiki 原文", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (index < links.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 34.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun topicIcon(route: WikiRoute?): ImageVector = when (route) {
    WikiRoute.ExtractionGuns -> Icons.Outlined.GpsFixed
    WikiRoute.ExtractionArmor -> Icons.Outlined.Shield
    WikiRoute.ExtractionMode -> Icons.Outlined.Explore
    WikiRoute.ExtractionWorkshop -> Icons.Outlined.BuildCircle
    is WikiRoute.ExtractionCatalog -> if (route.kind == ExtractionCatalogKind.ATTACHMENTS) Icons.Outlined.Tune else Icons.Outlined.Inventory2
    else -> Icons.AutoMirrored.Outlined.Article
}
