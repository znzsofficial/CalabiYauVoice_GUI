package com.nekolaska.calabiyau.feature.wiki.tips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.LoadingState
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.feature.wiki.tips.api.GameTipsApi
import com.nekolaska.calabiyau.feature.wiki.tips.model.GAME_TIPS_PAGE_URL
import com.nekolaska.calabiyau.feature.wiki.tips.model.GameTipsSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameTipsScreen(
    onBack: () -> Unit,
    onOpenWikiUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state = rememberLoadState(emptyList<GameTipsSection>()) { force ->
        GameTipsApi.fetchGameTips(forceRefresh = force)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("游戏Tips", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackNavButton(onClick = onBack) },
                actions = {
                    FilledTonalIconButton(
                        onClick = { state.reload(forceRefresh = true) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { onOpenWikiUrl(GAME_TIPS_PAGE_URL) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "在浏览器中打开")
                    }
                    Spacer(Modifier.width(8.dp))
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> LoadingState("正在加载游戏Tips…", mod) }
        ) { sections ->
            val tips = remember(sections) { sections.flatMap { it.tips } }
            var tipRefreshKey by remember(sections) { mutableStateOf(0) }
            var randomTip by remember(sections) { mutableStateOf(tips.randomOrNull()) }

            LaunchedEffect(tips, tipRefreshKey) {
                randomTip = tips.randomOrNull()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                randomTip?.let { tip ->
                    item(key = "random-game-tip") {
                        RandomGameTipCard(
                            tip = tip,
                            onRefresh = { tipRefreshKey++ },
                            onCopy = { copyTipText(context, tip, showSnack) }
                        )
                    }
                }
                items(sections, key = { it.title }) { section ->
                    GameTipsSectionCard(
                        section = section,
                        onCopyText = { copyTipText(context, it, showSnack) }
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun RandomGameTipCard(
    tip: String,
    onRefresh: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        shape = smoothCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = smoothCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.FormatQuote,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Text(
                    text = "随机游戏Tips",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier.wrapContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onCopy,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        )
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制")
                    }
                    FilledTonalIconButton(
                        onClick = onRefresh,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        )
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "换一条")
                    }
                }
            }

            Surface(
                shape = smoothCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = tip,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 23.sp
                )
            }
        }
    }
}

@Composable
private fun GameTipsSectionCard(
    section: GameTipsSection,
    onCopyText: (String) -> Unit
) {
    Card(
        shape = smoothCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = smoothCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${section.tips.size} 条内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                section.tips.forEach { tip ->
                    CopyableTipText(
                        text = "• $tip",
                        copyText = tip,
                        onCopy = onCopyText
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyableTipText(
    text: String,
    copyText: String,
    onCopy: (String) -> Unit
) {
    Text(
        text = text,
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = { onCopy(copyText) }
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 20.sp
    )
}

private fun copyTipText(context: Context, text: String, showSnack: (String) -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("游戏Tips", text))
    showSnack("已复制")
}
