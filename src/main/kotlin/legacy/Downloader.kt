package legacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import util.clearAll
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

object Downloader {
    lateinit var TARGET_URL: String  // 星绘
    lateinit var SAVE_DIR: String   // Windows 保存路径
    var CLEAR_DIR = false  // 是否清除输出文件夹
    lateinit var DOWNLOAD_LANGUAGE: String  // 修改这里来选择语言 CN EN JP
    var MAX_CONCURRENT_DOWNLOADS = 16  // 同时进行的最大下载数
    lateinit var Log: (String) -> Unit
    lateinit var Update: (Int, Int, String) -> Unit
    val client = OkHttpClient()


    fun start(
        url: String, saveDir: String, language: String, clearDir: Boolean, maxConcurrentDownloads: Int,
        log: (String) -> Unit, onUpdate: (Int, Int, String) -> Unit
    ): Unit =
        runBlocking {
            TARGET_URL = url
            DOWNLOAD_LANGUAGE = language
            CLEAR_DIR = clearDir
            SAVE_DIR = saveDir
            MAX_CONCURRENT_DOWNLOADS = maxConcurrentDownloads
            Log = log
            Update = onUpdate
            val saveDirFile = File(SAVE_DIR)
            // 如果设置了 CLEAR_DIR，则清空目录
            if (CLEAR_DIR) {
                Log("正在清空目录: ${saveDirFile.absolutePath}")
                if (saveDirFile.clearAll()) Log("目录已清空。")
                else Log("[警告] 清空目录失败: ${saveDirFile.absolutePath}")
            }
            // 检查并创建保存目录
            if (!saveDirFile.exists()) {
                if (saveDirFile.mkdirs()) {
                    Log("已创建保存目录: ${saveDirFile.absolutePath}")
                } else {
                    Log("[错误] 无法创建保存目录: ${saveDirFile.absolutePath}，请检查权限或路径。")
                    return@runBlocking // 无法创建目录则退出
                }
            } else {
                Log("找到保存目录: ${saveDirFile.absolutePath}")
            }

            Log("开始从 $TARGET_URL 获取 HTML 内容...")
            fetchHtml(TARGET_URL)?.let {
                Log("HTML 获取成功，将下载语言: $DOWNLOAD_LANGUAGE (最多 $MAX_CONCURRENT_DOWNLOADS 个并发)...")
                parseAndDownload(it)
                Log("\n所有下载任务已启动并等待完成...")
            } ?: Log("[错误] 未能从 $TARGET_URL 获取 HTML 内容。")
            Log("处理完成。")
        }

    /**
     * 根据指定的语言和 HTML 结构查找对应的 MP3 链接。
     * @param td 包含 rowspan 的那个 <td> 元素。
     * @param language 要查找的语言 ("CN", "JP", "EN")。
     * @return 找到的绝对 URL 字符串，如果找不到或不支持则返回 null。
     */
    fun findMp3Url(td: Element, language: String): String? {
        val parentRow = td.parent() ?: return null // 获取包含 td 的行 <tr>
        val rowspan = td.attr("rowspan").toIntOrNull() // 获取 rowspan 值

        return when (language.uppercase()) { // 统一转大写处理
            "CN" -> {
                // 中文总是在 rowspan 元素所在行的第二个 td (td:eq(1))
                parentRow.select("td:eq(1) a[href]").firstOrNull()?.attr("abs:href")
            }

            "JP" -> {
                // 日语总是在下一行的第一个 td (td:eq(0))，对 rowspan=2 和 3 都适用
                parentRow.nextElementSibling() // 获取下一个兄弟元素 <tr>
                    ?.select("td:eq(0) a[href]")?.firstOrNull()?.attr("abs:href")
            }

            "EN" -> {
                // 英语只在 rowspan=3 结构中存在，在日文行的下一行的第一个 td (td:eq(0))
                if (rowspan == 3) {
                    parentRow.nextElementSibling() // 日文行 <tr>
                        ?.nextElementSibling() // 英文行 <tr>
                        ?.select("td:eq(0) a[href]")?.firstOrNull()?.attr("abs:href")
                } else {
                    null // rowspan=2 没有英文
                }
            }

            else -> null // 不支持的语言
        }
    }


