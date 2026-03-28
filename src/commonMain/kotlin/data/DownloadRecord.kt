package data

import kotlinx.serialization.Serializable

/**
 * 下载记录 —— 双端共用数据模型。
 *
 * 使用 kotlinx.serialization，便于跨平台持久化。
 */
@Serializable
data class DownloadRecord(
    val name: String = "",          // 描述，如 "岚岚 / 默认" 或 "文件搜索"
    val fileCount: Int = 0,         // 文���数量
    val timestamp: Long = 0L,      // 完成时间戳
    val status: String = "success", // "success" | "error"
    val savePath: String = ""       // 保存路径
) {
    companion object {
        /**
         * 将记录列表序列化为 JSON 字符串。
         */
        fun encodeToJson(records: List<DownloadRecord>): String =
            SharedJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(serializer()), records)

        /**
         * 从 JSON 字符串反序列化为记录列表。
         */
        fun decodeFromJson(json: String): List<DownloadRecord> =
            try {
                SharedJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(serializer()), json)
            } catch (_: Exception) {
                emptyList()
            }
    }
}
