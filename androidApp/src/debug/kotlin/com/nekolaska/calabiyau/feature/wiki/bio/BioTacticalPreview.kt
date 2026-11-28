package com.nekolaska.calabiyau.feature.wiki.bio

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(widthDp = 320, heightDp = 680, fontScale = 1.5f, locale = "zh-rCN")
@Preview(widthDp = 480, heightDp = 540, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "zh-rCN")
@Composable
internal fun BioTacticalPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BioFilterStatusRow("晶源体 · 卓越 · 12 张卡牌", hasActiveFilter = true, onReset = {})
                BioCardItem(
                    imageUrl = null, title = "盗取", meta = "晶源体 · 通用", effect = "卡牌效果示例",
                    badge = "卓越", accentColor = bioRarityColor(3), fallbackIcon = Icons.Outlined.AutoAwesome, onClick = {}
                )
                val minimums = DECK_RARITY_THRESHOLDS.getValue("晶源体")
                BioDeckQualitySummary(minimums + ("卓越" to 0), minimums)
            }
        }
    }
}
