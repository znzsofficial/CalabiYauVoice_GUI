package com.nekolaska.calabiyau.feature.wiki.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.nekolaska.calabiyau.core.ui.LocalLiquidGlassEnabled
import com.nekolaska.calabiyau.core.ui.liquidGlass
import com.nekolaska.calabiyau.core.ui.smoothCapsuleShape
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.weapon.list.WeaponListApi
import com.nekolaska.calabiyau.feature.wiki.map.api.MapListApi
import com.nekolaska.calabiyau.feature.wiki.map.model.GameModeData
import data.ApiResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class HubSearchEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: List<String>,
    val icon: ImageVector,
    val targetRoute: WikiRoute,
    val kind: SearchEntryKind = SearchEntryKind.Static,
    val aliases: List<String> = emptyList(),
)

private data class GroupedSearchResults(
    val characters: List<HubSearchEntry> = emptyList(),
    val relatedWeapons: List<HubSearchEntry> = emptyList(),
    val maps: List<HubSearchEntry> = emptyList(),
    val entries: List<HubSearchEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = characters.isEmpty() && relatedWeapons.isEmpty() && maps.isEmpty() && entries.isEmpty()
}

private object HubWeaponSearchIndex {
    @Volatile
    private var cachedCategories: List<WeaponListApi.WeaponCategoryData>? = null

    private val mutex = Mutex()

    fun cached(): List<WeaponListApi.WeaponCategoryData> = cachedCategories.orEmpty()

    suspend fun load(): List<WeaponListApi.WeaponCategoryData> = mutex.withLock {
        cachedCategories?.takeIf { it.isNotEmpty() }?.let { return@withLock it }
        val lightweight = when (val result = WeaponListApi.fetchAllCategories(forceRefresh = false, includeImages = false)) {
            is ApiResult.Success -> result.value
            is ApiResult.Error -> emptyList()
        }
        val loaded = lightweight.ifEmpty {
            when (val result = WeaponListApi.fetchAllCategories(forceRefresh = false, includeImages = true)) {
                is ApiResult.Success -> result.value
                is ApiResult.Error -> emptyList()
            }
        }
        if (loaded.isNotEmpty()) {
            cachedCategories = loaded
        }
        loaded
    }
}

private enum class SearchEntryKind {
    Character,
    Weapon,
    Map,
    MapMode,
    Static,
}

private val fallbackMapNames = listOf(
    "欧拉港口",
    "88区",
    "404基地",
    "空间实验室",
    "柯西街区",
    "风曳镇",
    "离岛区",
    "极点",
    "科斯玛能源中心",
    "阿玛纳生态园",
    "零区",
    "运载轨道",
    "香槟镇",
    "黑塔",
    "卡丘训练场",
)

