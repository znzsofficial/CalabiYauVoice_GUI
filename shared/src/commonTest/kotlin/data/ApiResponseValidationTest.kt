package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiResponseValidationTest {
    @Test
    fun htmlFallbackDoesNotBecomeJsonParserError() {
        assertEquals("回复服务暂不可用：服务器返回了网页，请稍后刷新重试。",
            nonJsonApiMessage("  <!doctype html><html lang=\"zh-CN\">", replies = true))
        assertNull(nonJsonApiMessage(" {\"error\":\"讨论不存在\"}"))
        assertEquals("服务器返回的数据格式异常，请稍后重试。", nonJsonApiMessage(""))
    }
}
