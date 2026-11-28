package com.nekolaska.calabiyau.feature.weapon.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppTextColors
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.InfoChip
import com.nekolaska.calabiyau.core.ui.OpenWikiActionButton
import com.nekolaska.calabiyau.core.ui.PreviewImage
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.SectionTitle
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.ShimmerGroup
import com.nekolaska.calabiyau.core.ui.SkeletonChipRow
import com.nekolaska.calabiyau.core.ui.SkeletonSectionTitle
import com.nekolaska.calabiyau.core.ui.SkeletonTextLine
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.ui.tacticalTabular
import com.nekolaska.calabiyau.feature.weapon.detail.WeaponDetailApi.WeaponDetail
import com.nekolaska.calabiyau.feature.weapon.detail.model.WeaponDamageCoefficients
import com.nekolaska.calabiyau.feature.weapon.detail.model.WeaponDataSection
import com.nekolaska.calabiyau.feature.weapon.detail.model.WeaponDiagram
import util.wikiPathEncode

// ════════════════════════════════════════════════════════
//  武器详情页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

private val WeaponCardRadius = 22.dp
private val WeaponMediaRadius = 18.dp
private val WeaponMediaHeight = 176.dp
private val WeaponMediaInset = 16.dp
private val WeaponHeaderInset = 20.dp
private val WeaponDetailSidePadding = 16.dp
private val WeaponDetailTopPadding = 14.dp
private val WeaponFrostedSurface = Color(0xCC141720)
private val WeaponFrostedRim = Color.White.copy(alpha = 0.14f)

@Composable
private fun weaponCardShape() = smoothCornerShape(WeaponCardRadius)

@Composable
private fun weaponMediaShape() = smoothCornerShape(WeaponMediaRadius)

