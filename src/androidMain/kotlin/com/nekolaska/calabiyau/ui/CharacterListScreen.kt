package com.nekolaska.calabiyau.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.data.CharacterListApi
import kotlinx.coroutines.launch
import data.ApiResult

// ════════════════════════════════════════════════════════
//  角色列表页 —— 按阵营 Tab 展示角色卡片网格 (MD3 Expressive)
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterListScreen(
    onBack: () -> Unit,
    onOpenCharacterDetail: (name: String, portraitUrl: String?) -> Unit,
    initialTab: Int = 0,
    onTabChanged: ((Int) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorResult by remember { mutableStateOf<ApiResult.Error?>(null) }
    var factions by remember { mutableStateOf<List<CharacterListApi.FactionData>>(emptyList()) }
    var isOffline by remember { mutableStateOf(false) }
    var cacheAgeMs by remember { mutableLongStateOf(0L) }
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    fun loadData(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            errorResult = null
            when (val result = CharacterListApi.fetchAllFactions(forceRefresh)) {
                is ApiResult.Success -> {
                    factions = result.value
                    isOffline = result.isOffline
                    cacheAgeMs = result.cacheAgeMs
                }
                is ApiResult.Error -> errorResult = result
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("超弦体 & 晶源体", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading && factions.isEmpty() -> {
                CharacterListSkeleton(Modifier.padding(innerPadding))
            }

            errorResult != null && factions.isEmpty() -> {
                ErrorState(
                    message = errorResult!!.message,
                    kind = errorResult!!.kind,
                    onRetry = { loadData(forceRefresh = true) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { loadData(forceRefresh = true) },
                    state = rememberPullToRefreshState(),
                    modifier = Modifier.padding(innerPadding)
                ) {
                    Column {
                        if (isOffline) {
                            OfflineBanner(ageMs = cacheAgeMs)
                        }
                        // 阵营 Tab
                        if (factions.size > 1) {
                            PrimaryTabRow(
                                selectedTabIndex = selectedTab
                            ) {
                                factions.forEachIndexed { index, faction ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = {
                                            selectedTab = index
                                            onTabChanged?.invoke(index)
                                        },
                                        text = {
                                            Text(
                                                faction.faction,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // 角色网格
                        val currentFaction = factions.getOrNull(selectedTab)
                        if (currentFaction != null) {
                            CharacterGrid(
                                characters = currentFaction.characters,
                                onOpenCharacterDetail = onOpenCharacterDetail
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterGrid(
    characters: List<CharacterListApi.CharacterInfo>,
    onOpenCharacterDetail: (name: String, portraitUrl: String?) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(characters, key = { it.name }) { character ->
            CharacterCard(
                character = character,
                onClick = { onOpenCharacterDetail(character.name, character.imageUrl) }
            )
        }

        // 底部留白
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CharacterCard(
    character: CharacterListApi.CharacterInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 角色图片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 12f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                AsyncImage(
                    model = character.imageUrl,
                    contentDescription = character.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 角色名
            Text(
                text = character.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun CharacterListSkeleton(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        items(9) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(5f / 12f),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(12.dp)
                            .padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
