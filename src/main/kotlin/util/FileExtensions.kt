package util

/** 可预览的图片后缀 */
val IMAGE_EXTS: Set<String> = setOf("png", "jpg", "jpeg", "gif", "webp")

/** 可播放的音频后缀 */
val AUDIO_EXTS: Set<String> = setOf("mp3", "wav", "ogg", "flac", "aac", "m4a")

/** 提取文件后缀（忽略 URL 查询参数） */
fun String.fileExt(): String = substringAfterLast('.', "").lowercase().substringBefore('?')

/** 根据文件名或 URL 后缀判断是否为可预览的图片 */
fun isImageFile(name: String, url: String): Boolean =
    name.fileExt() in IMAGE_EXTS || url.fileExt() in IMAGE_EXTS

/** 根据文件名或 URL 后缀判断是否为音频文件 */
fun isAudioFile(name: String, url: String): Boolean =
    name.fileExt() in AUDIO_EXTS || url.fileExt() in AUDIO_EXTS

