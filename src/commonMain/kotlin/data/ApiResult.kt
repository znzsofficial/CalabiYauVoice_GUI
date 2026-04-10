package data

/**
 * 错误类型分类，便于 UI 精准展示错误状态。
 * 优先使用该字段判断错误类型，避免对错误消息做字符串匹配。
 */
enum class ErrorKind {
    /** 网络不可达、DNS/连接失败 */
    NETWORK,
    /** 读/连/写超时 */
    TIMEOUT,
    /** 经过 WikiEngine 重试后仍返回 403/429/503（CDN/WAF 拦截） */
    CDN_BLOCKED,
    /** JSON/wikitext 解析失败 */
    PARSE,
    /** 上游返回空数据或指定资源不存在 */
    NOT_FOUND,
    /** 未分类错误 */
    UNKNOWN
}

/**
 * 根据异常类型推断 [ErrorKind]。
 * 调用处：API 层的 `catch (e: Exception)` 块。
 */
fun Throwable.toErrorKind(): ErrorKind = when (this) {
    is java.net.UnknownHostException,
    is java.net.ConnectException,
    is java.net.NoRouteToHostException -> ErrorKind.NETWORK
    is java.net.SocketTimeoutException,
    is java.io.InterruptedIOException -> ErrorKind.TIMEOUT
    else -> {
        val msg = message.orEmpty()
        when {
            "403" in msg || "429" in msg || "503" in msg || "CDN" in msg -> ErrorKind.CDN_BLOCKED
            else -> ErrorKind.UNKNOWN
        }
    }
}

/**
 * 通用 API 结果封装。
 * 所有 API 文件共用，避免重复定义。
 */
sealed interface ApiResult<out T> {
    /**
     * @param isOffline 数据是否来自离线缓存
     * @param cacheAgeMs 缓存年龄（毫秒），用于 UI 展示"离线数据 · N 小时前"
     */
    data class Success<T>(
        val value: T,
        val isOffline: Boolean = false,
        val cacheAgeMs: Long = 0L
    ) : ApiResult<T>

    /**
     * @param kind 错误分类，UI 据此选择图标/提示语
     */
    data class Error(
        val message: String,
        val kind: ErrorKind = ErrorKind.UNKNOWN
    ) : ApiResult<Nothing>
}
