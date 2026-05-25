package com.nekolaska.calabiyau.feature.settings

import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 检查更新 API — 基于 Cloudflare Pages 上的 latest.json。
 *
 * 请求 `https://calabiyauwiki.pages.dev/downloads/latest.json`
 * 解析 versionName、versionCode、changelog、apkUrl。
 */
object UpdateApi {

    private const val API_URL =
        "https://calabiyauwiki.pages.dev/downloads/latest.json"

    private const val BASE_URL = "https://calabiyauwiki.pages.dev"

    /** 更新信息 */
    data class UpdateInfo(
        val tagName: String,        // e.g. "v2.1.0"
        val versionName: String,    // e.g. "2.1.0"（去掉 v 前缀）
        val versionCode: Long,
        val body: String,           // Release notes (Markdown)
        val htmlUrl: String,        // 下载页或 APK URL
        val apkUrl: String?         // APK 直链（如果有 .apk asset）
    )

    sealed interface Result {
        data class NewVersion(val info: UpdateInfo) : Result
        data object AlreadyLatest : Result
        data class Error(val message: String) : Result
    }

    // 独立的短超时 client，避免影响主 client
    private val client = WikiEngine.client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * 检查是否有新版本。
     * @param currentVersionName 当前版本名（如 "2.0.0"），仅用于旧 JSON 兜底比较。
     * @param currentVersionCode 当前 versionCode，优先用于更新判断。
     */
    suspend fun checkUpdate(
        currentVersionName: String,
        currentVersionCode: Long
    ): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val body = response.use {
                if (it.isSuccessful) it.body.string() else null
            } ?: return@withContext Result.Error("无法连接更新服务器")

            val json = SharedJson.parseToJsonElement(body).jsonObject
            val remoteVersion = json["versionName"]?.jsonPrimitive?.contentOrNull
                ?: json["version"]?.jsonPrimitive?.contentOrNull
                ?: return@withContext Result.Error("解析失败")
            val remoteVersionCode = json["versionCode"]?.jsonPrimitive?.longOrNull
            val changelog = parseChangelog(json)
            val apkUrl = json["apkUrl"]?.jsonPrimitive?.contentOrNull
                ?.let(::resolveUrl)
                ?: "$BASE_URL/downloads/CalabiYauVoice-latest.apk"
            val releaseUrl = json["releaseUrl"]?.jsonPrimitive?.contentOrNull
                ?.let(::resolveUrl)
                ?: apkUrl

            val info = UpdateInfo(
                tagName = "v${remoteVersion.removePrefix("v").removePrefix("V")}",
                versionName = remoteVersion.removePrefix("v").removePrefix("V"),
                versionCode = remoteVersionCode ?: 0L,
                body = changelog,
                htmlUrl = releaseUrl,
                apkUrl = apkUrl
            )

            if (remoteVersionCode?.let { it > currentVersionCode } ?: isNewer(info.versionName, currentVersionName)) {
                Result.NewVersion(info)
            } else {
                Result.AlreadyLatest
            }
        } catch (e: Exception) {
            Result.Error("检查失败: ${e.javaClass.simpleName}")
        }
    }

    private fun parseChangelog(json: JsonObject): String {
        val element = json["changelog"] ?: json["body"] ?: return ""
        return when (element) {
            is JsonArray -> element.joinToString("\n") { item ->
                "• ${item.jsonPrimitive.contentOrNull.orEmpty()}"
            }

            is JsonPrimitive -> element.contentOrNull.orEmpty()
            else -> ""
        }
    }

    private fun resolveUrl(url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> BASE_URL + url
        else -> "$BASE_URL/$url"
    }

    /**
     * 语义化版本比较：remote > current → true
     * 支持 "2.1.0" vs "2.0.0" 格式
     */
    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        val len = maxOf(r.size, c.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }
}
