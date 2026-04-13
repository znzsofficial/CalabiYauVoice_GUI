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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.data.WeaponListApi

// ════════════════════════════════════════════════════════
//  武器列表页 —— 按分类展示武器卡片网格 (MD3 Expressive)
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeaponListScreen(
    onBack: () -> Unit,
    onOpenWeaponDetail: (weaponName: String) -> Unit,
    initialTab: Int = 0,
    onTabChanged: ((Int) -> Unit)? = null
) {
    val state = rememberLoadState(emptyList<WeaponListApi.WeaponCategoryData>()) { force ->
        WeaponListApi.fetchAllCategories(force)
    }
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("武器一览", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> WeaponListSkeleton(mod) }
        ) { categories ->
            // 分类 Tab
            if (categories.size > 1) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    categories.forEachIndexed { index, cat ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                onTabChanged?.invoke(index)
                            },
                            text = {
                                Text(
                                    cat.category.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            // 武器网格
            val currentCategory = categories.getOrNull(selectedTab)
            if (currentCategory != null) {
                WeaponGrid(
                    weapons = currentCategory.weapons,
                    onOpenWeaponDetail = onOpenWeaponDetail
                )
            }
        }
    }
}

@Composable
private fun WeaponGrid(
    weapons: List<WeaponListApi.WeaponInfo>,
    onOpenWeaponDetail: (weaponName: String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(weapons, key = { it.name }) { weapon ->
            WeaponCard(
                weapon = weapon,
                onClick = { onOpenWeaponDetail(weapon.name) }
            )
        }

        // 底部留白
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WeaponCard(
    weapon: WeaponListApi.WeaponInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            // 武器图片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(smoothCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (weapon.imageUrl != null) {
                    AsyncImage(
                        model = weapon.imageUrl,
                        contentDescription = weapon.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                } else {
                    // 无图片占位
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.GpsFixed,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            // 武器信息
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = weapon.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 使用者（主武器）
                if (weapon.user.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = weapon.user,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 武器类型标签（主武器）
                if (weapon.type.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        shape = smoothCapsuleShape(),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = weapon.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                // 武器介绍（近战/副武器/战术道具，没有使用者时显示）
                if (weapon.user.isBlank() && weapon.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = weapon.description.replace("<br />", " ").replace("<br/>", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WeaponListSkeleton(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        items(6) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = smoothCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f),
                        shape = smoothCornerShape(16.dp)
                    )
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
                        Spacer(Modifier.height(4.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.55f).height(10.dp))
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.4f).height(10.dp))
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(Modifier.width(48.dp).height(18.dp), shape = smoothCapsuleShape())
                    }
                }
            }
        }
    }
}
