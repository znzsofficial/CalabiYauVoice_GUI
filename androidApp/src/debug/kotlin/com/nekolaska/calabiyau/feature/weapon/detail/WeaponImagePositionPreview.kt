package com.nekolaska.calabiyau.feature.weapon.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "默认字号", widthDp = 640, heightDp = 430, locale = "zh-rCN")
@Preview(name = "大字号", widthDp = 640, heightDp = 430, fontScale = 1.5f, locale = "zh-rCN")
@Composable
internal fun WeaponImagePositionPreview() {
    val detail = WeaponDetailApi.WeaponDetail(
        name = "静风", user = "", type = "狙击枪", obtainMethod = "", description = "",
        fireRate = "", mobileFireRate = "", aimSpeed = "", spreadControl = "", recoilControl = "",
        reloadSpeed = "", moveSpeedChange = "", stringDamage = "", maxAmmo = "", magCapacity = "",
        mobileMagCapacity = "", secondaryAttack = "", fireMode = "", magnification = "", damageTable = emptyList(),
        baseDamage = "100", headMultiplier = "", upperMultiplier = "", lowerMultiplier = "",
        imageUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/3gAAAABJRU5ErkJggg==",
        subPages = emptyList()
    )
    MaterialTheme {
        Surface {
            Row {
                WeaponDetailSkeleton(Modifier.weight(1f), imageUrl = detail.imageUrl, weaponName = detail.name)
                Surface(Modifier.weight(1f)) { WeaponDetailContent(detail, onOpenWikiUrl = {}) }
            }
        }
    }
}
