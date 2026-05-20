package portrait

// 立绘相关纯逻辑：数据类、解析函数、常量
// 不依赖任何平台 API，可在 commonMain 中被 desktop/android 共用

data class PortraitAsset(
    val title: String,
    val url: String
)

data class PortraitCostume(
    val key: String,
    val name: String,
    val illustration: PortraitAsset?,
    val frontPreview: PortraitAsset?,
    val backPreview: PortraitAsset?,
    val extraAssets: List<PortraitAsset> = emptyList()
)

data class CharacterPortraitCatalog(
    val characterName: String,
    val costumes: List<PortraitCostume>
)

internal enum class PortraitAssetSlot { ILLUSTRATION, FRONT, BACK }

private data class ParsedPortraitAsset(
    val costumeName: String,
    val slot: PortraitAssetSlot,
    val asset: PortraitAsset,
    val score: Int
)

// === 图片扩展名工具（内联，避免依赖 desktopMain util） ===

private val PORTRAIT_IMAGE_EXTS: Set<String> = setOf("png", "jpg", "jpeg", "gif", "webp")

private fun String.portraitFileExt(): String =
    substringAfterLast('.', "").lowercase().substringBefore('?')

internal fun isPortraitImageFile(name: String, url: String): Boolean =
    name.portraitFileExt() in PORTRAIT_IMAGE_EXTS || url.portraitFileExt() in PORTRAIT_IMAGE_EXTS

// === 目录常量 ===

val PORTRAIT_SOURCE_CATEGORIES: List<String> = listOf(
    "分类:角色立绘",
    "分类:晶源体文件"
)
val PREVIEW_SOURCE_CATEGORIES: List<String> = listOf(
    "分类:角色时装预览图"
)

private val ILLUSTRATION_KEYWORDS = listOf("角色立绘", "立绘")
private val FRONT_KEYWORDS = listOf("正面预览图", "正面预览", "正面", "前视", "前面")
private val BACK_KEYWORDS = listOf("背面预览图", "背面预览", "背面", "背视", "背身", "背部")
private val GENERIC_NAME_PARTS = listOf(
    "角色立绘", "立绘", "时装",
    "正面预览图", "背面预览图",
    "正面预览", "背面预览",
    "正面", "背面", "预览图", "预览"
)
private val DEFAULT_COSTUME_KEYWORDS = setOf("默认", "初始", "原皮", "基础")
private val RAW_CHARACTER_SUFFIXES = listOf("角色立绘", "立绘", "初始", "默认", "背面", "正面", "预览图", "预览")
private const val PREVIEW_PREFIX_TOKEN = "时装"
private val SEPARATOR_REGEX = Regex("[\\s_\\-·•]+")
private val WHITESPACE_REGEX = Regex("\\s+")
private const val DEFAULT_COSTUME_NAME = "默认时装"

// === 核心解析函数 ===

fun buildPortraitCatalog(
    characterName: String,
    files: List<Pair<String, String>>
): CharacterPortraitCatalog {
    val parsed = files
        .asSequence()
        .filter { (name, url) -> isPortraitImageFile(name, url) }
        .mapNotNull { (name, url) -> parsePortraitAsset(characterName, name, url) }
        .toList()

    val costumes = mergeDefaultCostumes(
        parsed.groupBy { normalizePortraitKey(it.costumeName) }
            .map { (_, assets) ->
                val sorted = assets.sortedWith(compareByDescending<ParsedPortraitAsset> { it.score }.thenBy { it.asset.title })
                val costumeName = sorted.firstOrNull()?.costumeName ?: DEFAULT_COSTUME_NAME
                val illustration = sorted.firstOrNull { it.slot == PortraitAssetSlot.ILLUSTRATION }?.asset
                val frontPreview = sorted.firstOrNull { it.slot == PortraitAssetSlot.FRONT }?.asset
                val backPreview = sorted.firstOrNull { it.slot == PortraitAssetSlot.BACK }?.asset
                val usedTitles = setOfNotNull(illustration?.title, frontPreview?.title, backPreview?.title)
                PortraitCostume(
                    key = normalizePortraitKey(costumeName),
                    name = costumeName,
                    illustration = illustration,
                    frontPreview = frontPreview,
                    backPreview = backPreview,
                    extraAssets = sorted.map { it.asset }.filterNot { it.title in usedTitles }
                )
            }
    ).sortedWith(compareBy<PortraitCostume> { !isDefaultCostumeName(it.name) }.thenBy { it.name.length }.thenBy { it.name })

    return CharacterPortraitCatalog(characterName = characterName, costumes = costumes)
}

