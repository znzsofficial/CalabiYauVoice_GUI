package com.nekolaska.calabiyau.feature.settings

import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import com.nekolaska.calabiyau.core.util.enqueueDownload
import java.io.File

/** The persistent system row stores the version identity, URL, download ID and chosen target together. */
internal object UpdateDownloadOwner {
    internal data class Row(val id: Long, val url: String?, val version: String?, val target: File?, val status: Int)

    internal fun reusable(rows: List<Row>, url: String, versions: Set<String>, root: File): Row? = rows
        .filter { it.url == url && it.version in versions && it.target != null && it.target.canonicalFile.parentFile == root.canonicalFile }
        .filter { it.status in setOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED) ||
            (it.status == DownloadManager.STATUS_SUCCESSFUL && it.target!!.isFile) }
        .maxByOrNull { it.id }

    /** One short lock covers lookup + allocation + enqueue from either update entry. Run on IO. */
    @Synchronized fun resolve(context: Context, info: UpdateApi.UpdateInfo, start: Boolean): UpdateApi.ApkDownload? {
        val root = context.getExternalFilesDir(null)?.canonicalFile ?: error("应用外部存储不可用")
        val dm = context.getSystemService(DownloadManager::class.java) ?: error("下载服务不可用")
        val url = UpdateApi.resolveApkUrl(info)
        val version = "应用更新 ${info.versionName}（${info.versionCode}）"
        val recognized = setOf(version, "正在下载 ${info.versionName}") // 2.2.1 rows also carry the exact artifact URL.
        val rows = dm.query(DownloadManager.Query())?.use { c -> buildList<Row> {
            while (c.moveToNext()) {
                fun string(col: String): String? = c.getString(c.getColumnIndexOrThrow(col))
                val local: String? = string(DownloadManager.COLUMN_LOCAL_URI)?.toUri()?.takeIf { it.scheme == "file" }?.path
                add(Row(c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)), string(DownloadManager.COLUMN_URI),
                    string(DownloadManager.COLUMN_DESCRIPTION), local?.let(::File), c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))))
            }
        } } ?: error("无法读取系统下载记录")
        reusable(rows, url, recognized, root)?.let { val file = checkNotNull(it.target); return UpdateApi.ApkDownload(it.id, file, file.name) }
        if (!start) return null
        check(rows.none { it.url == url && it.version in recognized && it.target == null && it.status in setOf(
            DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED) }) { "系统正在准备下载，请稍后重试" }
        val base = UpdateApi.apkFileName(info)
        require(File(base).name == base && base.endsWith(".apk", true)) { "安装包文件名无效" }
        val occupied = rows.mapNotNull { it.target?.canonicalPath }.toSet()
        var target = File(root, base); var n = 2
        while (target.exists() || target.canonicalPath in occupied) target = File(root, "${base.dropLast(4)}-copy${n++}.apk")
        val id = context.enqueueDownload(url, root, target.name, version, mimeType = "application/vnd.android.package-archive")
        return UpdateApi.ApkDownload(id, target, target.name)
    }
}
