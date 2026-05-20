package com.nekolaska.calabiyau.feature.character.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
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
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import java.time.LocalDate

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
    val state =
        rememberLoadState(emptyList<CharacterListApi.FactionData>()) { force ->
            CharacterListApi.fetchAllFactions(force)
        }
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var showBirthdayDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("超弦体 & 晶源体", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    IconButton(onClick = { showBirthdayDialog = true }) {
                        Icon(Icons.Outlined.Cake, contentDescription = "角色生日")
                    }
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> CharacterListSkeleton(mod) }
        ) { factions ->
            // 阵营 Tab
            if (factions.size > 1) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
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

    if (showBirthdayDialog) {
        BirthdayDialog(onDismiss = { showBirthdayDialog = false })
    }
}

@Composable
private fun BirthdayDialog(onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    val thisMonthBirthdays = remember(today) {
        CharacterBirthdays.entries.filter { it.month == today.monthValue }
    }
    val nearest = remember(today, thisMonthBirthdays) {
        thisMonthBirthdays
            .map { it to it.day - today.dayOfMonth }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        icon = { Icon(Icons.Outlined.Cake, contentDescription = null) },
        title = { Text("${today.monthValue}月角色生日") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BirthdayAlert(nearest)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                if (thisMonthBirthdays.isEmpty()) {
                    Text("本月没有角色过生日喵", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("本月寿星", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        thisMonthBirthdays.forEach { birthday ->
                            BirthdayRow(birthday, today)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun BirthdayAlert(nearest: Pair<CharacterBirthday, Int>?) {
    Surface(shape = smoothCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (nearest == null) {
                Text("本月剩下的日子里没有寿星了喵", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else if (nearest.second == 0) {
                Text("今天!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("是 ${nearest.first.name} 的生日", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                Text("${nearest.second} 天", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("距离 ${nearest.first.name} (${nearest.first.dateText}) 生日", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun BirthdayRow(birthday: CharacterBirthday, today: LocalDate) {
    val diff = birthday.day - today.dayOfMonth
    val status = when {
        diff < 0 -> "已过"
        diff == 0 -> "今天"
        else -> "${diff}天后"
    }
    val color = when {
        diff < 0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        diff == 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(birthday.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color, modifier = Modifier.weight(1f))
        Text("${birthday.dateText} ($status)", style = MaterialTheme.typography.bodySmall, color = color)
    }
}

private data class CharacterBirthday(
    val name: String,
    val month: Int,
    val day: Int
) {
    val dateText: String = month.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

private object CharacterBirthdays {
    val entries = listOf(
        CharacterBirthday("令", 1, 5),
        CharacterBirthday("千代", 1, 10),
        CharacterBirthday("香奈美", 1, 27),
        CharacterBirthday("芙拉薇娅", 2, 14),
        CharacterBirthday("加拉蒂亚", 2, 22),
        CharacterBirthday("玛德蕾娜", 3, 7),
        CharacterBirthday("忧雾", 3, 22),
        CharacterBirthday("米雪儿", 3, 25),
        CharacterBirthday("玛拉", 4, 1),
        CharacterBirthday("绯莎", 5, 10),
        CharacterBirthday("伊薇特", 5, 23),
        CharacterBirthday("莉莉丝", 6, 6),
        CharacterBirthday("官博娘", 6, 6),
        CharacterBirthday("汐", 6, 21),
        CharacterBirthday("信", 7, 11),
        CharacterBirthday("奥黛丽", 7, 29),
        CharacterBirthday("明", 8, 15),
        CharacterBirthday("梅瑞狄斯", 8, 28),
        CharacterBirthday("珐格兰丝", 9, 10),
        CharacterBirthday("星绘", 9, 26),
        CharacterBirthday("蕾欧娜", 10, 10),
        CharacterBirthday("拉薇", 10, 29),
        CharacterBirthday("艾卡", 11, 11),
        CharacterBirthday("心夏", 12, 12),
        CharacterBirthday("宣传车", 12, 18),
        CharacterBirthday("白墨", 12, 20)
    )
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
        shape = smoothCornerShape(12.dp),
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
                    .clip(smoothCornerShape(12.dp))
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
                shape = smoothCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(5f / 12f),
                        shape = smoothCornerShape(12.dp)
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
