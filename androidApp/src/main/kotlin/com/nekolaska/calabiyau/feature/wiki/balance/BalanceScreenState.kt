package com.nekolaska.calabiyau.feature.wiki.balance

import androidx.compose.runtime.*
import com.nekolaska.calabiyau.feature.wiki.balance.api.BalanceDataApi
import com.nekolaska.calabiyau.feature.wiki.balance.model.*
import data.ApiResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Request orchestration only. Applied/draft filters retain their own saved, pure state machine. */
internal class BalanceScreenState(
    private val fetchSettings: suspend (Boolean) -> ApiResult<BalanceSettings> = BalanceDataApi::fetchSettings,
    private val fetchData: suspend (BalanceDataQuery) -> ApiResult<BalanceResult> = { query ->
        BalanceDataApi.fetchBalanceData(query.mode, query.map, query.ranks, query.season, query.compareSeason)
    }
) {
    var settings by mutableStateOf<BalanceSettings?>(null)
        private set
    var result by mutableStateOf<BalanceResult?>(null)
        private set
    var isLoadingSettings by mutableStateOf(true)
        private set
    var isLoadingData by mutableStateOf(false)
        private set
    var settingsError by mutableStateOf<ApiResult.Error?>(null)
        private set
    var dataError by mutableStateOf<ApiResult.Error?>(null)
        private set
    private var settingsVersion = 0L
    private var dataVersion = 0L

    suspend fun loadSettings(force: Boolean): BalanceSettings? {
        val ticket = ++settingsVersion
        isLoadingSettings = true
        settingsError = null
        return try {
            val response = fetchSettings(force)
            currentCoroutineContext().ensureActive()
            if (ticket != settingsVersion) null else when (response) {
                is ApiResult.Success -> response.value.also { settings = it }
                is ApiResult.Error -> { settingsError = response; null }
            }
        } finally {
            if (ticket == settingsVersion) isLoadingSettings = false
        }
    }

    suspend fun loadData(query: BalanceDataQuery?) {
        val ticket = ++dataVersion
        if (query == null) { result = null; dataError = null; isLoadingData = false; return }
        isLoadingData = true
        dataError = null
        try {
            val response = fetchData(query)
            currentCoroutineContext().ensureActive()
            if (ticket != dataVersion) return
            when (response) {
                is ApiResult.Success -> { result = response.value; settingsError = null }
                is ApiResult.Error -> { result = null; dataError = response }
            }
        } finally {
            if (ticket == dataVersion) isLoadingData = false
        }
    }
}