private fun parsePortraitAsset(
    characterName: String,
    fileName: String,
    url: String
): ParsedPortraitAsset? {
    val rawName = fileName.substringAfter(':').substringBeforeLast('.')
    val slot = detectAssetSlot(rawName) ?: return null
    val costumeName = extractCostumeName(characterName, rawName)
    return ParsedPortraitAsset(
        costumeName = costumeName,
        slot = slot,
        asset = PortraitAsset(title = fileName, url = url),
        score = scoreAsset(slot, rawName, url)
    )
}

private fun detectAssetSlot(rawName: String): PortraitAssetSlot? {
    return when {
        ILLUSTRATION_KEYWORDS.any(rawName::contains) -> PortraitAssetSlot.ILLUSTRATION
        BACK_KEYWORDS.any(rawName::contains) -> PortraitAssetSlot.BACK
        FRONT_KEYWORDS.any(rawName::contains) -> PortraitAssetSlot.FRONT
        rawName.contains(PREVIEW_PREFIX_TOKEN) -> PortraitAssetSlot.FRONT
        rawName.contains("预览") -> PortraitAssetSlot.FRONT
        else -> null
    }
}

private fun extractCostumeName(characterName: String, rawName: String): String {
    var working = rawName
    characterNameVariants(characterName)
        .sortedByDescending { it.length }
        .forEach { variant ->
            working = working.replace("${variant}时装-", " ")
            working = working.replace("${variant}时装_", " ")
            working = working.replace("${variant}时装", " ")
            working = working.replace("${variant}-", " ")
            working = working.replace("${variant}_", " ")
            working = working.replace(variant, " ")
        }
    GENERIC_NAME_PARTS.forEach { token -> working = working.replace(token, " ") }
    working = working
        .replace(SEPARATOR_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    return when {
        working.isBlank() -> DEFAULT_COSTUME_NAME
        DEFAULT_COSTUME_KEYWORDS.any { working == it || working.startsWith("$it ") || working.endsWith(" $it") } -> DEFAULT_COSTUME_NAME
        else -> working
    }
}

private fun scoreAsset(slot: PortraitAssetSlot, rawName: String, url: String): Int {
    val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
    val extScore = when (ext) {
        "png" -> 40
        "webp" -> 36
        "jpg", "jpeg" -> 32
        "gif" -> 24
        else -> 12
    }
    val slotScore = when (slot) {
        PortraitAssetSlot.ILLUSTRATION -> when {
            rawName.contains("角色立绘") -> 60
            rawName.contains("立绘") -> 48
            else -> 0
        }
        PortraitAssetSlot.FRONT -> when {
            rawName.contains("正面预览图") -> 60
            rawName.contains("正面预览") -> 54
            rawName.contains("正面") -> 46
            rawName.contains(PREVIEW_PREFIX_TOKEN) -> 40
            rawName.contains("预览") -> 28
            else -> 0
        }
        PortraitAssetSlot.BACK -> when {
            rawName.contains("背面预览图") -> 60
            rawName.contains("背面预览") -> 54
            rawName.contains("背面") -> 46
            rawName.contains("_背面") || rawName.contains("-背面") || rawName.contains(" 背面") -> 42
            rawName.contains("背") -> 20
            else -> 0
        }
    }
    return extScore + slotScore
}

fun normalizePortraitQuery(keyword: String): String {
    val trimmed = keyword.trim()
    return if (trimmed.isBlank() || trimmed == "角色" || trimmed == "全部角色") "" else normalizePortraitKey(trimmed)
}

private fun characterNameVariants(characterName: String): Set<String> = buildSet {
    add(characterName)
    add(characterName.replace("·", ""))
    add(characterName.replace("·", " "))
    add(characterName.replace("·", "-"))
}

private fun isDefaultCostumeName(name: String): Boolean =
    name == DEFAULT_COSTUME_NAME || DEFAULT_COSTUME_KEYWORDS.any { name == it }

fun normalizePortraitKey(value: String): String = value
    .replace(Regex("\\.[^.]+$"), "")
    .replace(Regex("[\\s_\\-·•()（）\\[\\]【】]+"), "")
    .lowercase()

fun extractPortraitCharacterName(fileName: String): String? {
    val rawName = fileName.substringAfter(':').substringBeforeLast('.').trim()
    val fromPreview = rawName.substringBefore("时装-", missingDelimiterValue = "")
        .ifBlank { rawName.substringBefore("时装_", missingDelimiterValue = "") }
        .ifBlank { rawName.substringBefore("时装", missingDelimiterValue = "") }
        .trim()
    if (fromPreview.isNotBlank()) return fromPreview

    val fromSeparatedIllustration = rawName.substringBefore('-', missingDelimiterValue = "")
        .ifBlank { rawName.substringBefore('_', missingDelimiterValue = "") }
        .trim()
    if (fromSeparatedIllustration.isNotBlank()) return fromSeparatedIllustration

    val compactCandidate = RAW_CHARACTER_SUFFIXES.fold(rawName) { acc, suffix ->
        if (acc.endsWith(suffix)) acc.removeSuffix(suffix).trim() else acc
    }.trim()

    return compactCandidate.takeIf {
        it.isNotBlank() && it != rawName &&
            !it.contains("时装") && !it.contains("立绘")
    } ?: rawName.takeIf {
        it.isNotBlank() && !it.contains("时装") && !it.contains("立绘") && !it.contains("背面") && !it.contains("正面")
    }
}

fun remapPortraitFilesToOfficialNames(
    rawGrouped: Map<String, List<Pair<String, String>>>,
    officialNames: List<String>
): Map<String, List<Pair<String, String>>> {
    val remaining = rawGrouped.toMutableMap()
    val remapped = linkedMapOf<String, List<Pair<String, String>>>()

    officialNames.forEach { officialName ->
        val matchedKey = remaining.keys
            .map { indexedName -> indexedName to characterNameMatchScore(officialName, indexedName) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first

        if (matchedKey != null) {
            remapped[officialName] = remaining.remove(matchedKey).orEmpty()
        }
    }

    return remapped
}

fun groupRawPortraitFiles(
    files: List<Pair<String, String>>
): Map<String, List<Pair<String, String>>> {
    return files
        .distinctBy { it.second }
        .mapNotNull { file -> extractPortraitCharacterName(file.first)?.let { it to file } }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .mapValues { (_, groupedFiles) -> groupedFiles.distinctBy { it.second } }
}

fun searchCharacterNames(
    characterNames: Collection<String>,
    query: String
): List<String> {
    val normalizedQuery = normalizePortraitQuery(query)
    return characterNames
        .filter { normalizedQuery.isBlank() || normalizePortraitKey(it).contains(normalizedQuery) }
        .sortedWith(
            compareBy<String> { if (normalizedQuery.isBlank()) 0 else normalizePortraitKey(it).indexOf(normalizedQuery).coerceAtLeast(0) }
                .thenBy { it.length }
                .thenBy { it }
        )
}

fun findArchivedFilesForCharacter(
    index: Map<String, List<Pair<String, String>>>,
    characterName: String
): List<Pair<String, String>> {
    val directMatch = index[characterName]
    if (!directMatch.isNullOrEmpty()) return directMatch

    return index.entries
        .firstOrNull { arePortraitCharacterNamesCompatible(characterName, it.key) }
        ?.value
        .orEmpty()
}

private fun mergeDefaultCostumes(costumes: List<PortraitCostume>): List<PortraitCostume> {
    val defaultIllustrationCostumes = costumes.filter { it.illustration != null && isDefaultCostumeName(it.name) }
    if (defaultIllustrationCostumes.size != 1) return costumes

    val previewOnlyCandidates = costumes.filter {
        it.key != defaultIllustrationCostumes.single().key &&
            it.illustration == null &&
            (it.frontPreview != null || it.backPreview != null)
    }
    val completePreviewCandidates = previewOnlyCandidates.filter { it.frontPreview != null && it.backPreview != null }
    val mergeTarget = when {
        completePreviewCandidates.size == 1 -> completePreviewCandidates.single()
        previewOnlyCandidates.size == 1 -> previewOnlyCandidates.single()
        else -> null
    } ?: return costumes

    val defaultCostume = defaultIllustrationCostumes.single()
    val merged = mergeCostumeAssets(base = mergeTarget, defaultCostume = defaultCostume)
    return costumes
        .filterNot { it.key == defaultCostume.key || it.key == mergeTarget.key }
        .plus(merged)
}

private fun mergeCostumeAssets(
    base: PortraitCostume,
    defaultCostume: PortraitCostume
): PortraitCostume {
    val extras = buildList {
        addAll(base.extraAssets)
        addAll(defaultCostume.extraAssets)
        listOfNotNull(defaultCostume.frontPreview, defaultCostume.backPreview)
            .filterNot { it.url == base.frontPreview?.url || it.url == base.backPreview?.url }
            .forEach(::add)
    }.distinctBy { it.url }

    return base.copy(
        illustration = base.illustration ?: defaultCostume.illustration,
        frontPreview = base.frontPreview ?: defaultCostume.frontPreview,
        backPreview = base.backPreview ?: defaultCostume.backPreview,
        extraAssets = extras
    )
}

fun arePortraitCharacterNamesCompatible(a: String, b: String): Boolean = characterNameMatchScore(a, b) > 0

fun characterNameMatchScore(officialName: String, indexedName: String): Int {
    val official = normalizePortraitKey(officialName)
    val indexed = normalizePortraitKey(indexedName)
    return when {
        official.isBlank() || indexed.isBlank() -> 0
        official == indexed -> 100
        official.contains(indexed) || indexed.contains(official) -> 80 - kotlin.math.abs(official.length - indexed.length)
        official.startsWith(indexed) || indexed.startsWith(official) -> 70 - kotlin.math.abs(official.length - indexed.length)
        else -> 0
    }
}
