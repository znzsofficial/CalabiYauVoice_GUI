package data

/**
 * 通用 API 结果封装。
 * 所有 API 文件共用，避免重复定义。
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Error(val message: String) : ApiResult<Nothing>
}
