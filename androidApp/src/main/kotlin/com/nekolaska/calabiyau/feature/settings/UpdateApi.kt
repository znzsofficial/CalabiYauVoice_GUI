package com.nekolaska.calabiyau.feature.settings

import com.nekolaska.calabiyau.core.wiki.WikiEngine
import data.SharedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 检查更新 API — 基于 GitHub Releases。
 *
 * 请求 `https://api.github.com/repos/znzsofficial/CalabiYauVoice_GUI/releases/latest`
 * 解析 tag_name（版本号）、body（更新日志）、html_url（Release 页面链接）。
 */
object UpdateApi {

    private const val API_URL =
        "https://api.github.com/repos/znzsofficial/CalabiYauVoice_GUI/releases/latest"

    /** 更新信息 */
    data class UpdateInfo(
        val tagName: String,        // e.g. "v2.1.0"
        val versionName: String,    // e.g. "2.1.0"（去掉 v 前缀）
        val body: String,           // Release notes (Markdown)
        val htmlUrl: String,        // Release 页面 URL
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
     * @param currentVersion 当前版本号（如 "2.0.0"）
     */
    suspend fun checkUpdate(currentVersion: String): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            val response = client.newCall(request).execute()
            val body = response.use {
                if (it.isSuccessful) it.body.string() else null
            } ?: return@withContext Result.Error("无法连接 GitHub")

            val json = SharedJson.parseToJsonElement(body).jsonObject
            val tagName = json["tag_name"]?.jsonPrimitive?.content
                ?: return@withContext Result.Error("解析失败")
            val releaseBody = json["body"]?.jsonPrimitive?.content ?: ""
            val htmlUrl = json["html_url"]?.jsonPrimitive?.content
                ?: "https://github.com/znzsofficial/CalabiYauVoice_GUI/releases"

            // 尝试从 assets 中找 .apk 文件
            val apkUrl = json["assets"]?.jsonArray?.firstOrNull { asset ->
                asset.jsonObject["name"]?.jsonPrimitive?.content
                    ?.endsWith(".apk", ignoreCase = true) == true
            }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content

            val remoteVersion = tagName.removePrefix("v").removePrefix("V")
            val info = UpdateInfo(
                tagName = tagName,
                versionName = remoteVersion,
                body = releaseBody,
                htmlUrl = htmlUrl,
                apkUrl = apkUrl
            )

            if (isNewer(remoteVersion, currentVersion)) {
                Result.NewVersion(info)
            } else {
                Result.AlreadyLatest
            }
        } catch (e: Exception) {
            Result.Error("检查失败: ${e.javaClass.simpleName}")
        }
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
