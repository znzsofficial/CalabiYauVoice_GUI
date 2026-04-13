package com.nekolaska.calabiyau.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.data.WeaponDetailApi
import com.nekolaska.calabiyau.data.WeaponDetailApi.WeaponDetail

// ════════════════════════════════════════════════════════
//  武器详情页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeaponDetailScreen(
    weaponName: String,
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    onOpenWeaponSkins: ((String) -> Unit)? = null
) {
    val state = rememberLoadState<WeaponDetail?>(null, key = weaponName) { force ->
        WeaponDetailApi.fetchWeaponDetail(weaponName, force)
    }
    val wikiUrl = remember(weaponName) {
        val encoded = java.net.URLEncoder.encode(weaponName, "UTF-8").replace("+", "%20")
        "https://wiki.biligame.com/klbq/$encoded"
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(weaponName, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenWikiUrl(wikiUrl) }) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "在浏览器中打开")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            isDataEmpty = { it == null },
            loading = { mod -> WeaponDetailSkeleton(mod) }
        ) { detail ->
            WeaponDetailContent(
                detail = detail!!,
                onOpenWikiUrl = onOpenWikiUrl,
                onOpenWeaponSkins = onOpenWeaponSkins
            )
        }
    }
}

@Composable
private fun WeaponDetailContent(
    detail: WeaponDetail,
    onOpenWikiUrl: (String) -> Unit,
    onOpenWeaponSkins: ((String) -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 头部：武器图片 + 名称 + 类型 ──
        item(key = "header") {
            WeaponHeaderCard(detail)
        }

        // ── 武器介绍 ──
        if (detail.description.isNotBlank()) {
            item(key = "description") {
                WeaponDescriptionCard(detail.description)
            }
        }

        // ── 武器数据 ──
        item(key = "stats") {
            WeaponStatsCard(detail)
        }

        // ── 伤害表 ──
        if (detail.damageTable.isNotEmpty()) {
            item(key = "damage") {
                WeaponDamageCard(detail)
            }
        }

        // ── 冷却时间（战术道具） ──
        if (detail.cooldowns.isNotEmpty()) {
            item(key = "cooldown") {
                WeaponCooldownCard(detail.cooldowns)
            }
        }

        // ── 武器外观跳转 ──
        if (onOpenWeaponSkins != null) {
            item(key = "skin_nav") {
                FilledTonalButton(
                    onClick = { onOpenWeaponSkins(detail.name) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("查看武器外观")
                }
            }
        }

        // ── 子页面导航 ──
        if (detail.subPages.isNotEmpty()) {
            item(key = "sub_pages") {
                WeaponSubPagesCard(detail.subPages, onOpenWikiUrl)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ────────────────────────────────────────────
//  头部卡片
// ────────────────────────────────────────────

@Composable
private fun WeaponHeaderCard(detail: WeaponDetail) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            // 武器图片
            if (detail.imageUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = detail.imageUrl,
                        contentDescription = detail.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // 武器名
            Text(
                detail.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // 标签行：使用者 / 类型 / 开火模式 / 获得方式
            val hasChips = detail.user.isNotBlank() || detail.type.isNotBlank()
                    || detail.fireMode.isNotBlank() || detail.obtainMethod.isNotBlank()
            if (hasChips) {
                Spacer(Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (detail.user.isNotBlank()) {
                        InfoChip(detail.user, Icons.Outlined.Person,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (detail.type.isNotBlank()) {
                        InfoChip(detail.type, Icons.Outlined.Category,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    if (detail.fireMode.isNotBlank()) {
                        InfoChip(detail.fireMode, Icons.Outlined.FlashOn,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    if (detail.obtainMethod.isNotBlank()) {
                        InfoChip(detail.obtainMethod, Icons.Outlined.Lock,
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.onSurface)
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
private fun WeaponDescriptionCard(description: String) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.Description, "武器介绍")
            Spacer(Modifier.height(10.dp))
            Text(description, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ────────────────────────────────────────────
//  武器数据卡片
// ────────────────────────────────────────────

@Composable
private fun WeaponStatsCard(detail: WeaponDetail) {
    val stats = buildList {
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
    }
    if (stats.isEmpty()) return

    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.BarChart, "武器数据")
            Spacer(Modifier.height(12.dp))

            val rows = stats.chunked(2)
            rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            Text(value, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                    if (row.size < 2) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  伤害表卡片
// ────────────────────────────────────────────

@Composable
private fun WeaponDamageCard(detail: WeaponDetail) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.GpsFixed, "武器伤害")

            // 倍率信息
            if (detail.baseDamage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (detail.baseDamage.isNotBlank()) {
                        Text("基础伤害: ${detail.baseDamage}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (detail.headMultiplier.isNotBlank()) {
                        Text("头部×${detail.headMultiplier}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (detail.upperMultiplier.isNotBlank()) {
                        Text("上肢×${detail.upperMultiplier}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (detail.lowerMultiplier.isNotBlank()) {
                        Text("下肢×${detail.lowerMultiplier}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 判断是否为简单伤害表（无部位区分）
            val isSimpleTable = detail.damageTable.all { it.upper.isBlank() && it.lower.isBlank() }

            if (isSimpleTable) {
                // 简单伤害表（如焚焰者）：键值对展示
                detail.damageTable.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                    Surface(
                        shape = smoothCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                row.distance,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                row.head,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            } else {
                // 标准伤害表：距离/头部/上肢/下肢 四列
                Surface(
                    shape = smoothCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("距离", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text("头部", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center)
                        Text("上肢", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center)
                        Text("下肢", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center)
                    }
                }

                Spacer(Modifier.height(4.dp))

                detail.damageTable.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(row.distance, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                        Text(row.head, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error)
                        Text(row.upper, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center)
                        Text(row.lower, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
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
        shape = smoothCornerShape(24.dp),
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
                    shape = smoothCornerShape(12.dp),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when {
                            page.displayName.contains("武器") -> Icons.Outlined.GpsFixed
                            page.displayName.contains("语音") -> Icons.Outlined.RecordVoiceOver
                            page.displayName.contains("画廊") -> Icons.Outlined.PhotoLibrary
                            else -> Icons.AutoMirrored.Outlined.Article
                        }
                        Icon(icon, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(page.displayName, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.Timer, "冷却时间")
            Spacer(Modifier.height(12.dp))

            cooldowns.entries.forEachIndexed { index, (mode, seconds) ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
                Surface(
                    shape = smoothCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            mode,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = smoothCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "${seconds}秒",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeaponDetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 头部卡片骨架
        SkeletonCard {
            Column(Modifier.padding(20.dp)) {
                ShimmerBox(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape = smoothCornerShape(12.dp)
                )
                Spacer(Modifier.height(12.dp))
                ShimmerBox(Modifier.width(140.dp).height(24.dp))
                Spacer(Modifier.height(10.dp))
                SkeletonChipRow(count = 3)
            }
        }
        // 武器数据卡片骨架
        SkeletonCard {
            Column(Modifier.padding(20.dp)) {
                SkeletonSectionTitle()
                Spacer(Modifier.height(12.dp))
                repeat(3) { row ->
                    if (row > 0) Spacer(Modifier.height(12.dp))
                    SkeletonStatRow()
                }
            }
        }
        // 伤害表卡片骨架
        SkeletonCard {
            Column(Modifier.padding(20.dp)) {
                SkeletonSectionTitle()
                Spacer(Modifier.height(12.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(32.dp), shape = smoothCornerShape(8.dp))
                Spacer(Modifier.height(4.dp))
                repeat(3) {
                    Spacer(Modifier.height(4.dp))
                    ShimmerBox(Modifier.fillMaxWidth().height(28.dp))
                }
            }
        }
    }
}