private val hubSearchEntries = listOf(
    searchEntry("characters", "角色", "超弦体与晶源体资料", listOf("角色", "超弦体", "晶源体", "人物", "角色详情", "角色列表"), Icons.Outlined.People, WikiHubPage.CHARACTERS),
    searchEntry("weapons", "武器一览", "查看全部武器数据", listOf("武器", "枪械", "枪", "枪械数据", "武器详情", "武器列表"), Icons.Outlined.GpsFixed, WikiHubPage.WEAPONS),
    searchEntry("maps", "地图", "地图列表与点位资料", listOf("地图", "场景", "点位", "地图详情", "地图一览", "模式地图"), Icons.Outlined.Map, WikiHubPage.MAPS),
    searchEntry("items", "道具图鉴", "功能道具、货币与礼包", listOf("图鉴", "道具", "物品", "目录", "货币", "礼盒", "礼包", "道具图鉴"), Icons.Outlined.Inventory2, WikiHubPage.ITEMS),
    searchEntry("voting", "投票", "查看并参与当前 Wiki 投票", listOf("投票", "时装投票", "票选", "投票页"), Icons.Outlined.HowToVote, WikiHubPage.VOTING),
    searchEntry("costumes", "角色时装", "浏览全部角色时装与外观", listOf("时装", "皮肤", "角色外观", "服装", "衣服", "角色皮肤", "时装筛选"), Icons.Outlined.Checkroom, WikiHubPage.COSTUMES),
    searchEntry("weapon_skins", "武器外观", "浏览全部武器外观与皮肤", listOf("枪皮", "武器皮肤", "武器外观", "外观", "皮肤", "枪械皮肤"), Icons.Outlined.Palette, WikiHubPage.WEAPON_SKINS),
    searchEntry("bio_cards", "卡牌", "生化卡牌与卡组分享", listOf("卡牌", "生化", "卡组", "卡组分享", "PC卡牌", "移动端卡牌", "生化卡牌"), Icons.Outlined.Style, WikiHubPage.BIO_CARDS),
    searchEntry("activities", "活动", "当前与历史活动", listOf("活动", "活动页", "限时", "历史活动", "当前活动"), Icons.Outlined.Event, WikiHubPage.ACTIVITIES),
    searchEntry("announcements", "公告资讯", "游戏公告和更新信息", listOf("公告", "资讯", "更新", "新闻", "公告资讯", "维护公告"), Icons.Outlined.Campaign, WikiHubPage.ANNOUNCEMENTS),
    searchEntry("balance_data", "平衡数据", "胜率、选取率、KD 与评分", listOf("平衡", "胜率", "选取率", "KD", "伤害", "评分", "场均伤害", "场均得分", "数据"), Icons.Outlined.BarChart, WikiHubPage.BALANCE_DATA),
    searchEntry("gameplay_hub", "玩法与养成", "角色培养、玩法系统与移动端内容", listOf("玩法", "养成", "玩法系统", "角色培养", "移动端内容", "系统"), Icons.Outlined.Extension, WikiHubPage.GAMEPLAY_HUB),
    searchEntry("game_modes", "战斗模式", "查看所有战斗模式详情", listOf("战斗模式", "模式", "玩法", "爆破", "推车", "团队", "娱乐模式"), Icons.Outlined.SportsEsports, WikiHubPage.GAME_MODES),
    searchEntry("achievements", "成就", "战斗勋章、荣耀成就与光辉事迹", listOf("成就", "奖杯", "战斗勋章", "荣耀成就", "光辉事迹"), Icons.Outlined.EmojiEvents, WikiHubPage.ACHIEVEMENTS),
    searchEntry("oath", "誓约", "角色培养与誓约资料", listOf("誓约", "角色培养", "好感", "礼物", "生日"), Icons.Outlined.FavoriteBorder, WikiHubPage.OATH),
    searchEntry("imprints", "印迹", "印迹资料与养成内容", listOf("印迹", "角色培养", "养成"), Icons.Outlined.MilitaryTech, WikiHubPage.IMPRINTS),
    searchEntry("player_levels", "玩家等级", "等级奖励和经验", listOf("等级", "玩家等级", "经验", "奖励", "等级奖励"), Icons.Outlined.MilitaryTech, WikiHubPage.PLAYER_LEVELS),
    searchEntry("stringer_talents", "超弦体天赋", "移动端超弦体天赋", listOf("天赋", "超弦体天赋", "移动端", "手游天赋"), Icons.Outlined.PhoneAndroid, WikiHubPage.STRINGER_TALENTS),
    searchEntry("stringer_push_cards", "超弦推进卡牌", "超弦推进模式卡牌", listOf("超弦推进", "推进", "移动端卡牌", "推进卡牌", "卡牌"), Icons.Outlined.PhoneAndroid, WikiHubPage.STRINGER_PUSH_CARDS),
    searchEntry("catalog_hub", "外观与图鉴", "道具图鉴、时装筛选与武器外观", listOf("外观", "图鉴", "时装", "枪皮", "道具", "目录"), Icons.Outlined.Inventory2, WikiHubPage.CATALOG_HUB),
    searchEntry("decoration_hub", "玩家装饰", "基板、封装、勋章、喷漆等装饰", listOf("装饰", "玩家装饰", "身份展示", "局内装饰", "社交装饰"), Icons.Outlined.Palette, WikiHubPage.DECORATION_HUB),
    searchEntry("baseplates", "基板", "身份展示基板", listOf("基板", "玩家装饰", "身份展示", "名片"), Icons.Outlined.MilitaryTech, WikiHubPage.BASEPLATES),
    searchEntry("encasings", "封装", "身份展示封装", listOf("封装", "玩家装饰", "身份展示"), Icons.Outlined.MilitaryTech, WikiHubPage.ENCASINGS),
    searchEntry("medals", "勋章", "勋章与荣誉展示", listOf("勋章", "徽章", "荣誉", "玩家装饰"), Icons.Outlined.MilitaryTech, WikiHubPage.MEDALS),
    searchEntry("avatar_frames", "头像框", "头像框外观", listOf("头像框", "头像", "相框", "移动端", "身份展示"), Icons.Outlined.Palette, WikiHubPage.AVATAR_FRAMES),
    searchEntry("sprays", "喷漆", "局内喷漆装饰", listOf("喷漆", "喷涂", "局内装饰", "贴纸"), Icons.Outlined.Palette, WikiHubPage.SPRAYS),
    searchEntry("chat_bubbles", "聊天气泡", "聊天气泡外观", listOf("聊天气泡", "气泡", "聊天框", "社交装饰"), Icons.Outlined.Palette, WikiHubPage.CHAT_BUBBLES),
    searchEntry("headgear", "头套", "头套与头饰外观", listOf("头套", "头饰", "帽子", "局内装饰"), Icons.Outlined.Palette, WikiHubPage.HEADGEAR),
    searchEntry("stringer_actions", "超弦体动作", "角色动作与表演", listOf("动作", "超弦体动作", "角色动作", "局内动作"), Icons.Outlined.Palette, WikiHubPage.STRINGER_ACTIONS),
    searchEntry("room_appearances", "房间外观", "房间装饰外观", listOf("房间", "房间外观", "房间装饰", "宿舍"), Icons.Outlined.Extension, WikiHubPage.ROOM_APPEARANCES),
    searchEntry("vehicle_skins", "载具外观", "极限推进模式载具外观", listOf("载具", "载具外观", "车皮", "极限推进", "移动端"), Icons.Outlined.Extension, WikiHubPage.VEHICLE_SKINS),
    searchEntry("extension_hub", "游戏延伸", "剧情、历史、BGM、投票与百科内容", listOf("延伸", "资料", "世界观", "剧情", "历史", "BGM", "音乐", "百科"), Icons.Outlined.MoreHoriz, WikiHubPage.EXTENSION_HUB),
    searchEntry("story", "剧情故事", "角色与世界观故事", listOf("剧情", "故事", "世界观", "资料", "角色故事"), Icons.Outlined.Style, WikiHubPage.STORY),
    searchEntry("history", "游戏历史", "版本和游戏历史记录", listOf("历史", "版本历史", "游戏历史", "记录", "年表"), Icons.Outlined.AccountTree, WikiHubPage.GAME_HISTORY),
    searchEntry("comics", "四格漫画", "官方四格漫画", listOf("四格", "漫画", "四格漫画", "官方漫画"), Icons.Outlined.AutoAwesome, WikiHubPage.COMICS),
    searchEntry("collaborations", "联动", "联动活动与内容", listOf("联动", "合作", "联动活动"), Icons.Outlined.Handshake, WikiHubPage.COLLABORATIONS),
    searchEntry("memes", "梗百科", "社区梗与百科内容", listOf("梗", "梗百科", "百科", "社区梗"), Icons.Outlined.AutoAwesome, WikiHubPage.MEMES),
    searchEntry("game_tips", "游戏小贴士", "游戏提示与技巧", listOf("小贴士", "提示", "技巧", "tips", "游戏Tips", "游戏提示"), Icons.Outlined.AutoAwesome, WikiHubPage.GAME_TIPS),
    searchEntry("wallpapers", "壁纸", "官方壁纸与视觉图", listOf("壁纸", "画廊", "图片", "视觉图", "官方壁纸"), Icons.Outlined.Wallpaper, WikiHubPage.WALLPAPERS),
    searchEntry("stickers", "表情包", "官方表情包资源", listOf("表情", "表情包", "贴图", "官方表情包"), Icons.Outlined.EmojiEmotions, WikiHubPage.STICKERS),
    searchEntry("meow_language", "喵言喵语", "喵言喵语词条内容", listOf("喵言喵语", "喵语", "词条", "语言"), Icons.Outlined.Pets, WikiHubPage.MEOW_LANGUAGE),
    searchEntry("bgm", "BGM", "原声音乐、专辑与场景曲目", listOf("BGM", "音乐", "歌曲", "曲目", "原声", "专辑", "OST"), Icons.Outlined.MusicNote, WikiHubPage.BGM),
    searchEntry("navigation", "完整导航", "浏览 Wiki 全部分类目录", listOf("导航", "目录", "分类", "全部", "完整导航", "Wiki导航"), Icons.Outlined.AccountTree, WikiHubPage.NAVIGATION),
)

