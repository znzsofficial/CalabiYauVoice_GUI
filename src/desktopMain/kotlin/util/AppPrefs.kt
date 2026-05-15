package util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 应用全局偏好存储，使用 JSON 持久化到
 * %APPDATA%\CalabiYauVoice（即 AppData\Roaming\CalabiYauVoice）。
 */
@Serializable
private data class PrefsData(
    val categoryHintDismissed: Boolean = false,
    val savePath: String = "${System.getProperty("user.home")}${File.separator}卡拉彼丘资源",
    val converterSavePath: String = "${System.getProperty("user.home")}${File.separator}卡拉彼丘资源${File.separator}converted",
    val assetToolsOutputPath: String = "${System.getProperty("user.home")}${File.separator}卡拉彼丘资源${File.separator}素材工具",
    val recentUserLookupIds: List<String> = emptyList(),
    val recentBidLookupValues: List<String> = emptyList(),
    val recentWikiIdLookupValues: List<String> = emptyList()
)

object AppPrefs {

    private val file: File = File(
        System.getenv("APPDATA") ?: System.getProperty("user.home"),
        "CalabiYauVoice/prefs.json"
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private var data: PrefsData = load()
    private fun load(): PrefsData = runCatching {
        json.decodeFromString<PrefsData>(file.readText())
    }.getOrDefault(PrefsData())

    private fun save() {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(data))
    }

    var categoryHintDismissed: Boolean
        get() = data.categoryHintDismissed
        set(value) { data = data.copy(categoryHintDismissed = value); save() }

    var savePath: String
        get() = data.savePath
        set(value) { data = data.copy(savePath = value); save() }

    var converterSavePath: String
        get() = data.converterSavePath
        set(value) { data = data.copy(converterSavePath = value); save() }

    var assetToolsOutputPath: String
        get() = data.assetToolsOutputPath
        set(value) { data = data.copy(assetToolsOutputPath = value); save() }

    var recentBidLookupValues: List<String>
        get() = data.recentBidLookupValues
        set(value) { data = data.copy(recentBidLookupValues = value); save() }

    var recentWikiIdLookupValues: List<String>
        // 向后兼容：如果新字段为空，则迁移旧字段 recentUserLookupIds 的数据
        get() = data.recentWikiIdLookupValues.ifEmpty { data.recentUserLookupIds }
        set(value) { data = data.copy(recentWikiIdLookupValues = value); save() }
}
