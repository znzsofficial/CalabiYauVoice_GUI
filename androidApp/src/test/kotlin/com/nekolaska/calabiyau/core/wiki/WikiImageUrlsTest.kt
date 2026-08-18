package com.nekolaska.calabiyau.core.wiki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WikiImageUrlsTest {

    @Test
    fun originalFromThumbnailStripsThumbPath() {
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/a/ab/file.png",
            WikiImageUrls.originalFromThumbnail(
                "https://patchwiki.biligame.com/images/klbq/thumb/a/ab/file.png/70px-file.png"
            )
        )
    }

    @Test
    fun originalFromThumbnailKeepsNonThumbAndNormalizesProtocol() {
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/a/ab/file.png",
            WikiImageUrls.originalFromThumbnail("//patchwiki.biligame.com/images/klbq/a/ab/file.png?x=1#hash")
        )
        assertNull(WikiImageUrls.originalFromThumbnail(" "))
        assertNull(WikiImageUrls.originalFromThumbnail(null))
    }

    @Test
    fun thumbnailInsertsWidthIntoImagesPath() {
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/thumb/a/ab/file.png/360px-file.png",
            WikiImageUrls.thumbnail("https://patchwiki.biligame.com/images/klbq/a/ab/file.png", 360)
        )
        assertEquals(
            "https://patchwiki.biligame.com/images/klbq/a/ab/file.png",
            WikiImageUrls.thumbnail("https://patchwiki.biligame.com/images/klbq/a/ab/file.png", 0)
        )
    }
}
