package com.nekolaska.calabiyau.feature.weapon.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.feature.weapon.detail.WeaponDetailApi.WeaponDetail
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.InfoChip
import com.nekolaska.calabiyau.core.ui.SectionTitle
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.SkeletonCard
import com.nekolaska.calabiyau.core.ui.SkeletonChipRow
import com.nekolaska.calabiyau.core.ui.SkeletonSectionTitle
import com.nekolaska.calabiyau.core.ui.SkeletonStatRow
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import java.net.URLEncoder

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
    val state = rememberLoadState<WeaponDetail?>(
        null,
        key = weaponName
    ) { force ->
        WeaponDetailApi.fetchWeaponDetail(weaponName, force)
    }
    val wikiUrl = remember(weaponName) {
        val encoded = URLEncoder.encode(weaponName, "UTF-8").replace("+", "%20")
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
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    FilledTonalIconButton(onClick = { onOpenWikiUrl(wikiUrl) }) {
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
                onOpenSkins = onOpenWeaponSkins
            )
        }
    }
}

@Composable
private fun WeaponDetailContent(
    detail: WeaponDetail,
    onOpenWikiUrl: (String) -> Unit,
    onOpenSkins: ((String) -> Unit)? = null,
    onOpenCharacter: ((String) -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 头部：武器图片 + 名称 + 类型 ──
        item(key = "header") {
            Box(Modifier.padding(horizontal = 16.dp)) { WeaponHeaderCard(detail) }
        }

        // ── 武器介绍 ──
        if (detail.description.isNotBlank()) {
            item(key = "description") {
                Box(Modifier.padding(horizontal = 16.dp)) { WeaponDescriptionCard(detail.description) }
            }
        }

        // ── 获取途径 ──
        if (detail.obtainMethod.isNotBlank()) {
            item(key = "obtain") {
                Box(Modifier.padding(horizontal = 16.dp)) { WeaponObtainCard(method = detail.obtainMethod) }
            }
        }

        // ── 数据属性 (满级) ──
        item(key = "stats") {
            Box(Modifier.padding(horizontal = 16.dp)) { WeaponStatsCard(detail = detail) }
        }

        // ── 伤害数据 ──
        val distanceRows = detail.damageTable.filter { it.upper.isNotBlank() || it.lower.isNotBlank() }
        val simpleRows = detail.damageTable.filter { it.upper.isBlank() && it.lower.isBlank() }
        
        if (detail.baseDamage.isNotBlank() || distanceRows.isNotEmpty()) {
            item("damage_table") {
                Box(Modifier.padding(horizontal = 16.dp)) { 
                    WeaponDamageCard(detail, distanceRows) 
                }
            }
        }

        // ── 补充说明 / 其他伤害数据 ──
        if (simpleRows.isNotEmpty()) {
            item("damage_tips") {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    WeaponSimpleDamageCard(simpleRows)
                }
            }
        }

        // ── 冷却时间 ──
        if (detail.cooldowns.isNotEmpty()) {
            item("cooldowns") {
                Box(Modifier.padding(horizontal = 16.dp)) { WeaponCooldownCard(cooldowns = detail.cooldowns) }
            }
        }

        // ── 外观与角色快捷入口 ──
        if (onOpenSkins != null || onOpenCharacter != null) {
            item(key = "shortcuts") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onOpenSkins != null) {
                        FilledTonalButton(
                            onClick = { onOpenSkins(detail.name) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("查看武器外观")
                        }
                    }

                    if (onOpenCharacter != null) {
                        FilledTonalButton(
                            onClick = { onOpenCharacter(detail.name) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("查看角色信息")
                        }
                    }
                }
            }
        }

        // ── 子页面导航 ──
        if (detail.subPages.isNotEmpty()) {
            item(key = "sub_pages") {
                Box(Modifier.padding(horizontal = 16.dp)) { WeaponSubPagesCard(detail.subPages, onOpenWikiUrl) }
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

            // 标签行
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
                        InfoChip(
                            detail.user, Icons.Outlined.Person,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
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
                    if (detail.obtainMethod.isNotBlank()) {
                        InfoChip(
                            detail.obtainMethod, Icons.Outlined.Lock,
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.onSurface
                        )
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
//  武器获取途径
// ────────────────────────────────────────────

@Composable
private fun WeaponObtainCard(method: String) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(Icons.Outlined.Inventory2, "获取途径")
            Spacer(Modifier.height(10.dp))
            Text(method, style = MaterialTheme.typography.bodyLarge,
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
private fun WeaponDamageCard(detail: WeaponDetail, distanceRows: List<WeaponDetailApi.DamageRow>) {
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

            if (distanceRows.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))

                val groupedTables = distanceRows.groupBy { row ->
                    if (row.distance.startsWith("移动端·")) "移动端" else "PC"
                }

                listOf("PC", "移动端").forEach { tableName ->
                    val rows = groupedTables[tableName].orEmpty()
                    if (rows.isEmpty()) return@forEach

                    if (tableName == "移动端" && groupedTables["PC"]?.isNotEmpty() == true) {
                        Spacer(Modifier.height(16.dp))
                    }

                    Surface(
                        color = if (tableName == "移动端") {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = smoothCornerShape(12.dp)
                    ) {
                        Text(
                            text = tableName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (tableName == "移动端") {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

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

                    rows.forEachIndexed { index, row ->
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
                            Text(normalizeDistanceLabel(row.distance), Modifier.weight(1f),
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
        shape = smoothCornerShape(24.dp),
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
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = smoothCornerShape(12.dp)
                    ) {
                        Text(
                            text = row.sectionTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
                    shape = smoothCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (row.actionTitle == "补充" || row.value.length > 20) {
                        // 如果是长文本补充说明，改为垂直排列
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
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
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
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
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
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
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 头部卡片骨架
        Box(Modifier.padding(horizontal = 16.dp)) {
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
        }
        // 武器数据卡片骨架
        Box(Modifier.padding(horizontal = 16.dp)) {
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
        }
        // 伤害表卡片骨架
        Box(Modifier.padding(horizontal = 16.dp)) {
            SkeletonCard {
                Column(Modifier.padding(20.dp)) {
                    SkeletonSectionTitle()
                    Spacer(Modifier.height(12.dp))
                    ShimmerBox(
                        Modifier.fillMaxWidth().height(32.dp),
                        shape = smoothCornerShape(8.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    repeat(3) {
                        Spacer(Modifier.height(4.dp))
                        ShimmerBox(Modifier.fillMaxWidth().height(28.dp))
                    }
                }
            }
        }
    }
}

