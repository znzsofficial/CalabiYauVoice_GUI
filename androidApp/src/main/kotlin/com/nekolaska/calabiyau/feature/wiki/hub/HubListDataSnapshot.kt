package com.nekolaska.calabiyau.feature.wiki.hub

import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.weapon.list.WeaponListApi
import com.nekolaska.calabiyau.feature.wiki.map.model.GameModeData
import com.nekolaska.calabiyau.core.cache.MemoryCacheRegistry
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import data.ApiResult

/**
 * Hub 列表数据的进程级快照：
 * 主级页面切换会销毁 Hub 分支，重建时同步恢复数据避免重新转圈；
 * 搜索索引等其他消费者也可直接复用（命中时零请求）。
 * 会话内不自动刷新，新数据由列表页下拉刷新覆盖。
 */
internal object HubListDataSnapshot {
    private val lock = Any()
    private val requests = mutableMapOf<String, Long>()
    private var requestId = 0L
    private val epoch = mutableLongStateOf(0L)
    val generation: Long get() = epoch.longValue
    init { MemoryCacheRegistry.register("HubListDataSnapshot", ::clear) }

    private fun clear() = synchronized(lock) {
        epoch.longValue++
        requests.clear()
        factions = null; gameModes = null; weaponCategories = null
    }

    /** Only complete online successes can suppress reload on a later Hub entry. */
    suspend fun <T> capture(key: String, fetch: suspend () -> ApiResult<List<T>>, complete: (List<T>) -> Boolean,
                            publish: (List<T>?) -> Unit): ApiResult<List<T>> {
        val ticket = generation
        val request = synchronized(lock) { (++requestId).also { requests[key] = it } }
        val result = fetch()
        synchronized(lock) {
            if (generation == ticket && requests[key] == request) {
                publish(if (result is ApiResult.Success && !result.isOffline && complete(result.value)) result.value else null)
            }
        }
        return result
    }
    var factions: List<CharacterListApi.FactionData>? by mutableStateOf(null)

    var gameModes: List<GameModeData>? by mutableStateOf(null)

    var weaponCategories: List<WeaponListApi.WeaponCategoryData>? by mutableStateOf(null)
}
