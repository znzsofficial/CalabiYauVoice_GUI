package data

/**
 * 搜索模式 —— 双端共用。
 */
enum class SearchMode {
    /** 仅搜索语音文件 */
    VOICE_ONLY,
    /** 搜索所有分类 */
    ALL_CATEGORIES,
    /** 文件搜索 */
    FILE_SEARCH,
    /** 立绘预览 */
    PORTRAIT
}
