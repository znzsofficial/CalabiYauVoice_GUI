package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.liquidGlass
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.feature.wiki.gallery.WallpaperApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WikiGameplayHubScreen(
    onBack: () -> Unit,
    onNavigateTo: (WikiHubPage) -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    AggregatePageScaffold(title = "玩法与养成", onBack = onBack) { innerPadding, backdrop ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 10.dp,
                end = 16.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ActionCard(
                    title = "战斗模式",
                    subtitle = "查看所有战斗模式详情",
                    icon = Icons.Outlined.SportsEsports,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.GAME_MODES) },
                    backdrop = backdrop
                )
            }
            item {
                ContentBlockCard(
                    title = "角色培养",
                    icon = Icons.Outlined.FavoriteBorder,
                    items = listOf(
                        "誓约" to "誓约",
                        "印迹" to "印迹"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    nativePages = mapOf(
                        "誓约" to { onNavigateTo(WikiHubPage.OATH) },
                        "印迹" to { onNavigateTo(WikiHubPage.IMPRINTS) }
                    ),
                    backdrop = backdrop
                )
            }
            item {
                ContentBlockCard(
                    title = "玩法系统",
                    icon = Icons.Outlined.SportsEsports,
                    items = listOf(
                        "弦化" to "弦化",
                        "弦能增幅网络" to "弦能增幅网络",
                        "特别行动" to "特别行动",
                        "赫尔墨斯" to "赫尔墨斯",
                        "超弦体定位" to "超弦体定位",
                        "赛事系统" to "赛事系统"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    backdrop = backdrop
                )
            }
            item {
                ContentBlockCard(
                    title = "移动端内容",
                    icon = Icons.Outlined.PhoneAndroid,
                    items = listOf(
                        "超弦体天赋" to "超弦体天赋",
                        "超弦推进卡牌" to "战斗模式/超弦推进",
                        "载具外观" to "极限推进模式载具外观",
                        "头像框" to "头像框"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    nativePages = mapOf(
                        "超弦体天赋" to { onNavigateTo(WikiHubPage.STRINGER_TALENTS) },
                        "超弦推进卡牌" to { onNavigateTo(WikiHubPage.STRINGER_PUSH_CARDS) },
                        "载具外观" to { onNavigateTo(WikiHubPage.VEHICLE_SKINS) },
                        "头像框" to { onNavigateTo(WikiHubPage.AVATAR_FRAMES) }
                    ),
                    backdrop = backdrop
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WikiCatalogHubScreen(
    onBack: () -> Unit,
    onNavigateTo: (WikiHubPage) -> Unit
) {
    AggregatePageScaffold(title = "外观与图鉴", onBack = onBack) { innerPadding, backdrop ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 10.dp,
                end = 16.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ActionCard(
                    title = "道具图鉴",
                    subtitle = "浏览功能道具、货币与礼盒礼包",
                    icon = Icons.Outlined.Inventory2,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.ITEMS) },
                    backdrop = backdrop
                )
            }
            item {
                ActionCard(
                    title = "时装筛选",
                    subtitle = "浏览全部角色时装与外观",
                    icon = Icons.Outlined.Checkroom,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.COSTUMES) },
                    backdrop = backdrop
                )
            }
            item {
                ActionCard(
                    title = "武器外观",
                    subtitle = "浏览全部武器外观与皮肤",
                    icon = Icons.Outlined.Palette,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.WEAPON_SKINS) },
                    backdrop = backdrop
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WikiExtensionHubScreen(
    onBack: () -> Unit,
    onNavigateTo: (WikiHubPage) -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    AggregatePageScaffold(title = "游戏延伸", onBack = onBack) { innerPadding, backdrop ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 10.dp,
                end = 16.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ActionCard(
                    title = "投票",
                    subtitle = "查看并参与当前 Wiki 投票",
                    icon = Icons.Outlined.HowToVote,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.VOTING) },
                    backdrop = backdrop
                )
            }
            item {
                ContentBlockCard(
                    title = "资料与世界观",
                    icon = Icons.Outlined.MoreHoriz,
                    items = listOf(
                        "剧情故事" to "剧情故事",
                        "游戏历史" to "游戏历史",
                        "四格漫画" to "官方四格漫画",
                        "联动" to "联动",
                        "梗百科" to "梗百科",
                        "游戏Tips" to "游戏Tips"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    nativePages = mapOf(
                        "剧情故事" to { onNavigateTo(WikiHubPage.STORY) },
                        "游戏历史" to { onNavigateTo(WikiHubPage.GAME_HISTORY) },
                        "四格漫画" to { onNavigateTo(WikiHubPage.COMICS) },
                        "联动" to { onNavigateTo(WikiHubPage.COLLABORATIONS) },
                        "梗百科" to { onNavigateTo(WikiHubPage.MEMES) },
                        "游戏Tips" to { onNavigateTo(WikiHubPage.GAME_TIPS) }
                    ),
                    backdrop = backdrop
                )
            }
            item {
                ActionCard(
                    title = "壁纸",
                    subtitle = "浏览官方壁纸与视觉图",
                    icon = Icons.Outlined.Wallpaper,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.WALLPAPERS) },
                    backdrop = backdrop
                )
            }
            item {
                ActionCard(
                    title = "表情包",
                    subtitle = "查看官方表情包资源",
                    icon = Icons.Outlined.EmojiEmotions,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.STICKERS) },
                    backdrop = backdrop
                )
            }
            item {
                ActionCard(
                    title = "喵言喵语",
                    subtitle = "查看喵言喵语词条内容",
                    icon = Icons.Outlined.Pets,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.MEOW_LANGUAGE) },
                    backdrop = backdrop
                )
            }
            item {
                ActionCard(
                    title = "BGM",
                    subtitle = "浏览原声音乐、专辑与场景曲目",
                    icon = Icons.Outlined.MusicNote,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateTo(WikiHubPage.BGM) },
                    backdrop = backdrop
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WikiDecorationHubScreen(
    onBack: () -> Unit,
    onNavigateTo: (WikiHubPage) -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    AggregatePageScaffold(title = "玩家装饰", onBack = onBack) { innerPadding, backdrop ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 10.dp,
                end = 16.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ContentBlockCard(
                    title = "身份展示",
                    icon = Icons.Outlined.MilitaryTech,
                    items = listOf(
                        "基板" to "基板",
                        "封装" to "封装",
                        "勋章" to "勋章",
                        "头像框" to "头像框"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    nativePages = mapOf(
                        "基板" to { onNavigateTo(WikiHubPage.BASEPLATES) },
                        "封装" to { onNavigateTo(WikiHubPage.ENCASINGS) },
                        "勋章" to { onNavigateTo(WikiHubPage.MEDALS) },
                        "头像框" to { onNavigateTo(WikiHubPage.AVATAR_FRAMES) }
                    ),
                    backdrop = backdrop
                )
            }
            item {
                ContentBlockCard(
                    title = "局内与社交装饰",
                    icon = Icons.Outlined.Palette,
                    items = listOf(
                        "喷漆" to "喷漆",
                        "聊天气泡" to "聊天气泡",
                        "头套" to "头套",
                        "超弦体动作" to "超弦体动作"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    nativePages = mapOf(
                        "喷漆" to { onNavigateTo(WikiHubPage.SPRAYS) },
                        "聊天气泡" to { onNavigateTo(WikiHubPage.CHAT_BUBBLES) },
                        "头套" to { onNavigateTo(WikiHubPage.HEADGEAR) },
                        "超弦体动作" to { onNavigateTo(WikiHubPage.STRINGER_ACTIONS) }
                    ),
                    backdrop = backdrop
                )
            }
            item {
                ContentBlockCard(
                    title = "外观扩展",
                    icon = Icons.Outlined.Extension,
                    items = listOf(
                        "房间外观" to "房间外观",
                        "载具外观" to "极限推进模式载具外观"
                    ),
                    onOpenWikiUrl = onOpenWikiUrl,
                    nativePages = mapOf(
                        "房间外观" to { onNavigateTo(WikiHubPage.ROOM_APPEARANCES) },
                        "载具外观" to { onNavigateTo(WikiHubPage.VEHICLE_SKINS) }
                    ),
                    backdrop = backdrop
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AggregatePageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues, Backdrop) -> Unit
) {
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current.value
    var wallpaperUrl by remember { mutableStateOf(AppPrefs.wallpaperUrl) }
    LaunchedEffect(Unit) {
        if (wallpaperUrl.isNullOrBlank()) {
            val loadedUrl = withContext(Dispatchers.IO) {
                WallpaperApi.ensureWallpaperUrl(forceRefresh = false)
            }
            if (!loadedUrl.isNullOrBlank()) wallpaperUrl = loadedUrl
        }
    }
    val hasWallpaper = wallpaperUrl != null
    val layerBackdrop = rememberLayerBackdrop()
    val empty = remember { emptyBackdrop() }
    val backdrop: Backdrop = if (liquidGlassEnabled) layerBackdrop else empty
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primaryContainer
    val wallpaperOverlayBrush = remember(liquidGlassEnabled, surfaceColor, primaryColor) {
        Brush.verticalGradient(
            colors = if (liquidGlassEnabled) listOf(
                primaryColor.copy(alpha = 0.3f),
                surfaceColor.copy(alpha = 0.6f),
                surfaceColor.copy(alpha = 0.85f)
            ) else listOf(
                surfaceColor.copy(alpha = 0.15f),
                surfaceColor.copy(alpha = 0.5f),
                surfaceColor.copy(alpha = 0.8f)
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (hasWallpaper) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (liquidGlassEnabled) Modifier.layerBackdrop(layerBackdrop) else Modifier)
            ) {
                AsyncImage(
                    model = wallpaperUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(wallpaperOverlayBrush))
            }
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }

        androidx.compose.runtime.CompositionLocalProvider(LocalHasWallpaper provides hasWallpaper) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Bold) },
                        navigationIcon = { BackNavButton(onClick = onBack) },
                        colors = if (hasWallpaper) {
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent
                            )
                        } else {
                            TopAppBarDefaults.topAppBarColors()
                        },
                        modifier = if (hasWallpaper) Modifier.drawBehind {
                            drawRect(surfaceColor.copy(alpha = 0.52f))
                        } else Modifier
                    )
                },
                containerColor = Color.Transparent
            ) { innerPadding ->
                content(innerPadding, backdrop)
            }
        }
    }
}

@Composable
internal fun AggregatePreviewCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    backdrop: Backdrop
) {
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current
    val cardShape = smoothCornerShape(24.dp)
    val capsuleShape = smoothCapsuleShape()
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        shape = cardShape,
        color = when {
            liquidGlass -> Color.Transparent
            hasWallpaper -> containerColor.copy(alpha = 0.85f)
            else -> containerColor
        },
        modifier = Modifier
            .fillMaxSize()
            .liquidGlass(
                backdrop = backdrop,
                shape = { cardShape },
                surfaceAlpha = 0.3f
            )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxSize().height(88.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                Surface(
                    shape = capsuleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.padding(start = 18.dp).height(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(48.dp).padding(horizontal = 12.dp)) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = contentColor)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp)
                )
        }
    }
}
