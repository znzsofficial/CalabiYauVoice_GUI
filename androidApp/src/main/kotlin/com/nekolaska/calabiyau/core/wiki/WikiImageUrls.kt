package com.nekolaska.calabiyau.core.wiki

object WikiImageUrls {

    /**
     * Converts MediaWiki thumbnail URLs to original file URLs.
     * Example: /images/wiki/thumb/a/ab/file.png/70px-file.png -> /images/wiki/a/ab/file.png
     */
    fun originalFromThumbnail(url: String?): String? {
        val absolute = normalize(url) ?: return null
        return if ("/thumb/" in absolute) {
            absolute.replace("/thumb/", "/").substringBeforeLast("/")
        } else {
            absolute
        }
    }

    fun thumbnail(url: String?, widthPx: Int): String? {
        val original = originalFromThumbnail(url) ?: return null
        if (widthPx <= 0 || "/thumb/" in original) return original
        val fileName = original.substringAfterLast('/')
        if (fileName.isBlank()) return original
        val imagesAt = original.indexOf("/images/")
        if (imagesAt < 0) return original
        val afterImages = original.substring(imagesAt + "/images/".length)
        val wikiEnd = afterImages.indexOf('/')
        if (wikiEnd < 0) return original
        val prefix = original.substring(0, imagesAt + "/images/".length + wikiEnd)
        val hashPath = afterImages.substring(wikiEnd + 1)
        return "$prefix/thumb/$hashPath/${widthPx}px-$fileName"
    }

    fun normalize(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val absolute = if (url.startsWith("//")) "https:$url" else url
        return absolute.substringBefore('?').substringBefore('#')
    }
}
