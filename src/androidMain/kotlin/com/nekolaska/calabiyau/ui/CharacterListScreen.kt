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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var factions by remember { mutableStateOf<List<CharacterListApi.FactionData>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    fun loadData() {
        scope.launch {
            isLoading = true
            errorMessage = null
            when (val result = CharacterListApi.fetchAllFactions()) {
                is CharacterListApi.ApiResult.Success -> factions = result.value
                is CharacterListApi.ApiResult.Error -> errorMessage = result.message
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
            isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("正在加载角色列表…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            errorMessage != null && factions.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        FilledTonalButton(onClick = { loadData() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("重试")
                        }
                    }
                }
            }

            else -> {
                Column(Modifier.padding(innerPadding)) {
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
