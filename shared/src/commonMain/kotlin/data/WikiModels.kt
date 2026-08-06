package data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// === 搜索结果分组 ===
data class CharacterGroup(
    val characterName: String,
    val rootCategory: String,
    val subCategories: List<String>
)

// === MediaWiki API 模型 ===
@Serializable
data class WikiResponse(
    val query: WikiQuery? = null,
    @SerialName("continue") val continuation: Map<String, JsonElement>? = null
)

@Serializable
data class WikiQuery(
    val search: List<SearchItem>? = null,
    val categorymembers: List<CategoryMember>? = null,
    val pages: Map<String, WikiPage>? = null
)

@Serializable
data class SearchItem(val title: String)

@Serializable
data class CategoryMember(val ns: Int, val title: String)

@Serializable
data class WikiPage(val title: String, val imageinfo: List<ImageInfo>? = null)

@Serializable
data class ImageInfo(val url: String? = null, val mime: String? = null)

// allimages API 专用模型
@Serializable
data class AiItem(val name: String, val url: String? = null, val mime: String? = null)

@Serializable
data class AiQuery(val allimages: List<AiItem>? = null)

@Serializable
data class AiResponse(
    val query: AiQuery? = null,
    @SerialName("continue") val continuation: Map<String, JsonElement>? = null
)

// === 正则与工具函数 ===
val categoryPrefixRegex = Regex("^(Category:|分类:)")
val filePrefixRegex = Regex("^(File:|文件:)")
private val sanitizeRegex = Regex("[\\u0000-\\u001f\\\\/:*?\"<>|]")
private val windowsDeviceNames = Regex("(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?$")

fun sanitizeFileName(name: String): String {
    val sanitized = name.replace(sanitizeRegex, "_").trim().trimEnd('.', ' ')
    return when {
        sanitized.isBlank() || sanitized == "." || sanitized == ".." -> "_"
        windowsDeviceNames.matches(sanitized) -> "_$sanitized"
        else -> sanitized.take(180).trimEnd('.', ' ').ifBlank { "_" }
    }
}
