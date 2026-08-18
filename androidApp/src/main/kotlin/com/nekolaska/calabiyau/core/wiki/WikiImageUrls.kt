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
        val insertAt = original.indexOf("/images/")
        if (insertAt < 0) return original
        val prefix = original.substring(0, insertAt + "/images/".length)
        val hashPath = original.removePrefix(prefix)
        return "${prefix}thumb/$hashPath/${widthPx}px-$fileName"
    }

    fun normalize(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val absolute = if (url.startsWith("//")) "https:$url" else url
        return absolute.substringBefore('?').substringBefore('#')
    }
}
