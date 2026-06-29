package com.nekolaska.calabiyau.feature.wiki.balance.api

import com.nekolaska.calabiyau.core.cache.CachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceResult
import com.nekolaska.calabiyau.feature.wiki.balance.model.BalanceSettings
import com.nekolaska.calabiyau.feature.wiki.balance.parser.BalanceDataParsers
import com.nekolaska.calabiyau.feature.wiki.balance.source.BalanceDataRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 官网平衡数据 API（Android）。 */
object BalanceDataApi : CachedWikiApi<BalanceSettings>("BalanceDataApi") {

    private const val CHART_ID = "338985"
    private const val IDE_TOKEN = "b7FM3m"

    suspend fun fetchSettings(forceRefresh: Boolean = false): ApiResult<BalanceSettings> {
        return fetch(forceRefresh = forceRefresh)
    }

    override suspend fun fetchFromCache(): ApiResult<BalanceSettings> =
        ApiResult.Error("平衡数据不支持 cacheOnly", kind = ErrorKind.NETWORK)

    override suspend fun fetchFromNetwork(forceRefresh: Boolean): ApiResult<BalanceSettings> =
        withContext(Dispatchers.IO) {
            try {
                val result = BalanceDataRemoteSource.fetchSettingsBody()
                if (result.code !in 200..299) {
                    return@withContext ApiResult.Error(
                        "HTTP ${result.code}",
                        kind = when (result.code) {
                            403, 429, 503 -> ErrorKind.CDN_BLOCKED
                            404 -> ErrorKind.NOT_FOUND
                            else -> ErrorKind.NETWORK
                        }
                    )
                }

                val body = result.body ?: return@withContext ApiResult.Error(
                    "网络错误",
                    kind = ErrorKind.NETWORK
                )

                val settings = try {
                    BalanceDataParsers.parseSettings(body)
                } catch (e: IllegalStateException) {
                    if (e.message?.startsWith("SERVER_MSG:") == true) {
                        val msg = e.message!!.removePrefix("SERVER_MSG:")
                        return@withContext ApiResult.Error(msg, kind = ErrorKind.PARSE)
                    }
                    throw e
                }

                ApiResult.Success(settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Error(e.message ?: "网络错误", kind = e.toErrorKind())
            }
        }

    suspend fun fetchBalanceData(
        modeCode: String,
        mapCode: String,
        rankCodes: List<String>,
        season1Code: String,
        season2Code: String = "0"
    ): ApiResult<BalanceResult> = withContext(Dispatchers.IO) {
        try {
            val payload = BalanceDataParsers.buildBalancePayload(
                chartId = CHART_ID,
                ideToken = IDE_TOKEN,
                modeCode = modeCode,
                mapCode = mapCode,
                rankCodes = rankCodes,
                season1Code = season1Code,
                season2Code = season2Code
            )

            val result = BalanceDataRemoteSource.fetchBalanceDataBody(payload)
            if (result.code !in 200..299) {
                return@withContext ApiResult.Error(
                    "HTTP ${result.code}",
                    kind = when (result.code) {
                        403, 429, 503 -> ErrorKind.CDN_BLOCKED
                        404 -> ErrorKind.NOT_FOUND
                        else -> ErrorKind.NETWORK
                    }
                )
            }

            val body = result.body ?: return@withContext ApiResult.Error(
                "网络错误",
                kind = ErrorKind.NETWORK
            )

            val parsed = try {
                BalanceDataParsers.parseBalanceResult(body)
            } catch (e: NoSuchElementException) {
                return@withContext ApiResult.Error(
                    e.message ?: "查询失败",
                    kind = ErrorKind.NOT_FOUND
                )
            } catch (e: IllegalStateException) {
                return@withContext ApiResult.Error(
                    e.message ?: "返回数据格式异常",
                    kind = ErrorKind.PARSE
                )
            }

            ApiResult.Success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "网络错误", kind = e.toErrorKind())
        }
    }
}
