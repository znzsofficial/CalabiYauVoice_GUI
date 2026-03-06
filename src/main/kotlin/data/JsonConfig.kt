package data

import kotlinx.serialization.json.Json

/**
 * 全局共享 JSON 解析器，供 data 层各模块复用。
 * 配置：忽略未知 Key + 强制输入默认值。
 */
val SharedJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