private fun searchEntry(
    id: String,
    title: String,
    subtitle: String,
    keywords: List<String>,
    icon: ImageVector,
    targetPage: WikiHubPage,
) = HubSearchEntry(id, title, subtitle, keywords, icon, targetPage.toRoute())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HubSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onNavigateTo: (WikiRoute) -> Unit,
    factions: List<CharacterListApi.FactionData> = emptyList(),
    gameModes: List<GameModeData> = emptyList(),
    isIndexLoading: Boolean = false,
    backdrop: Backdrop = emptyBackdrop(),
) {
    val normalizedQuery = query.trim()
    var searchActive by remember { mutableStateOf(false) }
    var weaponCategories by remember { mutableStateOf(HubWeaponSearchIndex.cached()) }
    var isLoadingWeapons by remember { mutableStateOf(false) }
    var hasLoadedWeapons by remember { mutableStateOf(weaponCategories.isNotEmpty()) }

    LaunchedEffect(searchActive, hasLoadedWeapons) {
        if (!searchActive || hasLoadedWeapons || isLoadingWeapons) return@LaunchedEffect
        isLoadingWeapons = true
        try {
            val loadedCategories = HubWeaponSearchIndex.load()
            if (loadedCategories.isNotEmpty()) {
                weaponCategories = loadedCategories
                hasLoadedWeapons = true
            }
        } finally {
            isLoadingWeapons = false
        }
    }

    val dynamicEntries = remember(factions, weaponCategories, gameModes) {
        buildDynamicSearchEntries(factions, weaponCategories, gameModes)
    }
    val groupedResults = remember(normalizedQuery, dynamicEntries) {
        buildGroupedSearchResults(normalizedQuery, dynamicEntries)
    }
    val showSearchOptions = searchActive && normalizedQuery.isNotBlank()
    val liquidGlass = LocalLiquidGlassEnabled.current.value
    val hasWallpaper = LocalHasWallpaper.current
    val shape = smoothCornerShape(28.dp)
    val surfaceColor = when {
        liquidGlass -> Color.Transparent
        hasWallpaper -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    DockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = {
                    onQueryChange(it)
                    searchActive = true
                },
                onSearch = { },
                expanded = searchActive,
                onExpandedChange = { expanded ->
                    searchActive = expanded
                },
                placeholder = { Text("搜索入口、角色、武器、地图") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "清空")
                        }
                    }
                } else null,
            )
        },
        expanded = showSearchOptions,
        onExpandedChange = { expanded ->
            if (!expanded) searchActive = false
        },
        shape = shape,
        colors = SearchBarDefaults.colors(containerColor = surfaceColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = { shape },
                surfaceAlpha = 0.22f
            )
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(start = 14.dp, end = 14.dp, bottom = 12.dp)),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isLoadingWeapons && groupedResults.isEmpty) {
                Text(
                    "正在加载武器索引",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            } else if (groupedResults.isEmpty) {
                Text(
                    "没有匹配的入口",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            } else {
                SearchResultSection("角色", groupedResults.characters) { entry ->
                    onQueryChange("")
                    searchActive = false
                    onNavigateTo(entry.targetRoute)
                }
                SearchResultSection("相关武器", groupedResults.relatedWeapons) { entry ->
                    onQueryChange("")
                    searchActive = false
                    onNavigateTo(entry.targetRoute)
                }
                SearchResultSection("地图", groupedResults.maps) { entry ->
                    onQueryChange("")
                    searchActive = false
                    onNavigateTo(entry.targetRoute)
                }
                SearchResultSection("入口", groupedResults.entries) { entry ->
                    onQueryChange("")
                    searchActive = false
                    onNavigateTo(entry.targetRoute)
                }
            }
        }
    }
}