@Composable
private fun weaponCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeaponDetailScreen(
    weaponName: String,
    imageUrl: String? = null,
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    onOpenWeaponSkins: ((String) -> Unit)? = null,
    onOpenCharacter: ((String) -> Unit)? = null,
    canOpenCharacter: (String) -> Boolean = { false },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) {
    val state = rememberLoadState<WeaponDetail?>(
        null,
        key = weaponName
    ) { force ->
        WeaponDetailApi.fetchWeaponDetail(weaponName, force)
    }
    val wikiUrl = remember(weaponName) {
        "https://wiki.biligame.com/klbq/${weaponName.wikiPathEncode()}"
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        weaponName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    RefreshActionButton(
                        onClick = { state.reload(forceRefresh = true) },
                        enabled = !state.isLoading
                    )
                    OpenWikiActionButton(wikiUrl = wikiUrl, onOpenWikiUrl = onOpenWikiUrl, contentDescription = "在浏览器中打开")
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
            ApiResourceContent(
                state = state,
                modifier = Modifier.fillMaxSize(),
                isDataEmpty = { it == null },
                enablePullToRefresh = false,
                loading = { mod ->
                    WeaponDetailSkeleton(
                        modifier = mod,
                        imageUrl = imageUrl,
                        weaponName = weaponName,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            ) { detail ->
                WeaponDetailContent(
                    detail = detail ?: return@ApiResourceContent,
                    onOpenWikiUrl = onOpenWikiUrl,
                    onOpenSkins = onOpenWeaponSkins,
                    onOpenCharacter = onOpenCharacter,
                    canOpenCharacter = canOpenCharacter,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        }
    }
}

private enum class WeaponSection(val label: String) { OVERVIEW("概览"), DATA("数据"), UPGRADES("增幅") }

@Composable
private fun WeaponSectionTabs(
    sections: List<WeaponSection>, current: WeaponSection, onSelect: ((WeaponSection) -> Unit)? = null
) {
    val loading = onSelect == null
    val placeholder = if (loading) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier
    Box {
        PrimaryTabRow(selectedTabIndex = sections.indexOf(current), modifier = placeholder) {
            sections.forEach { tab ->
                Tab(
                    selected = current == tab,
                    enabled = !loading && sections.size > 1,
                    onClick = { onSelect?.invoke(tab) },
                    text = { Text(tab.label) }
                )
            }
        }
        if (loading) ShimmerBox(Modifier.align(Alignment.Center).width(52.dp).height(16.dp), AppShapes.capsule)
    }
}

@Composable
private fun weaponSharedImageModifier(
    name: String,
    sharedScope: SharedTransitionScope?,
    visibilityScope: androidx.compose.animation.AnimatedVisibilityScope?
): Modifier {
    val imageBounds = Modifier.fillMaxSize().padding(WeaponMediaInset)
    return if (sharedScope != null && visibilityScope != null) with(sharedScope) {
        imageBounds.sharedElement(
            rememberSharedContentState(key = "weapon-image-$name"), visibilityScope,
            boundsTransform = { _, _ -> tween(500) }
        )
    } else imageBounds
}

@Composable
internal fun WeaponDetailContent(
    detail: WeaponDetail,
    onOpenWikiUrl: (String) -> Unit,
    onOpenSkins: ((String) -> Unit)? = null,
    onOpenCharacter: ((String) -> Unit)? = null,
    canOpenCharacter: (String) -> Boolean = { false },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) {
    var previewImage by remember(detail.name) { mutableStateOf<PreviewImage?>(null) }

    val stats = remember(detail) { weaponStats(detail) }
    val distanceRows = remember(detail) { detail.damageTable.filter { it.distance.contains("米") &&
        (it.upper.isNotBlank() || it.lower.isNotBlank() || it.head.isNotBlank()) } }
    val simpleRows = remember(detail) { detail.damageTable.filter { !it.distance.contains("米") && it.upper.isBlank() && it.lower.isBlank() } }
    val sections = buildList {
        add(WeaponSection.OVERVIEW)
        if (stats.isNotEmpty() || detail.baseDamage.isNotBlank() || distanceRows.isNotEmpty() || simpleRows.isNotEmpty() ||
            detail.damageDescription.isNotBlank() || detail.cooldowns.isNotEmpty() || detail.performanceData.isNotEmpty()) add(WeaponSection.DATA)
        if (detail.augmentations.isNotEmpty()) add(WeaponSection.UPGRADES)
    }
    var selected by rememberSaveable(detail.name) { mutableStateOf(WeaponSection.OVERVIEW) }
    val current = selected.takeIf { it in sections } ?: WeaponSection.OVERVIEW
    val pages = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize()) {
        // Even an overview-only weapon keeps the first row so a cold load cannot shift the image.
        WeaponSectionTabs(sections, current) { selected = it }
        pages.SaveableStateProvider("${detail.name}:${current.name}") {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = WeaponDetailSidePadding, end = WeaponDetailSidePadding, top = WeaponDetailTopPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (current) {
                    WeaponSection.OVERVIEW -> {
                        item("header") {
                            WeaponHeaderCard(detail, onPreviewImage = { previewImage = it }, onOpenCharacter = onOpenCharacter,
                                canOpenCharacter = canOpenCharacter, sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope)
                        }
                        if (detail.itemEffect.isNotBlank()) item("item_effect") { WeaponDescriptionCard(detail.itemEffect, "道具效果") }
                        if (detail.mechanics.isNotEmpty() || detail.diagrams.isNotEmpty()) item("weapon_mechanics") {
                            WeaponSupplementaryCard(if (detail.itemEffect.isNotBlank()) "道具数据" else "武器机制", detail.mechanics,
                                diagramCount = detail.diagrams.size, initiallyExpanded = true) {
                                detail.diagrams.forEach { diagram -> WeaponDiagramPreview(diagram) { previewImage = it } }
                            }
                        }
                        if (onOpenSkins != null) item("skins") {
                            FilledTonalButton(
                                onClick = { onOpenSkins(detail.name) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                shape = smoothCornerShape(18.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Outlined.Palette, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("查看武器外观", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (detail.subPages.isNotEmpty()) item("sub_pages") { WeaponSubPagesCard(detail.subPages, onOpenWikiUrl) }
                    }
                    WeaponSection.DATA -> {
                        if (detail.baseDamage.isNotBlank() || distanceRows.isNotEmpty()) item("damage") { WeaponDamageCard(detail, distanceRows) }
                        if (detail.damageDescription.isNotBlank()) item("damage_description") { WeaponDescriptionCard(detail.damageDescription, "伤害说明") }
                        if (simpleRows.isNotEmpty()) item("simple_damage") { WeaponSimpleDamageCard(simpleRows) }
                        if (detail.cooldowns.isNotEmpty()) item("cooldowns") { WeaponCooldownCard(detail.cooldowns) }
                        if (stats.isNotEmpty()) item("stats") { WeaponStatsCard(stats) }
                        if (detail.performanceData.isNotEmpty()) item("reference") { WeaponSupplementaryCard("详细性能（Wiki 参考）", detail.performanceData) }
                    }
                    WeaponSection.UPGRADES -> detail.augmentations.forEach { network ->
                        item("mode:${network.mode}") {
                            Text(
                                network.mode,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                        network.groups.forEachIndexed { index, group ->
                            item("group:${network.mode}:$index") {
                                Card(
                                    shape = weaponCardShape(),
                                    colors = weaponCardColors(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            group.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        group.options.forEach { option ->
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = smoothCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainer
                                            ) {
                                                Column(
                                                    Modifier.padding(14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            option.title,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.align(Alignment.CenterVertically)
                                                        )
                                                        option.energyCost?.let { cost ->
                                                            Surface(
                                                                shape = AppShapes.capsule,
                                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                                                modifier = Modifier.align(Alignment.CenterVertically)
                                                            ) {
                                                                Text(
                                                                    "$cost 能量点",
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        option.changes,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item("bottom_nav_spacer") { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }

    previewImage?.let { preview ->
        ImagePreviewDialog(
            model = preview.url,
            contentDescription = preview.title,
            onDismiss = { previewImage = null }
        )
    }
}

// ────────────────────────────────────────────
//  头部卡片
// ────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeaponHeaderCard(
    detail: WeaponDetail,
    onPreviewImage: ((PreviewImage) -> Unit)? = null,
    onOpenCharacter: ((String) -> Unit)?,
    canOpenCharacter: (String) -> Boolean,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) {
    var imageFailed by remember(detail.imageUrl) { mutableStateOf(false) }
    val showImage = detail.imageUrl != null && !imageFailed
    val stageShape = weaponMediaShape()
    Card(
        shape = weaponCardShape(),
        colors = weaponCardColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(WeaponHeaderInset)) {
            // 武器图片（支持点击放大预览与失败占位；sharedElement 仅附着在图片上，避免拉伸缩放提示徽标）
            if (detail.imageUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WeaponMediaHeight)
                        .clip(stageShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f)
                                )
                            )
                        )
                        .let { mod ->
                            if (showImage && onPreviewImage != null) {
                                mod.clickable(onClickLabel = "查看大图") {
                                    onPreviewImage(PreviewImage(detail.imageUrl, detail.name))
                                }
                            } else mod
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (showImage) {
                        AsyncImage(
                            model = detail.imageUrl,
                            contentDescription = detail.name,
                            contentScale = ContentScale.Fit,
                            onError = { imageFailed = true },
                            modifier = weaponSharedImageModifier(detail.name, sharedTransitionScope, animatedVisibilityScope)
                        )
                        if (onPreviewImage != null) {
                            Surface(
                                shape = AppShapes.capsule,
                                color = WeaponFrostedSurface,
                                border = BorderStroke(0.5.dp, WeaponFrostedRim),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.ZoomIn,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = Color.White
                                    )
                                    Text(
                                        "查看大图",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        Icon(
                            Icons.Outlined.GpsFixed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            val hasChips = detail.type.isNotBlank() || detail.fireMode.isNotBlank()
            if (hasChips) {
                if (detail.imageUrl != null) Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (detail.type.isNotBlank()) {
                        InfoChip(
                            detail.type, Icons.Outlined.Category,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (detail.fireMode.isNotBlank()) {
                        InfoChip(
                            detail.fireMode, Icons.Outlined.FlashOn,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            if (detail.description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(detail.description, style = MaterialTheme.typography.bodyLarge)
            }
            if (detail.obtainMethod.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("获取途径：${detail.obtainMethod}", style = MaterialTheme.typography.bodyMedium, color = AppTextColors.muted)
            }
            if (detail.users.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("使用者", style = AppTextStyles.statLabel, color = AppTextColors.muted)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    detail.users.forEach { name ->
                        if (onOpenCharacter != null && canOpenCharacter(name)) {
                            AssistChip(
                                onClick = { onOpenCharacter(name) },
                                shape = AppShapes.capsule,
                                label = { Text(name) },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp)) }
                            )
                        } else {
                            Box(Modifier.align(Alignment.CenterVertically).padding(vertical = 2.dp)) {
                                InfoChip(name, Icons.Outlined.Person, MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  武器介绍
// ────────────────────────────────────────────

@Composable
private fun WeaponDescriptionCard(description: String, title: String = "武器介绍") {
    Card(
        shape = weaponCardShape(),
        colors = weaponCardColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.Description, title)
            Spacer(Modifier.height(10.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ────────────────────────────────────────────
//  武器数据卡片
// ────────────────────────────────────────────

private fun weaponStats(detail: WeaponDetail): List<Pair<String, String>> = buildList {
    if (detail.fireRate.isNotBlank()) add("射速" to detail.fireRate)
    if (detail.mobileFireRate.isNotBlank()) add("移动端射速" to detail.mobileFireRate)
    if (detail.aimSpeed.isNotBlank()) add("瞄准速度" to detail.aimSpeed)
    if (detail.spreadControl.isNotBlank()) add("散射控制" to detail.spreadControl)
    if (detail.recoilControl.isNotBlank()) add("后坐力控制" to detail.recoilControl)
    if (detail.reloadSpeed.isNotBlank()) add("装填速度" to detail.reloadSpeed)
    if (detail.moveSpeedChange.isNotBlank()) add("移速变化" to detail.moveSpeedChange)
    if (detail.stringDamage.isNotBlank()) add("弦化伤害" to detail.stringDamage)
    if (detail.magCapacity.isNotBlank()) add("弹匣容量" to detail.magCapacity)
    if (detail.mobileMagCapacity.isNotBlank()) add("移动端弹匣" to detail.mobileMagCapacity)
    if (detail.maxAmmo.isNotBlank()) add("最大备弹数" to detail.maxAmmo)
    if (detail.secondaryAttack.isNotBlank()) add("辅助攻击" to detail.secondaryAttack)
    if (detail.magnification.isNotBlank()) add("放大倍率" to detail.magnification)
    detail.extraStats.forEach { (label, value) -> add(label to value) }
}

@Composable
private fun WeaponStatsCard(stats: List<Pair<String, String>>) {
    Card(
        shape = weaponCardShape(),
        colors = weaponCardColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.BarChart, "武器数据")
            Spacer(Modifier.height(14.dp))

            val rows = stats.chunked(2)
            rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, style = AppTextStyles.statLabel, color = AppTextColors.muted)
                            Spacer(Modifier.height(3.dp))
                            Text(value, style = AppTextStyles.statValue)
                        }
                    }
                    if (row.size < 2) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WeaponSupplementaryCard(
    title: String,
    sections: List<WeaponDataSection>,
    diagramCount: Int = 0,
    initiallyExpanded: Boolean = false,
    diagrams: @Composable () -> Unit = {}
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val itemCount = sections.sumOf { it.values.size } + diagramCount
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "weaponSuppChevron"
    )
    Card(
        shape = weaponCardShape(),
        colors = weaponCardColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 整行可点击折叠头 + 条数胶囊，替代孤立文本按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.chip)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    SectionTitle(Icons.Outlined.Info, title)
                }
                Surface(
                    shape = AppShapes.capsule,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "$itemCount 项",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180, easing = FastOutSlowInEasing))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    diagrams()
                    sections.forEach { section ->
                        Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (section.notice.isNotBlank()) {
                            Surface(
                                shape = smoothCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Text(
                                    section.notice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                        section.values.forEach { stat ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(stat.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stat.value, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeaponDiagramPreview(diagram: WeaponDiagram, onPreview: (PreviewImage) -> Unit) {
    var failed by remember(diagram.previewUrl) { mutableStateOf(false) }
    Surface(
        onClick = { onPreview(PreviewImage(diagram.previewUrl, "${diagram.title}（PNG 预览）")) },
        shape = smoothCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(diagram.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (!failed) {
                AsyncImage(
                    model = diagram.previewUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).aspectRatio(diagram.aspectRatio)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.ZoomIn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (failed) "打开 PNG 预览" else "放大 PNG 预览",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  伤害表卡片
// ────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeaponDamageCard(detail: WeaponDetail, distanceRows: List<WeaponDetailApi.DamageRow>) {
    var selectedPlatform by rememberSaveable(detail.name) { mutableStateOf("PC") }
    val groupedTables = remember(distanceRows) {
        distanceRows.groupBy { row ->
            if (row.distance.startsWith("移动端·")) "移动端" else "PC"
        }
    }
    val platforms = listOf("PC", "移动端").filter { groupedTables.containsKey(it) }
    val effectivePlatform = selectedPlatform.takeIf { it in platforms } ?: platforms.firstOrNull() ?: "PC"
    val coefficients = if (effectivePlatform == "移动端") detail.mobileDamageCoefficients else WeaponDamageCoefficients(
        detail.baseDamage, detail.headMultiplier, detail.upperMultiplier, detail.lowerMultiplier
    )

    Card(
        shape = weaponCardShape(),
        colors = weaponCardColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    SectionTitle(Icons.Outlined.GpsFixed, "武器伤害")
                }

                if (platforms.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        platforms.forEach { platform ->
                            FilterChip(
                                selected = effectivePlatform == platform,
                                onClick = { selectedPlatform = platform },
                                label = { Text(platform, maxLines = 1) },
                                shape = AppShapes.capsule
                            )
                        }
                    }
                }
            }

            // 倍率信息胶囊
            if (coefficients != null && coefficients.baseDamage.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (coefficients.baseDamage.isNotBlank()) {
                        DamageMultiplierBadge(
                            text = "基础 ${coefficients.baseDamage}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (coefficients.head.isNotBlank()) {
                        DamageMultiplierBadge(
                            text = "头部 ×${coefficients.head}",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (coefficients.upper.isNotBlank()) {
                        DamageMultiplierBadge(
                            text = "上肢 ×${coefficients.upper}",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    if (coefficients.lower.isNotBlank()) {
                        DamageMultiplierBadge(
                            text = "下肢 ×${coefficients.lower}",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val rows = groupedTables[effectivePlatform].orEmpty()
            val pelletOnly = rows.isNotEmpty() && rows.all { it.upper.isBlank() && it.lower.isBlank() }
            if (rows.isNotEmpty()) {
                val numericStyle = MaterialTheme.typography.bodyMedium.tacticalTabular()
                Spacer(Modifier.height(16.dp))

                Surface(
                    shape = smoothCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(
                            "距离",
                            Modifier.weight(1.35f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (pelletOnly) {
                            Text(
                                "总弹丸伤害",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                "头部",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "上肢",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "下肢",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                        )
                    }
                    val isChargeRow = row.distance.contains("蓄力")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // 蓄力行浅色底纹，与普通行形成视觉分组（如狙击枪谢幕曲的开镜蓄力伤害）
                            .clip(smoothCornerShape(10.dp))
                            .background(
                                if (isChargeRow) MaterialTheme.colorScheme.surfaceContainer
                                else Color.Transparent
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlowRow(
                            Modifier.weight(1.35f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                normalizeDistanceLabel(row.distance).substringBefore("·蓄力"),
                                style = numericStyle,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            if (isChargeRow) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = AppShapes.capsule,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                ) {
                                    Text(
                                        "蓄力",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        if (pelletOnly) {
                            Text(
                                row.head,
                                Modifier.weight(1f),
                                style = numericStyle,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                row.head,
                                Modifier.weight(1f),
                                style = numericStyle,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                row.upper,
                                Modifier.weight(1f),
                                style = numericStyle,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                row.lower,
                                Modifier.weight(1f),
                                style = numericStyle,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DamageMultiplierBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = AppShapes.capsule,
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
        )
    }
}

private data class SimpleDamageDisplayRow(
    val sectionTitle: String,
    val groupTitle: String?,
    val actionTitle: String,
    val value: String
)

@Composable
private fun WeaponSimpleDamageCard(simpleRows: List<WeaponDetailApi.DamageRow>) {
    Card(
        shape = weaponCardShape(),
        colors = weaponCardColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.Info, "其他伤害数据 / 补充说明")
            Spacer(Modifier.height(12.dp))

            val parsedRows = simpleRows.map { row -> parseSimpleDamageRow(row) }
            var lastSectionTitle: String? = null
            var lastGroupTitle: String? = null

            parsedRows.forEachIndexed { index, row ->
                if (row.sectionTitle != lastSectionTitle) {
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = AppShapes.capsule
                    ) {
                        Text(
                            text = row.sectionTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    lastSectionTitle = row.sectionTitle
                    lastGroupTitle = null
                }

                if (row.groupTitle != null && row.groupTitle != lastGroupTitle) {
                    Text(
                        text = row.groupTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                    lastGroupTitle = row.groupTitle
                }

                Surface(
                    shape = smoothCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (row.actionTitle == "补充" || row.value.length > 20) {
                        // 如果是长文本补充说明，改为垂直排列
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                row.actionTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                row.value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        // 短数值普通排列
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                row.actionTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                row.value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private fun parseSimpleDamageRow(row: WeaponDetailApi.DamageRow): SimpleDamageDisplayRow {
    val parts = row.distance.split('·').map { it.trim() }.filter { it.isNotBlank() }
    return when {
        parts.size >= 3 -> SimpleDamageDisplayRow(
            sectionTitle = normalizeSimpleSectionTitle(parts[0]),
            groupTitle = parts[1],
            actionTitle = parts.drop(2).joinToString(" · "),
            value = row.head
        )

        parts.size == 2 -> SimpleDamageDisplayRow(
            sectionTitle = normalizeSimpleSectionTitle(parts[0]),
            groupTitle = null,
            actionTitle = parts[1],
            value = row.head
        )

        else -> SimpleDamageDisplayRow(
            sectionTitle = "数据",
            groupTitle = null,
            actionTitle = row.distance,
            value = row.head
        )
    }
}

private fun normalizeDistanceLabel(label: String): String {
    return label.removePrefix("移动端·")
}

private fun normalizeSimpleSectionTitle(title: String): String {
    return when {
        title == "移动端" -> "移动端"
        title.startsWith("移动端") -> title
        else -> title
    }
}

// ────────────────────────────────────────────
//  子页面导航
// ────────────────────────────────────────────

@Composable
private fun WeaponSubPagesCard(
    subPages: List<WeaponDetailApi.SubPage>,
    onOpenWikiUrl: (String) -> Unit
) {
    Card(
        shape = weaponCardShape(),
        colors = weaponCardColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.AutoMirrored.Outlined.Article, "更多内容")
            Spacer(Modifier.height(8.dp))

            subPages.forEachIndexed { index, page ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
                Surface(
                    onClick = { onOpenWikiUrl(page.wikiUrl) },
                    shape = smoothCornerShape(14.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when {
                            page.displayName.contains("武器") -> Icons.Outlined.GpsFixed
                            page.displayName.contains("语音") -> Icons.Outlined.RecordVoiceOver
                            page.displayName.contains("画廊") -> Icons.Outlined.PhotoLibrary
                            else -> Icons.AutoMirrored.Outlined.Article
                        }
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = smoothCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    icon,
                                    null,
                                    Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            page.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  冷却时间卡片（战术道具）
// ────────────────────────────────────────────

@Composable
private fun WeaponCooldownCard(cooldowns: Map<String, Int>) {
    Card(
        shape = weaponCardShape(),
        colors = weaponCardColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(Icons.Outlined.Timer, "冷却时间")
            Spacer(Modifier.height(4.dp))

            cooldowns.entries.forEach { (mode, seconds) ->
                Surface(
                    shape = smoothCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            mode,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = AppShapes.capsule,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "${seconds}秒",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WeaponDetailSkeleton(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    weaponName: String = "",
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) = ShimmerGroup {
    val cardShape = weaponCardShape()
    val cardColors = weaponCardColors()
    val stageShape = weaponMediaShape()
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        WeaponSectionTabs(listOf(WeaponSection.OVERVIEW), WeaponSection.OVERVIEW)
        Box(Modifier.padding(start = WeaponDetailSidePadding, end = WeaponDetailSidePadding, top = WeaponDetailTopPadding)) {
            Card(shape = cardShape, colors = cardColors, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(WeaponHeaderInset)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WeaponMediaHeight)
                            .clip(stageShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUrl != null) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = weaponName,
                                contentScale = ContentScale.Fit,
                                modifier = weaponSharedImageModifier(weaponName, sharedTransitionScope, animatedVisibilityScope)
                            )
                        } else {
                            ShimmerBox(
                                modifier = Modifier.fillMaxSize(),
                                shape = stageShape
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SkeletonChipRow(count = 2)
                    Spacer(Modifier.height(12.dp))
                    SkeletonTextLine(widthFraction = 0.9f)
                    Spacer(Modifier.height(4.dp))
                    SkeletonTextLine(widthFraction = 0.62f)
                }
            }
        }
        // Overview initially shows the published mechanics, not the off-screen data table.
        Box(Modifier.padding(start = WeaponDetailSidePadding, end = WeaponDetailSidePadding, top = 14.dp)) {
            Card(shape = cardShape, colors = cardColors, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    SkeletonSectionTitle()
                    Spacer(Modifier.height(12.dp))
                    repeat(2) {
                        SkeletonTextLine(widthFraction = 0.8f)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

