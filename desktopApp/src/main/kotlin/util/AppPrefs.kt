package util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 应用全局偏好存储，使用 JSON 持久化到
 * %APPDATA%\CalabiYauVoice（即 AppData\Roaming\CalabiYauVoice）。
 */
@Serializable
data class PrefsData(
    var categoryHintDismissed: Boolean = false,
    var savePath: String = "${System.getProperty("user.home")}${File.separator}卡拉彼丘资源",
    var converterSavePath: String = "${System.getProperty("user.home")}${File.separator}卡拉彼丘资源${File.separator}converted",
    var assetToolsOutputPath: String = "${System.getProperty("user.home")}${File.separator}卡拉彼丘资源${File.separator}素材工具",
    var recentUserLookupIds: List<String> = emptyList(),
    var recentBidLookupValues: List<String> = emptyList(),
    var recentWikiIdLookupValues: List<String> = emptyList()
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

    private inline fun <T> pref(
        crossinline getter: (PrefsData) -> T,
        crossinline setter: (PrefsData, T) -> Unit
    ) = object : kotlin.properties.ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = getter(data)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
            setter(data, value)
            save()
        }
    }

    var categoryHintDismissed by pref({ it.categoryHintDismissed }, { d, v -> d.categoryHintDismissed = v })
    var savePath by pref({ it.savePath }, { d, v -> d.savePath = v })
    var converterSavePath by pref({ it.converterSavePath }, { d, v -> d.converterSavePath = v })
    var assetToolsOutputPath by pref({ it.assetToolsOutputPath }, { d, v -> d.assetToolsOutputPath = v })
    var recentBidLookupValues by pref({ it.recentBidLookupValues }, { d, v -> d.recentBidLookupValues = v })

    var recentWikiIdLookupValues: List<String>
        // 向后兼容：如果新字段为空，则迁移旧字段 recentUserLookupIds 的数据
        get() = data.recentWikiIdLookupValues.ifEmpty { data.recentUserLookupIds }
        set(value) { data.recentWikiIdLookupValues = value; save() }
}
