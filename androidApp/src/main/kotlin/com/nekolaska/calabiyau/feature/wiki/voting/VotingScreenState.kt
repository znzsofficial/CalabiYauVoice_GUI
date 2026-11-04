package com.nekolaska.calabiyau.feature.wiki.voting

import androidx.compose.runtime.*
import com.nekolaska.calabiyau.feature.wiki.hub.hasWikiLoginCookie
import com.nekolaska.calabiyau.feature.wiki.voting.api.VotingApi
import com.nekolaska.calabiyau.feature.wiki.voting.model.*
import data.ApiResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Writes are explicit, single-flight and never retried; reads reconcile even partial write failures. */
internal class VotingScreenState(
    private val loggedIn: () -> Boolean = ::hasWikiLoginCookie,
    private val fetchConfig: suspend () -> ApiResult<PollConfig> = VotingApi::fetchPollConfig,
    private val fetchVotes: suspend (PollConfig) -> ApiResult<VoteState> = VotingApi::fetchVoteData,
    private val writeVotes: suspend (VoteState, Set<String>) -> ApiResult<Unit> = VotingApi::submitVotes
) {
    var isLoadingConfig by mutableStateOf(true)
        private set
    var isLoadingData by mutableStateOf(false)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var config by mutableStateOf<PollConfig?>(null)
        private set
    var votes by mutableStateOf<VoteState?>(null)
        private set
    var selectedNames by mutableStateOf<Set<String>>(emptySet())
        private set
    var isLoggedIn by mutableStateOf(false)
        private set
    private var version = 0L

    fun toggle(name: String) {
        if (isSubmitting || isLoadingConfig || isLoadingData || config?.candidates?.none { it.name == name } != false) return
        selectedNames = if (name in selectedNames) selectedNames - name else selectedNames + name
    }

    suspend fun load() {
        if (isSubmitting) return
        val ticket = ++version
        isLoadingConfig = true
        isLoadingData = false
        isLoggedIn = loggedIn()
        errorMessage = null
        config = null
        votes = null
        selectedNames = emptySet()
        try {
            val response = fetchConfig()
            currentCoroutineContext().ensureActive()
            if (ticket != version) return
            when (response) {
                is ApiResult.Error -> errorMessage = response.message
                is ApiResult.Success -> {
                    config = response.value
                    if (isLoggedIn) {
                        isLoadingData = true
                        val data = fetchVotes(response.value)
                        currentCoroutineContext().ensureActive()
                        if (ticket != version) return
                        when (data) {
                            is ApiResult.Success -> accept(data.value)
                            is ApiResult.Error -> errorMessage = data.message
                        }
                    }
                }
            }
        } finally {
            if (ticket == version) { isLoadingConfig = false; isLoadingData = false }
        }
    }

    suspend fun submit(): String? {
        val before = votes ?: return null
        if (isSubmitting || isLoadingConfig || isLoadingData || !loggedIn()) return null
        val submitted = selectedNames.toSet()
        isSubmitting = true
        ++version // Any earlier read may no longer publish over this mutation.
        try {
            val result = writeVotes(before, submitted)
            currentCoroutineContext().ensureActive()
            if (result is ApiResult.Success) votes = before.withSelectedNames(submitted)
            isLoadingData = true
            val refreshed = fetchVotes(before.config)
            currentCoroutineContext().ensureActive()
            if (refreshed is ApiResult.Success) accept(refreshed.value)
            return when {
                result is ApiResult.Success && refreshed is ApiResult.Success -> "投票提交成功！"
                result is ApiResult.Success && refreshed is ApiResult.Error -> "投票已提交，但状态刷新失败：${refreshed.message}"
                result is ApiResult.Error && refreshed is ApiResult.Success -> "提交未完全成功，已同步服务器状态：${result.message}"
                result is ApiResult.Error && refreshed is ApiResult.Error -> "提交失败且状态刷新失败：${result.message}；${refreshed.message}"
                else -> null
            }
        } finally {
            isLoadingData = false
            isSubmitting = false
        }
    }

    private fun accept(state: VoteState) {
        votes = state
        selectedNames = state.pollDataMap.filterValues { it.userVoted }.keys.toSet()
    }
}
