package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
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
    AggregatePageScaffold(title = "弦溯战线", onBack = onBack, backdrop = backdrop) { insets, pageBackdrop ->
        Box(
            Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets),
            contentAlignment = Alignment.TopCenter
        ) {
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            LazyColumn(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppSpacing.screen,
                    end = AppSpacing.screen,
                    top = AppSpacing.medium,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)
            ) {
                item("lookup") {
                    DropLookupHeroCard(
                        backdrop = pageBackdrop,
                        fontScale = fontScale,
                        onOpenTask = { task -> onNavigateTo(task.toWikiRoute()) }
                    )
                }
                item("equipment") {
                    EquipmentModuleGridSection(
                        title = "物资与装备",
                        links = extractionEquipmentLinks,
                        backdrop = pageBackdrop,
                        fontScale = fontScale,
                        open = ::open
                    )
                }
                item("guides") {
                    GuideDirectoryCard(
                        title = "玩法资料",
                        links = extractionGuideLinks,
                        backdrop = pageBackdrop,
                        open = ::open
                    )
                }
            }
        }
    }
}

@Composable
private fun DropLookupHeroCard(
    backdrop: Backdrop,
    fontScale: Float,
    onOpenTask: (DropTask) -> Unit
) {
    val shape = smoothCornerShape(14.dp)
    val colors = MaterialTheme.colorScheme
    val material = rememberHubPanelMaterial(
        glassAlpha = 0.34f,
        tone = colors.surfaceContainerLow,
        ink = colors.onSurface,
        supportingInk = colors.onSurfaceVariant,
        tintPlainGlass = true
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true }
            .hubPanelGlass(backdrop, shape, material),
        shape = shape,
        color = material.containerColor,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.primary))
            Column(
                Modifier.fillMaxWidth().padding(AppSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                ) {
                    Text(
                        "掉落查询",
                        modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() },
                        style = AppTextStyles.sectionTitle
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        shape = smoothCornerShape(6.dp),
                        color = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f))
                    ) {
                        Text(
                            "维里昂裂隙",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val minTile = 120.dp * fontScale
                    if (maxWidth >= minTile * 2 + AppSpacing.medium) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                        ) {
                            DropTask.entries.forEach { task ->
                                DropActionButton(
                                    task = task,
                                    modifier = Modifier.weight(1f),
                                    open = { onOpenTask(task) }
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                            DropTask.entries.forEach { task ->
                                DropActionButton(
                                    task = task,
                                    modifier = Modifier.fillMaxWidth(),
                                    open = { onOpenTask(task) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropActionButton(task: DropTask, modifier: Modifier, open: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = smoothCornerShape(10.dp)
    if (task == DropTask.ITEMS) {
        Button(
            onClick = open,
            modifier = modifier.heightIn(min = 50.dp),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.medium))
            Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    } else {
        val surface = colors.surfaceContainerHigh
        val ink = colors.onSurface
        val primary = colors.primary
        val translucent = LocalLiquidGlassEnabled.current.value
        val hasWallpaper = LocalHasWallpaper.current
        val container = remember(surface, ink, primary, translucent, hasWallpaper) {
            when {
                !translucent -> surface
                hasWallpaper -> hubReadableTint(surface, ink, 0.84f, primary)
                else -> surface.copy(alpha = 0.84f)
            }
        }
        FilledTonalButton(
            onClick = open,
            modifier = modifier.heightIn(min = 50.dp),
            shape = shape,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.65f)),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = container,
                contentColor = ink
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp), tint = primary)
            Spacer(Modifier.width(AppSpacing.medium))
            Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EquipmentModuleGridSection(
    title: String,
    links: List<TopicLink>,
    backdrop: Backdrop,
    fontScale: Float,
    open: (TopicLink) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hasWallpaper = LocalHasWallpaper.current
    Column(
        modifier = Modifier.fillMaxWidth().semantics { isTraversalGroup = true },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (hasWallpaper) {
            val headerShape = smoothCornerShape(8.dp)
            val headerMaterial = rememberHubPanelMaterial(
                glassAlpha = 0.32f,
                tone = colors.surfaceContainerLow,
                ink = colors.onSurface,
                supportingInk = colors.onSurfaceVariant,
                tintPlainGlass = true
            )
            Surface(
                modifier = Modifier.hubPanelGlass(backdrop, headerShape, headerMaterial),
                shape = headerShape,
                color = headerMaterial.containerColor,
                contentColor = colors.onSurface,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
            ) {
                SectionHeadingRow(title, Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        } else {
            SectionHeadingRow(title, Modifier.padding(horizontal = 2.dp))
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val minCardWidth = 136.dp * fontScale
            val twoColumns = maxWidth >= minCardWidth * 2 + 10.dp
            if (twoColumns) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    links.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { link ->
                                EquipmentModuleCard(
                                    link = link,
                                    backdrop = backdrop,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    open = { open(link) }
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    links.forEach { link ->
                        EquipmentModuleCard(
                            link = link,
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            open = { open(link) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeadingRow(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, smoothCornerShape(2.dp))
        )
        Text(title, Modifier.semantics { heading() }, style = AppTextStyles.sectionTitle)
    }
}

@Composable
private fun EquipmentModuleCard(
    link: TopicLink,
    backdrop: Backdrop,
    modifier: Modifier,
    open: () -> Unit
) {
    val shape = smoothCornerShape(14.dp)
    val colors = MaterialTheme.colorScheme
    val material = rememberHubPanelMaterial(
        glassAlpha = 0.32f,
        tone = colors.surfaceContainerLow,
        ink = colors.onSurface,
        supportingInk = colors.onSurfaceVariant,
        tintPlainGlass = true
    )
    val dotColor = equipmentModuleAccent(link.route)
    Surface(
        onClick = open,
        modifier = modifier
            .hubPanelGlass(backdrop, shape, material)
            .semantics { role = Role.Button },
        shape = shape,
        color = material.containerColor,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = smoothCornerShape(9.dp),
                    color = colors.surfaceContainerHigh,
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            topicIcon(link.route),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = colors.primary
                        )
                    }
                }
                Box(
                    Modifier
                        .padding(top = 2.dp, end = 2.dp)
                        .size(7.dp)
                        .background(dotColor, CircleShape)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    link.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    equipmentModuleSubtitle(link.route),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GuideDirectoryCard(
    title: String,
    links: List<TopicLink>,
    backdrop: Backdrop,
    open: (TopicLink) -> Unit
) {
    val shape = smoothCornerShape(14.dp)
    val colors = MaterialTheme.colorScheme
    val material = rememberHubPanelMaterial(
        glassAlpha = 0.30f,
        tone = colors.surfaceContainerLow,
        ink = colors.onSurface,
        supportingInk = colors.onSurfaceVariant,
        tintPlainGlass = true
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true }
            .hubPanelGlass(backdrop, shape, material),
        shape = shape,
        color = material.containerColor,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.large)) {
            SectionHeadingRow(
                title = title,
                modifier = Modifier.padding(horizontal = AppSpacing.screen, vertical = AppSpacing.xSmall)
            )
            Spacer(Modifier.height(AppSpacing.xSmall))
            links.forEachIndexed { index, link ->
                TopicDirectoryRow(link) { open(link) }
                if (index < links.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(
                            start = AppSpacing.screen + GuideIconBoxSize + AppSpacing.iconGap,
                            end = AppSpacing.screen
                        ),
                        color = colors.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

private val GuideIconBoxSize = 32.dp

@Composable
private fun TopicDirectoryRow(link: TopicLink, open: () -> Unit) {
    val isWeb = link.route == null
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = open)
            .heightIn(min = 50.dp)
            .padding(horizontal = AppSpacing.screen, vertical = AppSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.iconGap)
    ) {
        Surface(
            modifier = Modifier.size(GuideIconBoxSize),
            shape = smoothCornerShape(8.dp),
            color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.50f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(topicIcon(link.route), null, Modifier.size(18.dp), tint = colors.primary)
            }
        }
        if (isWeb) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val compact = maxWidth < 176.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
                val sourceLabel = "Wiki 原文"
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)
                ) {
                    Text(
                        link.label,
                        Modifier.align(Alignment.CenterVertically).padding(end = AppSpacing.medium),
                        style = AppTextStyles.listItemTitle,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        shape = smoothCornerShape(6.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                        ) {
                            Text(
                                if (compact) "原文" else sourceLabel,
                                modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(sourceLabel) },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                null,
                                Modifier.size(14.dp),
                                tint = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                link.label,
                Modifier.weight(1f),
                style = AppTextStyles.listItemTitle,
                fontWeight = FontWeight.Medium
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun equipmentModuleAccent(route: WikiRoute?): Color {
    val colors = MaterialTheme.colorScheme
    return when (route) {
        WikiRoute.ExtractionGuns -> colors.error
        WikiRoute.ExtractionArmor -> colors.tertiary
        is WikiRoute.ExtractionCatalog ->
            if (route.kind == ExtractionCatalogKind.ATTACHMENTS) colors.secondary else colors.primary
        else -> colors.primary
    }
}

private fun equipmentModuleSubtitle(route: WikiRoute?): String = when (route) {
    WikiRoute.ExtractionGuns -> "品质档对比"
    WikiRoute.ExtractionArmor -> "护甲与生命"
    is WikiRoute.ExtractionCatalog ->
        if (route.kind == ExtractionCatalogKind.ATTACHMENTS) "槽位与效果" else "价值与占格"
    else -> ""
}

private fun topicIcon(route: WikiRoute?): ImageVector = when (route) {
    WikiRoute.ExtractionGuns -> Icons.Outlined.GpsFixed
    WikiRoute.ExtractionArmor -> Icons.Outlined.Shield
    WikiRoute.ExtractionMode -> Icons.Outlined.Explore
    WikiRoute.ExtractionWorkshop -> Icons.Outlined.BuildCircle
    is WikiRoute.ExtractionCatalog -> if (route.kind == ExtractionCatalogKind.ATTACHMENTS) Icons.Outlined.Tune else Icons.Outlined.Inventory2
    else -> Icons.AutoMirrored.Outlined.Article
}