private fun buildDynamicSearchEntries(
    factions: List<CharacterListApi.FactionData>,
    weaponCategories: List<WeaponListApi.WeaponCategoryData>,
    gameModes: List<GameModeData>,
): List<HubSearchEntry> {
    val characterEntries = factions.flatMap { faction ->
        faction.characters.map { character ->
            HubSearchEntry(
                id = "character_${character.name}",
                title = character.name,
                subtitle = "角色 · ${faction.faction}",
                keywords = listOf(character.name, faction.faction, "角色", "超弦体", "晶源体"),
                icon = Icons.Outlined.People,
                targetRoute = WikiRoute.CharDetail(character.name, character.imageUrl),
                kind = SearchEntryKind.Character,
            )
        }
    }
    val weaponEntries = weaponCategories.flatMap { category ->
        category.weapons.map { weapon ->
            HubSearchEntry(
                id = "weapon_${weapon.name}",
                title = weapon.name,
                subtitle = listOf("武器", category.category.displayName, weapon.user, weapon.type)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                keywords = listOf(weapon.name, weapon.user, weapon.type, category.category.displayName, "武器", "枪械", "枪"),
                icon = Icons.Outlined.GpsFixed,
                targetRoute = WikiRoute.WeaponDetail(weapon.name),
                kind = SearchEntryKind.Weapon,
                aliases = listOf(weapon.user, weapon.type, category.category.displayName).filter { it.isNotBlank() },
            )
        }
    }
    val mapsByName = linkedMapOf<String, HubSearchEntry>()
    gameModes.forEach { mode ->
        mode.maps.forEach { map ->
            val existing = mapsByName[map.name]
            val subtitle = existing?.subtitle
                ?.plus("、${mode.displayName}")
                ?: "地图 · ${mode.displayName}"
            mapsByName[map.name] = HubSearchEntry(
                id = "map_${map.name}",
                title = map.name,
                subtitle = subtitle,
                keywords = listOf(map.name, mode.displayName, "地图", "场景", "点位", "地图详情"),
                icon = Icons.Outlined.Map,
                targetRoute = WikiRoute.MapDetail(map.name, existingRouteImageUrl(existing) ?: map.imageUrl),
                kind = SearchEntryKind.Map,
            )
        }
    }
    fallbackMapNames.forEach { mapName ->
        mapsByName.putIfAbsent(
            mapName,
            HubSearchEntry(
                id = "fallback_map_$mapName",
                title = mapName,
                subtitle = "地图详情",
                keywords = listOf(mapName, "地图", "地图详情", "场景"),
                icon = Icons.Outlined.Map,
                targetRoute = WikiRoute.MapDetail(mapName, null),
                kind = SearchEntryKind.Map,
            )
        )
    }
    val mapModeEntries = if (gameModes.isNotEmpty()) gameModes.map { mode ->
        HubSearchEntry(
            id = "map_mode_${mode.displayName}",
            title = mode.displayName,
            subtitle = "地图模式 · ${mode.maps.size} 张地图",
            keywords = listOf(mode.displayName, mode.templateName, "地图", "模式地图"),
            icon = Icons.Outlined.Map,
            targetRoute = WikiRoute.Maps,
            kind = SearchEntryKind.MapMode,
        )
    } else MapListApi.GAME_MODES.map { (displayName, templateName) ->
        HubSearchEntry(
            id = "fallback_map_mode_$displayName",
            title = displayName,
            subtitle = "地图模式",
            keywords = listOf(displayName, templateName, "地图", "模式地图"),
            icon = Icons.Outlined.Map,
            targetRoute = WikiRoute.Maps,
            kind = SearchEntryKind.MapMode,
        )
    }
    return characterEntries + weaponEntries + mapsByName.values + mapModeEntries
}

