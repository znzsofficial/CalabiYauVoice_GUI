package com.nekolaska.calabiyau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.outlined.*
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
import com.nekolaska.calabiyau.data.VotingApi
import com.nekolaska.calabiyau.ui.shared.BackNavButton
import com.nekolaska.calabiyau.ui.shared.ErrorState
import com.nekolaska.calabiyau.ui.shared.smoothCornerShape
import com.nekolaska.calabiyau.ui.wiki.hasWikiLoginCookie
import data.ApiResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VotingScreen(onBack: () -> Unit, embedded: Boolean = false) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 状态
    var isLoadingConfig by remember { mutableStateOf(true) }
    var isLoadingData by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pollConfig by remember { mutableStateOf<VotingApi.PollConfig?>(null) }
    var voteState by remember { mutableStateOf<VotingApi.VoteState?>(null) }
    var selectedNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoggedIn by remember { mutableStateOf(hasWikiLoginCookie()) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // 加载投票页面配置
    LaunchedEffect(retryTrigger) {
        isLoadingConfig = true
        errorMessage = null
        when (val result = VotingApi.fetchPollConfig()) {
            is ApiResult.Success -> {
                pollConfig = result.value
                // 继续加载投票数据
                if (isLoggedIn) {
                    isLoadingData = true
                    when (val dataResult = VotingApi.fetchVoteData(result.value)) {
                        is ApiResult.Success -> {
                            voteState = dataResult.value
                            // 恢复用户之前的投票选择
                            selectedNames = dataResult.value.pollDataMap
                                .filter { it.value.userVoted }
                                .keys
                        }
                        is ApiResult.Error -> {
                            errorMessage = dataResult.message
                        }
                    }
                    isLoadingData = false
                }
            }
            is ApiResult.Error -> {
                errorMessage = result.message
            }
        }
        isLoadingConfig = false
    }

    if (!embedded) {
        BackHandler { onBack() }
    }

    Scaffold(
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = {
                        Text(
                            "时装投票",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        BackNavButton(onClick = onBack)
                    },
                    actions = {
                        // 刷新按钮
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isLoadingConfig = true
                                    errorMessage = null
                                    isLoggedIn = hasWikiLoginCookie()
                                    when (val result = VotingApi.fetchPollConfig()) {
                                        is ApiResult.Success -> {
                                            pollConfig = result.value
                                            if (isLoggedIn) {
                                                isLoadingData = true
                                                when (val dataResult = VotingApi.fetchVoteData(result.value)) {
                                                    is ApiResult.Success -> {
                                                        voteState = dataResult.value
                                                        selectedNames = dataResult.value.pollDataMap
                                                            .filter { it.value.userVoted }
                                                            .keys
                                                    }
                                                    is ApiResult.Error -> {
                                                        errorMessage = dataResult.message
                                                    }
                                                }
                                                isLoadingData = false
                                            }
                                        }
                                        is ApiResult.Error -> {
                                            errorMessage = result.message
                                        }
                                    }
                                    isLoadingConfig = false
                                }
                            },
                            enabled = !isLoadingConfig && !isLoadingData
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = smoothCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        },
        bottomBar = {
            // 提交投票栏
            if (isLoggedIn && pollConfig != null && !isLoadingConfig && !isLoadingData) {
                VotingBottomBar(
                    selectedCount = selectedNames.size,
                    hasChanges = hasVoteChanges(voteState, selectedNames),
                    isSubmitting = isSubmitting,
                    onSubmit = {
                        val state = voteState ?: return@VotingBottomBar
                        scope.launch {
                            isSubmitting = true
                            when (val result = VotingApi.submitVotes(state, selectedNames)) {
                                is ApiResult.Success -> {
                                    snackbarHostState.showSnackbar("投票提交成功！")
                                    // 刷新数据
                                    isLoadingData = true
                                    when (val refreshResult = VotingApi.fetchVoteData(state.config)) {
                                        is ApiResult.Success -> {
                                            voteState = refreshResult.value
                                            selectedNames = refreshResult.value.pollDataMap
                                                .filter { it.value.userVoted }
                                                .keys
                                        }
                                        is ApiResult.Error -> { /* 忽略刷新失败 */ }
                                    }
                                    isLoadingData = false
                                }
                                is ApiResult.Error -> {
                                    snackbarHostState.showSnackbar("提交失败: ${result.message}")
                                }
                            }
                            isSubmitting = false
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when {
                isLoadingConfig -> {
                    // 加载中
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("正在加载投票页面…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                errorMessage != null && pollConfig == null -> {
                    ErrorState(
                        message = errorMessage!!,
                        onRetry = { retryTrigger++ }
                    )
                }
                pollConfig != null -> {
                    VotingContent(
                        config = pollConfig!!,
                        voteState = voteState,
                        selectedNames = selectedNames,
                        isLoadingData = isLoadingData,
                        isLoggedIn = isLoggedIn,
                        onToggleCandidate = { name ->
                            selectedNames = if (name in selectedNames) {
                                selectedNames - name
                            } else {
                                selectedNames + name
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────── 投票内容 ───────────────────────

@Composable
private fun VotingContent(
    config: VotingApi.PollConfig,
    voteState: VotingApi.VoteState?,
    selectedNames: Set<String>,
    isLoadingData: Boolean,
    isLoggedIn: Boolean,
    onToggleCandidate: (String) -> Unit
) {
    // 计算预览总人数
    val previewTotal = if (voteState != null) {
        var total = voteState.totalParticipants
        if (!voteState.userVotedTotal && selectedNames.isNotEmpty()) total += 1
        else if (voteState.userVotedTotal && selectedNames.isEmpty()) total -= 1
        total
    } else 0

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 标题和统计信息
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 投票标题
                Text(
                    text = "传说时装 & 宿舍时装投票",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 提示信息
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = smoothCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "本投票不设置结束时间和票数限制",
                            "投票的时装会随游戏更新增加",
                            "可以同时投给多个时装且投票可重新提交"
                        ).forEach { hint ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // 未登录提示
                if (!isLoggedIn) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = smoothCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "请先在 Wiki 页面登录后再投票",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // 投票统计
                if (voteState != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.People,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "累计投票人数：$previewTotal",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 数据加载中指示
                if (isLoadingData) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "正在加载投票数据…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 候选项卡片网格
        items(config.candidates, key = { it.name }) { candidate ->
            val isSelected = candidate.name in selectedNames
            val pollData = voteState?.pollDataMap?.get(candidate.name)
            val previewVotes = if (pollData != null) {
                var v = pollData.votes
                if (isSelected && !pollData.userVoted) v += 1
                if (!isSelected && pollData.userVoted) v -= 1
                v
            } else null
            val rate = if (previewVotes != null && previewTotal > 0) {
                (previewVotes.toFloat() / previewTotal * 100)
            } else null

            CandidateCard(
                name = candidate.name,
                imageUrl = candidate.imageUrl,
                votes = previewVotes,
                rate = rate,
                isSelected = isSelected,
                isLoggedIn = isLoggedIn,
                onClick = { if (isLoggedIn) onToggleCandidate(candidate.name) }
            )
        }

        // 底部留白
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────── 候选卡片 ───────────────────────

@Composable
private fun CandidateCard(
    name: String,
    imageUrl: String,
    votes: Int?,
    rate: Float?,
    isSelected: Boolean,
    isLoggedIn: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Card(
        onClick = onClick,
        enabled = isLoggedIn,
        modifier = Modifier.fillMaxWidth(),
        shape = smoothCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        )
    ) {
        Column {
            // 图片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(smoothCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // 选中指示器
                if (isSelected) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp),
                        shape = smoothCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "已选",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            }

            // 信息区
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 时装名称
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                // 投票信息
                if (votes != null) {
                    Text(
                        text = if (rate != null) "支持 $votes（${"%.1f".format(rate)}%）"
                        else "支持 $votes",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "加载中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ─────────────────────── 底部提交栏 ───────────────────────

@Composable
private fun VotingBottomBar(
    selectedCount: Int,
    hasChanges: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 选中数量提示
            Icon(
                Icons.Filled.HowToVote,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "已选 $selectedCount 项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // 提交按钮
            FilledTonalButton(
                onClick = onSubmit,
                enabled = hasChanges && !isSubmitting,
                shape = smoothCornerShape(12.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("提交中…")
                } else {
                    Icon(
                        Icons.Outlined.HowToVote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasChanges) "提交投票" else "无变更")
                }
            }
        }
    }
}

// ─────────────────────── 工具函数 ───────────────────────

/** 检查用户投票选择是否有变更 */
private fun hasVoteChanges(
    voteState: VotingApi.VoteState?,
    selectedNames: Set<String>
): Boolean {
    if (voteState == null) return false
    for (candidate in voteState.config.candidates) {
        val wasVoted = voteState.pollDataMap[candidate.name]?.userVoted ?: false
        val isSelected = candidate.name in selectedNames
        if (wasVoted != isSelected) return true
    }
    return false
}
