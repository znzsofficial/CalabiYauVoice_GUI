package com.nekolaska.calabiyau.core.wiki

object WikiImageUrls {

    /**
     * Converts MediaWiki thumbnail URLs to original file URLs.
     * Example: /images/wiki/thumb/a/ab/file.png/70px-file.png -> /images/wiki/a/ab/file.png
     */
    fun originalFromThumbnail(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val absolute = if (url.startsWith("//")) "https:$url" else url
        return if ("/thumb/" in absolute) {
            absolute.replace("/thumb/", "/").substringBeforeLast("/")
        } else {
            absolute
        }
    }
}
