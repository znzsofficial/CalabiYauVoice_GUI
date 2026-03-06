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
    val converterSavePath: String = "${System.getProperty("user.home")}${File.separator}卡拉彼丘资源${File.separator}converted"
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
}