private fun existingRouteImageUrl(entry: HubSearchEntry?): String? {
    return (entry?.targetRoute as? WikiRoute.MapDetail)?.imageUrl
}

private fun buildGroupedSearchResults(
    query: String,
    dynamicEntries: List<HubSearchEntry>,
): GroupedSearchResults {
    if (query.isBlank()) return GroupedSearchResults()

    val characters = dynamicEntries
        .filter { it.kind == SearchEntryKind.Character }
        .rankedMatches(query)
        .take(4)
    val matchedCharacterNames = characters.map { normalizeSearchText(it.title) }.toSet()
    val weapons = dynamicEntries.filter { it.kind == SearchEntryKind.Weapon }
    val relatedWeapons = weapons
        .mapNotNull { weapon ->
            val userMatch = weapon.aliases
                .map(::normalizeSearchText)
                .any { alias -> alias.isNotBlank() && (alias in matchedCharacterNames || aliasMatchesQuery(alias, query)) }
            if (userMatch) weapon.weaponRelationScore(query) to weapon else null
        }
        .sortedBy { it.first }
        .map { it.second }
        .take(5)
    val directWeapons = weapons
        .rankedMatches(query)
        .filterNot { weapon -> relatedWeapons.any { it.id == weapon.id } }
        .take(5 - relatedWeapons.size)
    val maps = dynamicEntries
        .filter { it.kind == SearchEntryKind.Map || it.kind == SearchEntryKind.MapMode }
        .rankedMatches(query)
        .take(5)
    val entries = hubSearchEntries
        .rankedMatches(query)
        .take(4)

    return GroupedSearchResults(
        characters = characters,
        relatedWeapons = relatedWeapons + directWeapons,
        maps = maps,
        entries = entries,
    )
}

