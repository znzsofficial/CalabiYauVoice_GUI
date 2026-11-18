package com.nekolaska.calabiyau.feature.wiki.balance.source

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import com.nekolaska.calabiyau.core.wiki.WikiEngine
import okhttp3.Request
import okhttp3.OkHttpClient
import util.awaitResponseText

data class BalanceHttpResult(
    val code: Int,
    val body: String?
)

object BalanceDataRemoteSource {

    private const val BASE_URL = "https://klbq-prod-www.idreamsky.com"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val client = WikiEngine.client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchSettingsBody(): BalanceHttpResult {
        val url = "$BASE_URL/api/pages/KLBQ_BALANCE/index"
        return requestBody(client, Request.Builder().url(url).build())
    }

    suspend fun fetchBalanceDataBody(payload: String): BalanceHttpResult =
        postQuery(client, "$BASE_URL/api/common/ide", payload)

    internal suspend fun postQuery(client: OkHttpClient, url: String, payload: String): BalanceHttpResult =
        requestBody(client, Request.Builder().url(url).post(payload.toRequestBody(JSON_MEDIA)).build())

    private suspend fun requestBody(client: OkHttpClient, request: Request): BalanceHttpResult {
        val response = client.newCall(request).awaitResponseText()
        return BalanceHttpResult(response.code, response.body.takeIf { response.isSuccessful })
    }
}
