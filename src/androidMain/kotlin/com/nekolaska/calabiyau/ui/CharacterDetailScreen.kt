package com.nekolaska.calabiyau.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.data.CharacterDetailApi
import com.nekolaska.calabiyau.data.CharacterDetailApi.CharacterDetail

// ════════════════════════════════════════════════════════
//  角色详情页 —— 原生客户端版 (MD3 Expressive)
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    characterName: String,
    portraitUrl: String? = null,   // 从列表页传入的立绘 URL（可选）
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit,
    onOpenCostumes: ((String) -> Unit)? = null,
    onOpenWeaponSkins: ((String) -> Unit)? = null
) {
    val state = rememberLoadState<CharacterDetail?>(null, key = characterName) { force ->
        CharacterDetailApi.fetchCharacterDetail(characterName, force)
    }
    val wikiUrl = remember(characterName) {
        val encoded = java.net.URLEncoder.encode(characterName, "UTF-8").replace("+", "%20")
        "https://wiki.biligame.com/klbq/$encoded"
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        characterName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
            loading = { mod -> CharacterDetailSkeleton(mod) }
        ) { detail ->
            CharacterDetailContent(
                detail = detail!!,
                portraitUrl = portraitUrl,
                onOpenWikiUrl = onOpenWikiUrl,
                onOpenCostumes = onOpenCostumes,
                onOpenWeaponSkins = onOpenWeaponSkins
            )
        }
    }
}

// ────────────────────────────────────────────
//  详情内容
// ────────────────────────────────────────────

