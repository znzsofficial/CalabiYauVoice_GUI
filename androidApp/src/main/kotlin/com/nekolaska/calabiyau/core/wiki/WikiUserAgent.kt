package com.nekolaska.calabiyau.core.wiki

import kotlin.random.Random

data class WikiBrowserProfile(
    val userAgent: String,
    val accept: String = WikiUserAgent.DEFAULT_ACCEPT,
    val acceptLanguage: String = WikiUserAgent.DEFAULT_ACCEPT_LANGUAGE,
    val secChUa: String? = null,
    val secChUaMobile: String,
    val secChUaPlatform: String
)

object WikiUserAgent {
    const val DEFAULT_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
    const val DEFAULT_ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8"

    private val mobileDevices = listOf(
        "Android 16; Pixel 10 Pro",
        "Android 15; Pixel 9 Pro",
        "Android 15; Pixel 8 Pro",
        "Android 15; SM-S9380",
        "Android 14; SM-S9280",
        "Android 15; 25010PN30C",
        "Android 14; 24129PN74C",
        "Android 15; PKB110",
        "Android 14; V2408A",
        "Android 14; ALN-AL00",
        "Android 13; PGEM10",
        "Android 13; V2303A"
    )

    private val desktopPlatforms = listOf(
        "Windows NT 10.0; Win64; x64" to "Windows",
        "Macintosh; Intel Mac OS X 15_2" to "macOS",
        "Macintosh; Intel Mac OS X 14_7_5" to "macOS",
        "X11; Linux x86_64" to "Linux"
    )

    private val chromeVersions = listOf(
        "143.0.7499.40",
        "142.0.7444.203",
        "141.0.7390.122",
        "140.0.7339.207"
    )

    private val edgeVersions = listOf(
        "143.0.3650.20",
        "142.0.3595.94"
    )

    fun randomProfile(desktopMode: Boolean): WikiBrowserProfile {
        val random = Random(System.nanoTime())
        val chromeVersion = chromeVersions.random(random)
        return if (desktopMode) {
            desktopProfile(random, chromeVersion)
        } else {
            mobileProfile(random, chromeVersion)
        }
    }

    private fun mobileProfile(random: Random, chromeVersion: String): WikiBrowserProfile {
        val device = mobileDevices.random(random)
        val majorVersion = chromeVersion.substringBefore('.')
        return WikiBrowserProfile(
            userAgent = "Mozilla/5.0 (Linux; $device) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/$chromeVersion Mobile Safari/537.36",
            secChUa = chromeSecChUa(majorVersion),
            secChUaMobile = "?1",
            secChUaPlatform = "\"Android\""
        )
    }

    private fun desktopProfile(random: Random, chromeVersion: String): WikiBrowserProfile {
        val (platform, secPlatform) = desktopPlatforms.random(random)
        val majorVersion = chromeVersion.substringBefore('.')
        val chromeUa = "Mozilla/5.0 ($platform) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$chromeVersion Safari/537.36"
        val useEdge = platform.startsWith("Windows") && random.nextInt(4) == 0
        return if (useEdge) {
            val edgeVersion = edgeVersions.random(random)
            val edgeMajorVersion = edgeVersion.substringBefore('.')
            WikiBrowserProfile(
                userAgent = "$chromeUa Edg/$edgeVersion",
                secChUa = edgeSecChUa(edgeMajorVersion, majorVersion),
                secChUaMobile = "?0",
                secChUaPlatform = "\"Windows\""
            )
        } else {
            WikiBrowserProfile(
                userAgent = chromeUa,
                secChUa = chromeSecChUa(majorVersion),
                secChUaMobile = "?0",
                secChUaPlatform = "\"$secPlatform\""
            )
        }
    }

    private fun chromeSecChUa(majorVersion: String): String {
        return "\"Google Chrome\";v=\"$majorVersion\", \"Chromium\";v=\"$majorVersion\", \"Not_A Brand\";v=\"24\""
    }

    private fun edgeSecChUa(edgeMajorVersion: String, chromeMajorVersion: String): String {
        return "\"Microsoft Edge\";v=\"$edgeMajorVersion\", \"Chromium\";v=\"$chromeMajorVersion\", \"Not_A Brand\";v=\"24\""
    }
}
