package com.nekolaska.calabiyau.feature.wiki.hub

import kotlin.test.*

class WikiNavigationPolicyTest {
    @Test fun explicitBlockWinsOverAllowedBilibiliSuffixForEveryEntryPoint() {
        for (url in listOf("https://d.bilibili.com/", "https://D.BILIBILI.COM/app", "http://d.bilibili.com/",
            "https://d.bilibili.com./", "https://sub.d.bilibili.com/")) {
            assertEquals(WikiNavigation.Blocked, WikiNavigationPolicy.classify(url))
        }
        assertTrue(WikiNavigationPolicy.classify("https://passport.bilibili.com/login") is WikiNavigation.Internal)
    }

    @Test fun onlyHttpsAndRealAllowedHostsCanLoadAsMainDocuments() {
        for (url in listOf("https://wiki.biligame.com/klbq/首页", "https://wiki.nekolaska.vip/")) {
            assertTrue(WikiNavigationPolicy.classify(url) is WikiNavigation.Internal)
        }
        for (url in listOf("https://wiki.biligame.com.attacker.test/", "https://wiki.biligame.com@attacker.test/",
            "http://wiki.biligame.com/", "https://attacker.test/")) {
            assertTrue(WikiNavigationPolicy.classify(url) is WikiNavigation.External)
        }
    }

    @Test fun opaqueIntentUrisAreHandledWithoutAnyHierarchicalQueryAssumption() {
        val navigation = WikiNavigationPolicy.classify("intent:scan/#Intent;scheme=zxing;end")
        assertTrue(navigation is WikiNavigation.External); assertTrue(navigation.intent)
        assertTrue(WikiNavigationPolicy.classify("intent:#Intent;action=android.intent.action.VIEW;end") is WikiNavigation.External)
    }

    @Test fun intentFallbacksAreDecodedAndPassThroughTheSameAllowAndBlockRules() {
        val fallback = "intent:scan/#Intent;scheme=zxing;S.browser_fallback_url=https%3A%2F%2Fwiki.biligame.com%2Fklbq%2F;end"
        assertEquals(WikiNavigation.Internal("https://wiki.biligame.com/klbq/"), WikiNavigationPolicy.classify(fallback))
        assertFalse(WikiNavigationPolicy.mayLoadDocument(fallback))
        assertEquals("https://wiki.biligame.com/klbq/", WikiNavigationPolicy.documentUrl(fallback))
        assertTrue(WikiNavigationPolicy.mayLoadDocument("https://wiki.biligame.com/klbq/"))
        assertEquals(WikiNavigation.Blocked, WikiNavigationPolicy.classify(
            "intent://scan/#Intent;S.browser_fallback_url=https%3A%2F%2Fd.bilibili.com%2F;end"))
        assertEquals(WikiNavigation.Blocked, WikiNavigationPolicy.classify(
            "intent:scan/#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"))
        assertEquals(WikiNavigation.Blocked, WikiNavigationPolicy.classify(
            "intent:scan/#Intent;S.browser_fallback_url=%ZZ;end"))
        assertEquals(WikiNavigation.Blocked, WikiNavigationPolicy.classify(
            "intent://scan/?browser_fallback_url=%ZZ#Intent;scheme=zxing;end"))
    }

    @Test fun localSchemesMalformedEscapesAndControlCharactersFailClosed() {
        for (url in listOf("javascript:alert(1)", "file:///data/data/app/private", "content://provider/file",
            "data:text/html,test", "about:blank", "https://wiki.biligame.com/%XX", "https://wiki.biligame.com/\nX", "")) {
            assertEquals(WikiNavigation.Blocked, WikiNavigationPolicy.classify(url))
        }
    }
}
