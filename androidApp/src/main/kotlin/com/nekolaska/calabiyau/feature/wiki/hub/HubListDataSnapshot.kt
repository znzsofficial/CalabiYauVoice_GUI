package com.nekolaska.calabiyau.feature.wiki.hub

import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.weapon.list.WeaponListApi
import com.nekolaska.calabiyau.feature.wiki.map.model.GameModeData

/**
 * Hub 列表数据的进程级快照：
 * 主级页面切换会销毁 Hub 分支，重建时同步恢复数据避免重新转圈；
 * 搜索索引等其他消费者也可直接复用（命中时零请求）。
 * 会话内不自动刷新，新数据由列表页下拉刷新覆盖。
 */
internal object HubListDataSnapshot {
    @Volatile
    var factions: List<CharacterListApi.FactionData>? = null

    @Volatile
    var gameModes: List<GameModeData>? = null

    @Volatile
    var weaponCategories: List<WeaponListApi.WeaponCategoryData>? = null
}
