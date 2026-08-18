package com.nekolaska.calabiyau.core.wiki

import android.content.Context
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.BlackholeDecoder
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

private const val PREFETCH_CONCURRENCY = 2

private val inflightPortraitUrls = ConcurrentHashMap.newKeySet<String>()

fun wikiPortraitUrl(url: String?): String? = WikiImageUrls.originalFromThumbnail(url)

fun wikiPortraitRequest(
    context: Context,
    url: String?,
    crossfade: Boolean = true
): ImageRequest? {
    val data = wikiPortraitUrl(url) ?: return null
    return ImageRequest.Builder(context)
        .data(data)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .crossfade(crossfade)
        .build()
}

@OptIn(ExperimentalCoilApi::class)
suspend fun ImageLoader.prefetchWikiPortraits(
    context: Context,
    urls: Collection<String?>,
    concurrency: Int = PREFETCH_CONCURRENCY
) {
    val pending = withContext(Dispatchers.IO) {
        urls.mapNotNull(::wikiPortraitUrl)
            .distinct()
            .filterNot { it in inflightPortraitUrls }
            .filterNot(::isWikiPortraitCached)
    }
    if (pending.isEmpty()) return

    val semaphore = Semaphore(concurrency.coerceAtLeast(1))
    for (url in pending) {
        currentCoroutineContext().ensureActive()
        if (!inflightPortraitUrls.add(url)) continue
        try {
            semaphore.withPermit {
                val result = execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .decoderFactory(BlackholeDecoder.Factory())
                        .build()
                )
                if (result !is SuccessResult) {
                    inflightPortraitUrls.remove(url)
                    if (result is ErrorResult && result.throwable is CancellationException) {
                        throw result.throwable
                    }
                }
            }
        } catch (error: CancellationException) {
            inflightPortraitUrls.remove(url)
            throw error
        } catch (_: Exception) {
            inflightPortraitUrls.remove(url)
        }
    }
}

private fun ImageLoader.isWikiPortraitCached(url: String): Boolean {
    if (memoryCache?.get(MemoryCache.Key(url)) != null) return true
    val snapshot = diskCache?.openSnapshot(url) ?: return false
    snapshot.close()
    return true
}
