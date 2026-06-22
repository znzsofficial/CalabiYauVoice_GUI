package com.nekolaska.calabiyau.core.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File

/**
 * 向系统 [DownloadManager] 注入一个下载任务。
 *
 * @param url 下载地址
 * @param dir 目标目录（自动创建）
 * @param fileName 保存文件名
 * @param description 通知描述
 * @param headers 可选的 HTTP 请求头（如 Cookie、User-Agent）
 * @param mimeType 可选的 MIME 类型
 * @return 下载任务 ID
 */
fun Context.enqueueDownload(
    url: String,
    dir: File,
    fileName: String,
    description: String = "正在保存...",
    headers: Map<String, String> = emptyMap(),
    mimeType: String? = null
): Long {
    if (!dir.exists()) dir.mkdirs()
    val request = DownloadManager.Request(url.toUri()).apply {
        setTitle(fileName)
        setDescription(description)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationUri(Uri.fromFile(File(dir, fileName)))
        headers.forEach { (k, v) -> addRequestHeader(k, v) }
        if (mimeType != null) setMimeType(mimeType)
    }
    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return dm.enqueue(request)
}
