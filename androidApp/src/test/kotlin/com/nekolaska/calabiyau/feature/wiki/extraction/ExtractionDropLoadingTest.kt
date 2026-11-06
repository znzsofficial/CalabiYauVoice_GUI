package com.nekolaska.calabiyau.feature.wiki.extraction

import androidx.compose.runtime.mutableStateOf
import com.nekolaska.calabiyau.core.ui.LoadState
import com.nekolaska.calabiyau.feature.wiki.extraction.model.ExtractionEntry
import data.ApiResult
import kotlinx.coroutines.*
import kotlin.test.*

/** Exercise the same independent, retained LoadStates used by the drops screen, without real network/UI. */
class ExtractionDropLoadingTest {
    private val cached = listOf(ExtractionEntry("old", "旧池", "容器"))
    private val fresh = listOf(ExtractionEntry("new", "新池", "容器"))

    @Test fun lateDiskPrefetchNeverReplacesSuccessfulNetworkIncludingEmptyResults() {
        for (response in listOf(fresh, emptyList())) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val disk = CompletableDeferred<ApiResult<List<ExtractionEntry>>>()
                val network = CompletableDeferred<ApiResult<List<ExtractionEntry>>>()
                val state = LoadState(emptyList<ExtractionEntry>(), scope,
                    mutableStateOf<suspend (Boolean) -> ApiResult<List<ExtractionEntry>>>({ network.await() }),
                    mutableStateOf<suspend () -> ApiResult<List<ExtractionEntry>>>({ disk.await() }))
                state.reload()
                network.complete(ApiResult.Success(response))
                disk.complete(ApiResult.Success(cached, isOffline = true, cacheAgeMs = 99))
                assertEquals(response, state.data)
                assertFalse(state.isOffline)
                assertFalse(state.isLoading)
            } finally { scope.cancel() }
        }
    }

    @Test fun failedSecondaryLoadDoesNotEraseSourceDataAndRetryRetainsLastGoodPool() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val catalog = LoadState(emptyList<ExtractionEntry>(), scope,
                mutableStateOf<suspend (Boolean) -> ApiResult<List<ExtractionEntry>>>({ ApiResult.Success(fresh) }))
            var response: ApiResult<List<ExtractionEntry>> = ApiResult.Error("完整表暂不可用")
            val containers = LoadState(emptyList<ExtractionEntry>(), scope,
                mutableStateOf<suspend (Boolean) -> ApiResult<List<ExtractionEntry>>>({ response }))
            catalog.reload()
            containers.reload()
            assertEquals(fresh, catalog.data)
            assertNull(catalog.error)
            assertNotNull(containers.error)
            response = ApiResult.Success(cached)
            containers.reload(forceRefresh = true)
            assertNull(containers.error)
            response = ApiResult.Error("结构不完整")
            containers.reload(forceRefresh = true)
            assertEquals(cached, containers.data)
            assertNotNull(containers.error)
            assertFalse(containers.isLoading)
        } finally { scope.cancel() }
    }

    @Test fun offlinePrefetchIsLabeledAndFreshValidationReplacesIt() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val network = CompletableDeferred<ApiResult<List<ExtractionEntry>>>()
            val state = LoadState(emptyList<ExtractionEntry>(), scope,
                mutableStateOf<suspend (Boolean) -> ApiResult<List<ExtractionEntry>>>({ network.await() }),
                mutableStateOf<suspend () -> ApiResult<List<ExtractionEntry>>>({ ApiResult.Success(cached, isOffline = true, cacheAgeMs = 456) }))
            state.reload()
            assertEquals(cached, state.data)
            assertTrue(state.isOffline)
            assertTrue(state.isShowingCachedPrefetch)
            assertEquals(456L, state.cacheAgeMs)
            network.complete(ApiResult.Success(fresh))
            assertEquals(fresh, state.data)
            assertFalse(state.isOffline)
            assertFalse(state.isShowingCachedPrefetch)
        } finally { scope.cancel() }
    }
}
