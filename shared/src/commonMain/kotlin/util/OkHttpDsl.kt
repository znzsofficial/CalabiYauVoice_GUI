package util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellableContinuation
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.File
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 发起简单 GET 请求，返回 [Response]。
 * 调用方需自行关闭 Response（推荐 `.use {}`）。
 */
fun OkHttpClient.executeGet(url: String): Response {
    return newCall(Request.Builder().url(url).build()).execute()
}

/** Executes a GET request and cancels the underlying OkHttp call with the coroutine. */
suspend fun OkHttpClient.awaitGet(url: String): Response = suspendCancellableCoroutine { continuation ->
    awaitCall(Request.Builder().url(url).build(), continuation)
}

suspend fun OkHttpClient.awaitRequest(
    url: String,
    block: Request.Builder.() -> Unit
): Response = suspendCancellableCoroutine { continuation ->
    awaitCall(Request.Builder().url(url).apply(block).build(), continuation)
}

/** Streams a response body into [file] while keeping cancellation attached to the OkHttp call. */
suspend fun OkHttpClient.awaitGetToFile(url: String, file: File): Boolean =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                file.delete()
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) {
                            if (continuation.isActive) continuation.resume(false)
                            return
                        }
                        val contentType = it.header("Content-Type").orEmpty().lowercase()
                        if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                            throw IOException("Unexpected HTML response for $url")
                        }
                        it.body.byteStream().use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    if (looksLikeHtmlFile(file)) {
                        throw IOException("Downloaded HTML instead of a media file for $url")
                    }
                    if (continuation.isActive) continuation.resume(true)
                    else file.delete()
                } catch (error: IOException) {
                    file.delete()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

private fun OkHttpClient.awaitCall(
    request: Request,
    continuation: CancellableContinuation<Response>
) {
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) { _, value, _ -> value.close() }
            else response.close()
        }
    })
}

/**
 * 发起带自定义 Header 的 GET 请求。
 */
fun OkHttpClient.executeGet(
    url: String,
    headers: Map<String, String>
): Response {
    val request = Request.Builder().url(url).apply {
        headers.forEach { (k, v) -> header(k, v) }
    }.build()
    return newCall(request).execute()
}

/**
 * Builder DSL：在 [Request.Builder] 上自由配置后执行。
 *
 * ```kotlin
 * client.executeRequest(url) {
 *     post(formBody)
 *     header("Cookie", cookies)
 * }
 * ```
 */
inline fun OkHttpClient.executeRequest(
    url: String,
    block: Request.Builder.() -> Unit
): Response {
    return newCall(Request.Builder().url(url).apply(block).build()).execute()
}

/**
 * 快捷构建 [FormBody]。
 *
 * ```kotlin
 * formBodyOf("action" to "edit", "title" to page)
 * ```
 */
fun formBodyOf(vararg pairs: Pair<String, String>): FormBody {
    return FormBody.Builder().apply {
        pairs.forEach { (k, v) -> add(k, v) }
    }.build()
}

/**
 * 构造带查询参数的 URL，所有参数经过 URL 编码。
 *
 * ```kotlin
 * buildWikiUrl("https://example/api.php", "action" to "query", "format" to "json")
 * // → "https://example/api.php?action=query&format=json"
 * ```
 */
fun buildWikiUrl(base: String, vararg params: Pair<String, String>): String {
    if (params.isEmpty()) return base
    val query = params.joinToString("&") { (k, v) ->
        "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
    }
    return "$base?$query"
}

/**
 * Wiki 页面路径编码：空格用 `%20` 而非 `+`。
 *
 * ```kotlin
 * "角色名".wikiPathEncode()  // → "%E8%A7%92%E8%89%B2%E5%90%8D"
 * ```
 */
fun String.wikiPathEncode(): String =
    URLEncoder.encode(this, "UTF-8").replace("+", "%20")

/**
 * 构造 MediaWiki parse API URL。
 *
 * ```kotlin
 * buildParseUrl(API, "角色名", "wikitext|text")
 * // → "https://...api.php?action=parse&page=角色名&prop=wikitext|text&format=json"
 * ```
 */
fun buildParseUrl(api: String, page: String, prop: String, vararg extra: Pair<String, String>): String =
    buildWikiUrl(api, "action" to "parse", "page" to page, "prop" to prop, "format" to "json", *extra)

/**
 * 发起 GET 请求并返回响应体字符串，失败返回 null。
 */
fun looksLikeHtmlFile(file: File): Boolean {
    if (!file.isFile || file.length() == 0L) return false
    val prefix = file.inputStream().use { input ->
        val buffer = ByteArray(64)
        val read = input.read(buffer)
        if (read <= 0) return false
        buffer.copyOf(read).toString(Charsets.UTF_8)
    }.trimStart().lowercase()
    return prefix.startsWith("<!doctype html") || prefix.startsWith("<html")
}

fun OkHttpClient.executeGetString(url: String): String? =
    executeGet(url).use { if (it.isSuccessful) it.body.string() else null }

/**
 * 将响应体写入文件。
 */
fun Response.bodyToFile(file: File) {
    body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
}

/**
 * 统一的 API 调用异常捕获。
 *
 * ```kotlin
 * val result = runApiCatching { fetchSomething() }
 * // result: ApiResult<T>
 * ```
 */
inline fun <T> runApiCatching(block: () -> T): data.ApiResult<T> =
    try { data.ApiResult.Success(block()) } catch (e: Exception) { data.ApiResult.Error(e.message ?: "未知错误") }
