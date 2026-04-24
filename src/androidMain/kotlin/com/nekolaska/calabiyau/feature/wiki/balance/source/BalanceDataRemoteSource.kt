package com.nekolaska.calabiyau.feature.wiki.balance.source

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class BalanceHttpResult(
    val code: Int,
    val body: String?
)

object BalanceDataRemoteSource {

    private const val BASE_URL = "https://klbq-prod-www.idreamsky.com"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun fetchSettingsBody(): BalanceHttpResult {
        val url = "$BASE_URL/api/pages/KLBQ_BALANCE/index"
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { resp ->
            val body = if (resp.isSuccessful) resp.body.string() else null
            BalanceHttpResult(resp.code, body)
        }
    }

    fun fetchBalanceDataBody(payload: String): BalanceHttpResult {
        val request = Request.Builder()
            .url("$BASE_URL/api/common/ide")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()

        return client.newCall(request).execute().use { resp ->
            val body = if (resp.isSuccessful) resp.body.string() else null
            BalanceHttpResult(resp.code, body)
        }
    }
}
