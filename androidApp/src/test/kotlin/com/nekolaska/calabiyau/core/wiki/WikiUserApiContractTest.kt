package com.nekolaska.calabiyau.core.wiki

import data.ApiResult
import kotlin.test.*

class WikiUserApiContractTest {
    private fun parse(body: String): ApiResult<WikiUserApi.UserInfo> =
        WikiUserApi.decodeWikiQueryPayload(body, "invalid user", WikiUserApi::parseUserInfoJson)

    @Test fun mediaWikiErrorsAndMissingIdentityNeverBecomeSuccessfulNullUsers() {
        for (body in listOf("""{"error":{"code":"maxlag","info":"busy"}}""", "{}",
            """{"query":{}}""", "not json", "<html>blocked</html>")) {
            assertTrue(parse(body) is ApiResult.Error)
        }
    }

    @Test fun validUsersAndActualAnonymousUsersRemainDistinctSuccessfulResults() {
        val user = parse("""{"query":{"userinfo":{"id":12,"name":"A"}}}""") as ApiResult.Success
        val anon = parse("""{"query":{"userinfo":{"id":0,"name":"127.0.0.1","anon":""}}}""") as ApiResult.Success
        assertTrue(user.value.isLoggedIn); assertEquals(12L, user.value.id)
        assertFalse(anon.value.isLoggedIn)
    }

    @Test fun errorsAreRejectedBeforeContributionsOrWatchlistTransformsRun() {
        var ran = false
        val result = WikiUserApi.decodeWikiQueryPayload("""{"error":{"code":"badtoken"}}""", "failed") {
            ran = true; emptyList<String>()
        }
        assertTrue(result is ApiResult.Error); assertFalse(ran)
    }
}
