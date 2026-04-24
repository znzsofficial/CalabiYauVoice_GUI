package com.nekolaska.calabiyau.feature.wiki.gallery.api

import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import com.nekolaska.calabiyau.feature.wiki.gallery.model.GalleryImage
import com.nekolaska.calabiyau.feature.wiki.gallery.model.GallerySection
import com.nekolaska.calabiyau.feature.wiki.gallery.parser.GalleryParsers
import com.nekolaska.calabiyau.feature.wiki.gallery.source.GalleryRemoteSource
import data.ApiResult
import data.ErrorKind
import data.toErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 画廊 API（Android）。
 *
 * 解析 Wiki 页面的渲染 HTML，提取分 section 的图片列表，
 * 然后批量获取图片 URL。支持壁纸、表情包、四格漫画等页面。
 */
object GalleryApi {

    init {
        MemoryCacheRegistry.register("GalleryApi", ::clearMemoryCache)
    }

    private val cache = mutableMapOf<String, List<GallerySection>>()

    fun clearMemoryCache() { cache.clear() }

    /**
     * 获取画廊数据（带缓存）。
     * @param pageName Wiki 页面名（如 "壁纸"、"表情包"、"官方四格漫画"）
     */
    suspend fun fetchGallery(
        pageName: String,
        forceRefresh: Boolean = false
    ): ApiResult<List<GallerySection>> {
        if (!forceRefresh) {
            cache[pageName]?.let { return ApiResult.Success(it) }
        }
        return fetchFromNetwork(pageName, forceRefresh).also {
            if (it is ApiResult.Success) cache[pageName] = it.value
        }
    }

    private suspend fun fetchFromNetwork(
        pageName: String,
        forceRefresh: Boolean
    ): ApiResult<List<GallerySection>> =
        withContext(Dispatchers.IO) {
            try {
                val result = GalleryRemoteSource.fetchPageHtml(pageName, forceRefresh)
                    ?: return@withContext ApiResult.Error(
                        "获取页面失败，且无离线缓存",
                        kind = ErrorKind.NETWORK
                    )
                val html = result.json

                val rawSections = GalleryParsers.parseHtml(pageName, html)
                if (rawSections.isEmpty()) {
                    return@withContext ApiResult.Error(
                        "未找到图片内容",
                        kind = ErrorKind.NOT_FOUND
                    )
                }

                val allFileNames = rawSections.flatMap { s -> s.second.map { it.first } }.distinct()
                val urlMap = GalleryRemoteSource.fetchImageUrls(allFileNames)

                val sections = rawSections.mapNotNull { (title, files) ->
                    val images = files.mapNotNull { (fileName, caption) ->
                        val imageUrl = urlMap[fileName] ?: return@mapNotNull null
                        GalleryImage(fileName, caption, imageUrl)
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
            } catch (e: Exception) {
                ApiResult.Error("网络异常: ${e.message}", kind = e.toErrorKind())
            }
        }
}