    /**
     * 解析 HTML 内容，根据 DOWNLOAD_LANGUAGE 常量并发下载指定语言的语音。
     * @param html 待解析的 HTML 字符串
     */
    suspend fun parseAndDownload(html: String) = coroutineScope {
        val doc = Jsoup.parse(html, TARGET_URL) // 提供 baseUri
        val downloadJobs = mutableListOf<Job>()
        val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
        val progressCounter = AtomicInteger(0)
        val nameCounters = ConcurrentHashMap<String, AtomicInteger>()
        val counterMutexes = ConcurrentHashMap<String, Mutex>()

        // 1. 查找所有可能的条目起始 td
        val entries = doc.select("td[rowspan='3'], td[rowspan='2']")

        // 2. 预处理：根据选定语言过滤有效任务
        val validTasks = entries.mapNotNull { td ->
            val rawName = td.text().trim()
            if (rawName.isEmpty()) return@mapNotNull null

            val baseName = sanitizeFileName(rawName)
            // 查找指定语言的链接
            val mp3Url = findMp3Url(td, DOWNLOAD_LANGUAGE)

            // 如果找到了指定语言的有效链接，则任务有效
            if (mp3Url != null && mp3Url.isNotBlank() && mp3Url.endsWith(".mp3", ignoreCase = true)) {
                Triple(baseName, mp3Url, rawName) // (基础名, 找到的MP3链接, 原始名)
            } else {
                // 如果找不到选定语言的链接，则此条目对于当前任务无效
                // Log("  [跳过] 对于 '$rawName' 未找到有效的 $DOWNLOAD_LANGUAGE 语音链接。")
                null
            }
        }

        val totalValidTasks = validTasks.size
        if (totalValidTasks == 0) {
            Log("未发现任何有效的 $DOWNLOAD_LANGUAGE 语音条目可供下载。")
            return@coroutineScope
        }
        Log("发现 $totalValidTasks 个有效的 $DOWNLOAD_LANGUAGE 语音条目，开始下载...")

        // 3. 为每个有效任务启动下载协程
        validTasks.forEach { (baseName, mp3Url, rawName) ->
            val job = launch(Dispatchers.IO) {
                var targetFile: File? = null
                try {
                    // --- 文件名包含语言后缀 ---
                    val baseNameWithLang = "${baseName}_$DOWNLOAD_LANGUAGE" // 例如 "选择角色_CN"

                    val nameMutex = counterMutexes.computeIfAbsent(baseNameWithLang) { Mutex() } // 使用带语言的 key
                    val counter = nameCounters.computeIfAbsent(baseNameWithLang) { AtomicInteger(0) } // 使用带语言的 key

                    val uniqueFileName = nameMutex.withLock {
                        val index = counter.getAndIncrement()
                        if (index == 0) {
                            "$baseNameWithLang.mp3"
                        } else {
                            "$baseNameWithLang($index).mp3" // 例如 "选择角色_CN(1).mp3"
                        }
                    }
                    targetFile = File(SAVE_DIR, uniqueFileName)

                    // --- 执行下载 ---
                    downloadSemaphore.acquire()
                    try {
                        downloadFile(mp3Url, targetFile)
                        val completedCount = progressCounter.incrementAndGet()
                        Update(completedCount, totalValidTasks, targetFile.name)
                    } finally {
                        downloadSemaphore.release()
                    }

                } catch (e: CancellationException) {
                    Log("\n[取消] '$rawName' ($DOWNLOAD_LANGUAGE) (${targetFile?.name ?: "未知"}) 的下载任务被取消。")
                    targetFile?.takeIf { it.exists() }?.delete()
                    throw e
                } catch (e: Exception) {
                    Log("\n[错误] 处理 '$rawName' ($DOWNLOAD_LANGUAGE) (${targetFile?.name ?: "未知文件"}) 时发生异常: ${e.message}")
                    targetFile?.takeIf { it.exists() }?.delete()
                }
            }
            downloadJobs.add(job)
        }

        // 4. 等待所有下载协程完成
        downloadJobs.joinAll()
    }

    /**
     * 从指定的 URL 下载文件并保存到目标文件。
     * 现在是一个 suspend 函数，以便在协程中调用。
     * @param url 文件的网络地址
     * @param targetFile 要保存到的本地文件对象
     */
    fun downloadFile(url: String, targetFile: File) {
        // Log("  准备下载 (挂起): ${targetFile.name} [源地址: $url]")

        val request = Request.Builder()
            .url(url)
            .header("Referer", TARGET_URL)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36"
            )
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 异步环境下的错误日志可能交错，添加文件名帮助识别
                    Log("\n[错误] 下载 ${targetFile.name} 失败。服务器响应码: ${response.code}")
                    return // 下载失败
                }

                response.body.use { body ->
                    targetFile.outputStream().buffered().use { output ->
                        body.byteStream().copyTo(output)
                        // Log("  下载成功 (挂起完成): ${targetFile.absolutePath}")
                    }
                }

            }
        } catch (e: IOException) {
            Log("\n[错误] 下载 ${targetFile.name} 时发生 IO 异常: ${e.message}")
            if (targetFile.exists()) targetFile.delete()
        } catch (e: CancellationException) {
            // 协程被取消时的处理
            Log("\n[取消] 下载 ${targetFile.name} 被取消。")
            if (targetFile.exists()) targetFile.delete() // 删除部分下载的文件
            throw e // 重新抛出取消异常很重要
        } catch (e: Exception) {
            Log("\n[错误] 下载 ${targetFile.name} 时发生未知错误: ${e.message}")
            if (targetFile.exists()) targetFile.delete()
        }
    }

    /**
     * 获取指定 URL 的 HTML 网页内容。
     * @param url 网页地址
     * @return 获取到的 HTML 字符串，如果失败则返回 null
     */
    suspend fun fetchHtml(url: String): String? = withContext(Dispatchers.IO) {
        // 将网络请求放在 IO 线程池执行
        val request = Request(
            url = url.toHttpUrl(),
            headers = headersOf(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36"
            )
        )

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log("[错误] 获取 HTML 失败。URL: $url, 状态码: ${response.code}")
                    null
                } else {
                    response.body.string() // 读取响应体
                }
            }
        } catch (e: IOException) {
            Log("[错误] 获取 HTML 时发生 IO 异常: ${e.message}")
            null
        } catch (e: Exception) {
            Log("[错误] 获取 HTML 时发生未知错误: ${e.message}")
            null
        }
    }

    /**
     * 清理字符串，使其成为合法的文件名 (主要针对 Windows 系统)。
     * 替换非法字符为下划线，合并多余空格。
     * @param name 原始名称字符串
     * @return 清理后的、适合用作文件名的字符串
     */
    fun sanitizeFileName(name: String): String {
        // 替换 Windows 文件名中的非法字符: \ / : * ? " < > | 为下划线 "_"
        val sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            // 将一个或多个连续的空白字符 (空格, tab, 换行等) 替换为单个空格
            .replace("\\s+".toRegex(), " ")
            // 去除首尾的空格
            .trim()
        // 如果清理后字符串为空，则返回一个默认名称，防止创建无名文件
        return sanitized.ifEmpty { "unnamed" }
    }

}