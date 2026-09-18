package data

/** An HTML fallback can have HTTP 200; report a service mismatch instead of leaking parser output. */
internal fun nonJsonApiMessage(body: String, replies: Boolean = false): String? {
    val trimmed = body.trimStart()
    if (trimmed.startsWith("{")) return null
    if (trimmed.startsWith("<")) {
        return if (replies) "回复服务暂不可用：服务器返回了网页，请稍后刷新重试。"
        else "服务暂不可用：服务器返回了网页，请稍后重试。"
    }
    return "服务器返回的数据格式异常，请稍后重试。"
}
