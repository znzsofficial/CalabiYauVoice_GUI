package com.nekolaska.calabiyau.feature.wiki.gallery.api

import com.nekolaska.calabiyau.core.cache.KeyedCachedWikiApi
import com.nekolaska.calabiyau.feature.wiki.gallery.model.GalleryImage
import com.nekolaska.calabiyau.feature.wiki.gallery.model.GallerySection
import com.nekolaska.calabiyau.feature.wiki.gallery.parser.GalleryParsers
import com.nekolaska.calabiyau.feature.wiki.gallery.source.GalleryRemoteSource
import com.nekolaska.calabiyau.feature.wiki.gallery.source.GalleryPageSourceResult
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 画廊 API（Android）。
 *
 * 解析 Wiki 页面的渲染 HTML，提取分 section 的图片列表，
 * 然后批量获取图片 URL。支持壁纸、表情包、四格漫画等页面。
 */
object GalleryApi : KeyedCachedWikiApi<String, List<GallerySection>>("GalleryApi") {

    /**
     * 获取画廊数据（带缓存）。
     * @param pageName Wiki 页面名（如 "壁纸"、"表情包"、"官方四格漫画"）
     */
    suspend fun fetchGallery(
        pageName: String,
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<List<GallerySection>> = fetch(pageName, forceRefresh, cacheOnly, allowMemoryCache)

    override suspend fun fetchFromCache(key: String): ApiResult<List<GallerySection>> = fetchFromSource(
        pageName = key,
        forceRefresh = false,
        cacheOnly = true,
        loadSource = { GalleryRemoteSource.loadCachedPageHtml(key) },
        networkErrorMessage = "无离线缓存"
    )

    override suspend fun fetchFromNetwork(
        key: String,
        forceRefresh: Boolean
    ): ApiResult<List<GallerySection>> = fetchFromSource(
        pageName = key,
        forceRefresh = forceRefresh,
        cacheOnly = false,
        loadSource = { GalleryRemoteSource.fetchPageHtml(key, forceRefresh) },
        networkErrorMessage = "获取页面失败，且无离线缓存"
    )

    private suspend fun fetchFromSource(
        pageName: String,
        forceRefresh: Boolean,
        cacheOnly: Boolean,
        loadSource: suspend () -> GalleryPageSourceResult?,
        networkErrorMessage: String
    ): ApiResult<List<GallerySection>> =
        withContext(Dispatchers.IO) {
            try {
                val result = loadSource()
                    ?: return@withContext ApiResult.Error(
                        networkErrorMessage,
                        kind = ErrorKind.NETWORK
                    )
                val html = result.html

                val rawSections = GalleryParsers.parseHtml(pageName, html)
                if (rawSections.isEmpty()) {
                    return@withContext ApiResult.Error(
                        "未找到图片内容",
                        kind = ErrorKind.NOT_FOUND
                    )
                }

                val allFileNames = rawSections
                    .flatMap { s -> s.second.filter { it.directImageUrl.isNullOrBlank() }.map { it.fileName } }
                    .distinct()
                val urlMap = GalleryRemoteSource.fetchImageUrls(allFileNames, forceRefresh, cacheOnly)

                val sections = rawSections.mapNotNull { (title, files) ->
                    val images = files.mapNotNull { image ->
                        val imageUrl = image.directImageUrl ?: urlMap[image.fileName] ?: return@mapNotNull null
                        GalleryImage(
                            fileName = image.fileName,
                            caption = image.caption,
                            imageUrl = imageUrl,
                            description = image.description,
                            obtainMethod = image.obtainMethod
                        )
                    }
                    if (images.isEmpty()) null else GallerySection(title, images)
                }

                if (sections.isEmpty()) {
                    ApiResult.Error("未能解析到图片 URL", kind = ErrorKind.NOT_FOUND)
                } else {
                    ApiResult.Success(
                        sections,
                        isOffline = result.isFromCache,
                        cacheAgeMs = result.ageMs
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Error("网络异常: ${e.message}", kind = e.toErrorKind())
            }
        }
}