@Composable
private fun CharacterDetailContent(
    detail: CharacterDetail,
    portraitUrl: String?,
    onOpenWikiUrl: (String) -> Unit,
    onOpenCostumes: ((String) -> Unit)? = null,
    onOpenWeaponSkins: ((String) -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 头部：立绘 + 基本信息 ──
        item(key = "header") {
            HeaderSection(detail = detail, portraitUrl = portraitUrl)
        }

        // ── 个性语录 ──
        if (detail.quote.isNotBlank()) {
            item(key = "quote") {
                QuoteCard(quote = detail.quote)
            }
        }

        // ── 角色简介 ──
        if (detail.description.isNotBlank() || detail.summary.isNotBlank()) {
            item(key = "description") {
                DescriptionCard(
                    avatarUrl = detail.avatarUrl,
                    summary = detail.summary,
                    description = detail.description
                )
            }
        }

        // ── 详细属性 ──
        item(key = "attributes") {
            AttributesCard(detail = detail)
        }

        // ── 武器信息 ──
        if (detail.weaponName.isNotBlank()) {
            item(key = "weapon") {
                WeaponInfoCard(detail = detail)
            }
        }

        // ── 外观跳转 ──
        if (onOpenCostumes != null || onOpenWeaponSkins != null) {
            item(key = "skin_nav") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onOpenCostumes != null) {
                        FilledTonalButton(
                            onClick = { onOpenCostumes(detail.name) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Checkroom, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("角色时装")
                        }
                    }
                    if (onOpenWeaponSkins != null && detail.weaponName.isNotBlank()) {
                        FilledTonalButton(
                            onClick = { onOpenWeaponSkins(detail.weaponName) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("武器外观")
                        }
                    }
                }
            }
        }

        // ── 角色技能 ──
        if (detail.skills.isNotEmpty()) {
            item(key = "skills") {
                SkillsCard(skills = detail.skills)
            }
        }

        // ── 超弦体特性 / 兴趣爱好 / 饮食习惯 ──
        val personalItems = buildList {
            if (detail.traits.isNotBlank()) add("超弦体特性" to detail.traits)
            if (detail.hobbies.isNotBlank()) add("兴趣爱好" to detail.hobbies)
            if (detail.diet.isNotBlank()) add("饮食习惯" to detail.diet)
        }
        if (personalItems.isNotEmpty()) {
            item(key = "personal") {
                PersonalInfoCard(items = personalItems)
            }
        }

        // ── 角色故事 & 相关剧情 ──
        if (detail.stories.isNotEmpty()) {
            item(key = "stories") {
                StoriesCard(
                    stories = detail.stories,
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }
        }

        // ── 观测语录 ──
        if (detail.observerQuote.isNotBlank()) {
            item(key = "observer_quote") {
                ObserverQuoteCard(quote = detail.observerQuote)
            }
        }

        // ── 更新改动历史 ──
        if (detail.updateHistory.isNotEmpty()) {
            item(key = "update_history") {
                UpdateHistoryCard(history = detail.updateHistory)
            }
        }

        // ── 子页面导航 ──
        if (detail.subPages.isNotEmpty()) {
            item(key = "sub_pages") {
                SubPagesCard(
                    subPages = detail.subPages,
                    onOpenWikiUrl = onOpenWikiUrl
                )
            }
        }

        // 底部留白
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ────────────────────────────────────────────
//  头部区域：头像 + 名称 + 阵营/定位标签
// ────────────────────────────────────────────

@Composable
private fun HeaderSection(detail: CharacterDetail, portraitUrl: String? = null) {
    val headerImage = portraitUrl ?: detail.avatarUrl
    val cardColor = CardDefaults.cardColors().containerColor
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 立绘/头像区域
            if (headerImage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (portraitUrl != null) 320.dp else 200.dp)
                ) {
                    AsyncImage(
                        model = headerImage,
                        contentDescription = detail.name,
                        contentScale = ContentScale.Crop,
                        // 立绘：跳过顶部约15%空白，从偏上位置开始裁剪
                        alignment = if (portraitUrl != null) BiasAlignment(0f, -0.7f)
                                    else Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 底部渐变（颜色与 Card 背景一致）
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        cardColor
                                    )
                                )
                            )
                    )
                }
            }

            Column(
                modifier = Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (headerImage == null) 20.dp else 0.dp,
                    bottom = 20.dp
                )
            ) {
                // 角色名
                Text(
                    detail.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                // 英文名 / 日文名
                if (detail.englishName.isNotBlank() || detail.japaneseName.isNotBlank()) {
                    val subName = buildString {
                        if (detail.englishName.isNotBlank()) append(detail.englishName)
                        if (detail.japaneseName.isNotBlank()) {
                            if (isNotEmpty()) append(" / ")
                            append(detail.japaneseName)
                        }
                    }
                    Text(
                        subName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 标签行：阵营 + 定位 + 身份
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (detail.faction.isNotBlank()) {
                        InfoChip(
                            label = detail.faction,
                            icon = Icons.Outlined.Shield,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (detail.role.isNotBlank()) {
                        InfoChip(
                            label = detail.role,
                            icon = Icons.Outlined.WorkOutline,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (detail.identity.isNotBlank()) {
                        InfoChip(
                            label = detail.identity,
                            icon = Icons.Outlined.Badge,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    if (detail.activeArea.isNotBlank()) {
                        InfoChip(
                            label = detail.activeArea,
                            icon = Icons.Outlined.LocationOn,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  个性语录卡片
// ────────────────────────────────────────────

@Composable
private fun QuoteCard(quote: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Outlined.FormatQuote,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                quote,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ────────────────────────────────────────────
//  角色简介 / 描述卡片
// ────────────────────────────────────────────

@Composable
private fun DescriptionCard(
    avatarUrl: String? = null,
    summary: String,
    description: String
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(icon = Icons.Outlined.Person, title = "角色简介")
            Spacer(Modifier.height(10.dp))

            // 头像 + 简介文本
            Row {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (summary.isNotBlank()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  详细属性卡片（网格布局）
// ────────────────────────────────────────────

@Composable
private fun AttributesCard(detail: CharacterDetail) {
    val attributes = buildList {
        if (detail.gender.isNotBlank()) add("性别" to detail.gender)
        if (detail.age.isNotBlank()) add("年龄" to detail.age)
        if (detail.height.isNotBlank()) add("身高" to detail.height)
        if (detail.weight.isNotBlank()) add("体重" to detail.weight)
        if (detail.birthday.isNotBlank()) add("生日" to detail.birthday)
        if (detail.activeArea.isNotBlank()) add("活动区域" to detail.activeArea)
        if (detail.cnVoiceActor.isNotBlank()) add("中文声优" to detail.cnVoiceActor)
        if (detail.jpVoiceActor.isNotBlank()) add("日文声优" to detail.jpVoiceActor)
    }
    if (attributes.isEmpty()) return

    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(icon = Icons.Outlined.Info, title = "基本信息")
            Spacer(Modifier.height(12.dp))

            // 两列网格
            val rows = attributes.chunked(2)
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
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    // 填充空位
                    if (row.size < 2) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  武器信息卡片
// ────────────────────────────────────────────

@Composable
private fun WeaponInfoCard(detail: CharacterDetail) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(icon = Icons.Outlined.GpsFixed, title = "专属武器")
            Spacer(Modifier.height(12.dp))

            // 武器名 + 类型
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.GpsFixed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        detail.weaponName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (detail.weaponType.isNotBlank()) {
                        Text(
                            detail.weaponType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 武器介绍
            if (detail.weaponIntro.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    detail.weaponIntro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  个人信息卡片（特性 / 爱好 / 饮食）
// ────────────────────────────────────────────

@Composable
private fun PersonalInfoCard(items: List<Pair<String, String>>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(icon = Icons.Outlined.FavoriteBorder, title = "个人特质")
            Spacer(Modifier.height(12.dp))

            items.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ────────────────────────────────────────────
//  观测语录卡片
// ────────────────────────────────────────────

@Composable
private fun ObserverQuoteCard(quote: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "观测语录",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                quote,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ────────────────────────────────────────────
//  子页面导航卡片
// ────────────────────────────────────────────

@Composable
private fun SubPagesCard(
    subPages: List<CharacterDetailApi.SubPage>,
    onOpenWikiUrl: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(icon = Icons.AutoMirrored.Outlined.Article, title = "更多内容")
            Spacer(Modifier.height(8.dp))

            subPages.forEachIndexed { index, page ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
                Surface(
                    onClick = { onOpenWikiUrl(page.wikiUrl) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when {
                            page.displayName.contains("语音") -> Icons.Outlined.RecordVoiceOver
                            page.displayName.contains("誓约") -> Icons.Outlined.FavoriteBorder
                            page.displayName.contains("画廊") || page.displayName.contains("时装") -> Icons.Outlined.PhotoLibrary
                            page.displayName.contains("档案") -> Icons.Outlined.FolderOpen
                            page.displayName.contains("武器") -> Icons.Outlined.GpsFixed
                            else -> Icons.AutoMirrored.Outlined.Article
                        }
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            page.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  角色技能卡片
// ────────────────────────────────────────────

@Composable
private fun SkillsCard(skills: List<CharacterDetailApi.SkillInfo>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(icon = Icons.Outlined.AutoAwesome, title = "角色技能")
            Spacer(Modifier.height(12.dp))

            skills.forEachIndexed { index, skill ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                SkillItem(skill = skill)
            }
        }
    }
}

@Composable
private fun SkillItem(skill: CharacterDetailApi.SkillInfo) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        // 技能标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 技能图标（有图片则显示图片，否则显示槽位字母）
            if (skill.iconUrl != null) {
                AsyncImage(
                    model = skill.iconUrl,
                    contentDescription = skill.name,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            skill.slot,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                skill.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        // 技能描述（可展开）
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                skill.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp, top = 6.dp, end = 4.dp)
            )
        }

        // 折叠时显示预览
        if (!expanded) {
            Text(
                skill.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 48.dp, top = 4.dp, end = 4.dp)
            )
        }
    }
}

// ────────────────────────────────────────────
//  角色故事 & 相关剧情卡片
// ────────────────────────────────────────────

@Composable
private fun StoriesCard(
    stories: List<CharacterDetailApi.StoryEntry>,
    onOpenWikiUrl: (String) -> Unit
) {
    val characterStories = stories.filter { it.section == "角色故事" }
    val relatedStories = stories.filter { it.section == "相关剧情" }

    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            if (characterStories.isNotEmpty()) {
                SectionTitle(icon = Icons.AutoMirrored.Outlined.MenuBook, title = "角色故事")
                Spacer(Modifier.height(10.dp))
                StoryRow(stories = characterStories, onOpenWikiUrl = onOpenWikiUrl)
            }

            if (relatedStories.isNotEmpty()) {
                if (characterStories.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                }
                SectionTitle(icon = Icons.Outlined.AutoStories, title = "相关剧情")
                Spacer(Modifier.height(10.dp))
                StoryRow(stories = relatedStories, onOpenWikiUrl = onOpenWikiUrl)
            }
        }
    }
}

@Composable
private fun StoryRow(
    stories: List<CharacterDetailApi.StoryEntry>,
    onOpenWikiUrl: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(stories, key = { it.pageUrl }) { story ->
            StoryCard(story = story, onClick = { onOpenWikiUrl(story.pageUrl) })
        }
    }
}

@Composable
private fun StoryCard(
    story: CharacterDetailApi.StoryEntry,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box {
            // 封面图
            AsyncImage(
                model = story.imageUrl,
                contentDescription = story.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            // 底部渐变 + 标题
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}



// ────────────────────────────────────────────
//  更新改动历史
// ────────────────────────────────────────────

@Composable
private fun UpdateHistoryCard(history: List<CharacterDetailApi.UpdateEntry>) {
    var expanded by remember { mutableStateOf(false) }
    // 默认只显示前 3 条
    val visibleHistory = if (expanded) history else history.take(3)

    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(icon = Icons.Outlined.History, title = "更新改动历史")
            Spacer(Modifier.height(12.dp))

            visibleHistory.forEach { entry ->
                // 日期标题
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        entry.date,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 改动条目
                entry.changes.forEach { change ->
                    Row(
                        Modifier.padding(start = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 6.dp, top = 1.dp)
                        )
                        Text(
                            change,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // 展开/收起按钮
            if (history.size > 3) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (expanded) "收起" else "查看全部 ${history.size} 条更新")
                }
            }
        }
    }
}

@Composable
private fun CharacterDetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 头部卡片骨架
        SkeletonCard {
            ShimmerBox(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            Column(Modifier.padding(20.dp)) {
                ShimmerBox(Modifier.width(120.dp).height(24.dp))
                Spacer(Modifier.height(8.dp))
                SkeletonTextLine(widthFraction = 0.7f)
                Spacer(Modifier.height(12.dp))
                SkeletonChipRow(count = 3)
            }
        }
        // 语录卡片骨架
        SkeletonCard {
            Row(Modifier.padding(20.dp)) {
                ShimmerBox(Modifier.size(28.dp), shape = RoundedCornerShape(6.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonTextLine()
                    Spacer(Modifier.height(6.dp))
                    SkeletonTextLine(widthFraction = 0.7f)
                }
            }
        }
        // 属性卡片骨架
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
}
