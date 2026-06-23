package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 在 IO 调度器上执行 API 调用，统一处理异常到 [ApiResult.Error]。
 *
 * 用法：
 * ```kotlin
 * suspend fun fetchSomething(): ApiResult<Data> = ioApiCall("获取数据失败") {
 *     val result = RemoteSource.fetch() ?: return@ioApiCall ApiResult.Error("无数据", kind = ErrorKind.NETWORK)
 *     ApiResult.Success(parseData(result))
 * }
 * ```
 */
suspend fun <T> ioApiCall(
    errorMessage: String,
    block: suspend () -> ApiResult<T>
): ApiResult<T> = withContext(Dispatchers.IO) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiResult.Error("$errorMessage: ${e.message}", kind = e.toErrorKind())
    }
}
