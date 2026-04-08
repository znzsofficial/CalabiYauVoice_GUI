package data

/**
 * 通用 API 结果封装。
 * 所有 API 文件共用，避免重复定义。
 */
sealed interface ApiResult<out T> {
    /** @param isOffline 数据是否来自离线缓存 */
    data class Success<T>(val value: T, val isOffline: Boolean = false) : ApiResult<T>
    data class Error(val message: String) : ApiResult<Nothing>
}
