package com.nekolaska.calabiyau.feature.settings

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.net.Uri
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import data.SharedJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

internal data class PendingAppUpdateInstall(
    val versionCode: Long,
    val versionName: String,
    val apkPath: String
) {
    fun encode(): String = SharedJson.encodeToString(buildJsonObject {
        put("versionCode", versionCode)
        put("versionName", versionName)
        put("apkPath", apkPath)
    })

    companion object {
        fun decode(value: String?): PendingAppUpdateInstall? = runCatching {
            val json = SharedJson.parseToJsonElement(value ?: return null).jsonObject
            PendingAppUpdateInstall(
                versionCode = json["versionCode"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                versionName = json["versionName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                apkPath = json["apkPath"]?.jsonPrimitive?.contentOrNull.orEmpty()
            ).takeIf { it.versionCode > 0 || it.versionName.isNotBlank() }
        }.getOrNull()
    }
}

internal data class UpdateArchiveInfo(val packageName: String, val versionCode: Long)

internal object UpdateApkCleanup {
    private val updaterFileName = Regex("CalabiYauVoice-(?:\\d+(?:\\.\\d+){1,3}|latest)\\.apk", RegexOption.IGNORE_CASE)

    fun targetVersionInstalled(
        pending: PendingAppUpdateInstall,
        installedVersionCode: Long,
        installedVersionName: String
    ): Boolean = if (pending.versionCode > 0) installedVersionCode >= pending.versionCode
        else installedVersionName == pending.versionName

    fun isObsoleteUpdaterApk(
        fileName: String,
        archive: UpdateArchiveInfo?,
        expectedPackageName: String,
        installedVersionCode: Long,
        activeDownload: Boolean
    ): Boolean = !activeDownload && updaterFileName.matches(fileName) && archive?.packageName == expectedPackageName &&
        archive.versionCode in 1..installedVersionCode

    fun canSweepLegacyApks(downloadQuerySucceeded: Boolean, hasActiveDownloadWithoutFilePath: Boolean): Boolean =
        downloadQuerySucceeded && !hasActiveDownloadWithoutFilePath

    fun canDeletePendingApk(
        downloadQuerySucceeded: Boolean,
        targetPath: String?,
        activePaths: Set<String>,
        hasUnknownActivePath: Boolean
    ): Boolean = downloadQuerySucceeded && targetPath != null && !hasUnknownActivePath && targetPath !in activePaths

    /** Returns true when the installed version makes the marker consumable (deleted, already absent or invalid path). */
    internal fun cleanupInstalledPendingApk(
        pending: PendingAppUpdateInstall,
        installedVersionCode: Long,
        installedVersionName: String,
        roots: List<File>,
        onOwnedPathRemoved: (String) -> Unit = {},
        delete: (File) -> Boolean = { it.delete() }
    ): Boolean {
        if (!targetVersionInstalled(pending, installedVersionCode, installedVersionName)) return false
        val target = safeUpdaterFile(pending.apkPath, roots) ?: return true
        val path = runCatching { target.canonicalPath }.getOrNull() ?: return false
        if (!target.exists()) {
            onOwnedPathRemoved(path)
            return true
        }
        if (!delete(target)) return false
        onOwnedPathRemoved(path)
        return true
    }

    /** Runs off-main at app startup. Never removes a failed/cancelled or still-running newer download. */
    fun cleanupAfterLaunch(context: Context): Int {
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull() ?: return 0
        val installedCode = installed.releaseVersionCode()
        val installedName = installed.versionName.orEmpty()
        // UpdateApi stores DownloadManager APKs under this root. If the volume is unavailable,
        // preserve the marker so a later startup can clean the now-mounted file.
        val updateRoot = context.getExternalFilesDir(null) ?: return 0
        val roots = listOf(updateRoot, context.filesDir)
            .mapNotNull { runCatching { it.canonicalFile }.getOrNull() }.distinctBy { it.path }
        if (roots.isEmpty()) return 0

        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val downloads = downloadManager?.let(::readOwnedDownloads)
        val activeDownloads = downloads.orEmpty().filter { it.status in ACTIVE_DOWNLOAD_STATUSES }
        val activePaths = activeDownloads.mapNotNull { it.path }.toSet()
        var deleted = 0

        val pending = PendingAppUpdateInstall.decode(AppPrefs.pendingAppUpdateInstallJson)
        if (pending != null && targetVersionInstalled(pending, installedCode, installedName)) {
            val target = safeUpdaterFile(pending.apkPath, roots)
            val targetPath = target?.let { runCatching { it.canonicalPath }.getOrNull() }
            val hasUnknownActivePath = activeDownloads.any { it.path == null }
            val activeNames = activePaths.mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }.toSet()
            if (canDeletePendingApk(downloads != null, targetPath, activeNames, hasUnknownActivePath) &&
                cleanupInstalledPendingApk(pending, installedCode, installedName, roots, onOwnedPathRemoved = { path ->
                    removeDownloadRecords(downloadManager, downloads, path)
                })) {
                if (target?.exists() == false) {
                    deleted++
                }
                AppPrefs.persistPendingAppUpdateInstall(null)
            }
        }

        // Migration cleanup for packages downloaded by older app versions before the install marker existed.
        // On a DownloadManager query failure, skip this broad cleanup rather than risk an active destination.
        val hasUnknownActivePath = activeDownloads.any { it.path == null }
        if (!canSweepLegacyApks(downloads != null, hasUnknownActivePath)) return deleted
        val knownDownloads = checkNotNull(downloads)
        val activeNames = activePaths.mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }.toSet()
        roots.forEach { root ->
            root.listFiles()?.forEach { file ->
                if (!file.isFile || !updaterFileName.matches(file.name)) return@forEach
                val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return@forEach
                if (canonical.parentFile != root || canonical.path in activeNames) return@forEach
                val archive = readArchiveInfo(context, file)
                if (isObsoleteUpdaterApk(file.name, archive, context.packageName, installedCode, canonical.path in activeNames)) {
                    if (file.delete()) {
                        deleted++
                        removeDownloadRecords(downloadManager, knownDownloads, canonical.path)
                    }
                }
            }
        }
        return deleted
    }

    private data class OwnedDownload(val id: Long, val status: Int, val path: String?)

    private fun removeDownloadRecords(manager: DownloadManager?, downloads: List<OwnedDownload>?, path: String) {
        if (manager == null || downloads == null) return
        downloads.filter { row -> row.path?.let { runCatching { File(it).canonicalPath }.getOrNull() } == path }
            .forEach { row -> runCatching { manager.remove(row.id) } }
    }

    private fun readOwnedDownloads(manager: DownloadManager): List<OwnedDownload>? = runCatching {
        manager.query(DownloadManager.Query())?.use { cursor ->
            buildList<OwnedDownload> {
                val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val uriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                while (cursor.moveToNext()) {
                    val rawUri: String? = if (uriColumn >= 0) cursor.getString(uriColumn) else null
                    val parsedUri: Uri? = rawUri?.let { raw -> Uri.parse(raw) }
                    val path: String? = parsedUri?.let { uri ->
                        if (uri.scheme.equals("file", true)) uri.path else null
                    }
                    add(OwnedDownload(cursor.getLong(idColumn), cursor.getInt(statusColumn), path))
                }
            }
        } ?: error("DownloadManager query unavailable")
    }.getOrNull()

    private fun safeUpdaterFile(path: String, roots: List<File>): File? {
        if (!isOwnedUpdaterPath(path, roots)) return null
        return runCatching { File(path).canonicalFile }.getOrNull()
    }

    internal fun isOwnedUpdaterPath(path: String, roots: List<File>): Boolean {
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        return updaterFileName.matches(file.name) && roots.any { file.parentFile == it.canonicalFile }
    }

    private fun readArchiveInfo(context: Context, file: File): UpdateArchiveInfo? = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        UpdateArchiveInfo(info.packageName, info.releaseVersionCode())
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun PackageInfo.releaseVersionCode(): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else versionCode.toLong()

    private val ACTIVE_DOWNLOAD_STATUSES = setOf(
        DownloadManager.STATUS_PENDING,
        DownloadManager.STATUS_RUNNING,
        DownloadManager.STATUS_PAUSED
    )
}