private fun List<HubSearchEntry>.rankedMatches(query: String): List<HubSearchEntry> {
    return mapNotNull { entry ->
        entry.matchScore(query)?.let { score -> score to entry }
    }
        .sortedBy { it.first }
        .map { it.second }
}

private fun aliasMatchesQuery(alias: String, query: String): Boolean {
    val q = normalizeSearchText(query)
    if (q.isBlank()) return false
    return alias == q || alias.startsWith(q) || (!isShortSearchQuery(q) && alias.contains(q))
}

private fun HubSearchEntry.weaponRelationScore(query: String): Int {
    val category = aliases.getOrNull(2).orEmpty()
    val categoryScore = when (category) {
        "主武器" -> 0
        "副武器" -> 10
        "近战武器" -> 20
        "战术道具" -> 30
        else -> 40
    }
    return categoryScore + (matchScore(query) ?: 25)
}

private fun HubSearchEntry.matchScore(query: String): Int? {
    val q = normalizeSearchText(query)
    if (q.isBlank()) return null
    val titleText = normalizeSearchText(title)
    val subtitleText = normalizeSearchText(subtitle)
    val keywordTexts = keywords.map(::normalizeSearchText).filter { it.isNotBlank() }
    val aliasTexts = aliases.map(::normalizeSearchText).filter { it.isNotBlank() }
    val isShortQuery = isShortSearchQuery(q)

    val baseScore = when {
        titleText == q -> 0
        titleText.startsWith(q) -> 10
        kind == SearchEntryKind.Weapon && aliasTexts.any { it == q } -> 18
        titleText.contains(q) && kind != SearchEntryKind.Static -> 20
        keywordTexts.any { it == q && it.length >= 2 } -> 35
        !isShortQuery && aliasTexts.any { it.contains(q) } -> 40
        !isShortQuery && keywordTexts.any { it.startsWith(q) && it.length >= 2 } -> 50
        !isShortQuery && subtitleText.contains(q) && kind != SearchEntryKind.Static -> 70
        !isShortQuery && keywordTexts.any { it.contains(q) && it.length >= 3 } -> 80
        else -> return null
    }
    return baseScore + kind.sortOffset()
}

private fun isShortSearchQuery(query: String): Boolean {
    return query.length <= 1 || query.all { it.isLetterOrDigit() } && query.length <= 2
}

private fun SearchEntryKind.sortOffset(): Int = when (this) {
    SearchEntryKind.Character -> 0
    SearchEntryKind.Weapon -> 2
    SearchEntryKind.Map -> 4
    SearchEntryKind.MapMode -> 8
    SearchEntryKind.Static -> 20
}

private fun normalizeSearchText(value: String): String {
    return value.trim().lowercase()
}

@Composable
private fun SearchResultSection(
    title: String,
    entries: List<HubSearchEntry>,
    onEntryClick: (HubSearchEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
    entries.forEach { entry ->
        HubSearchResultRow(entry = entry, onClick = { onEntryClick(entry) })
    }
}

@Composable
private fun HubSearchResultRow(
    entry: HubSearchEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(smoothCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = smoothCapsuleShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(entry.icon, contentDescription = null, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )
    }
}
