package com.nekolaska.calabiyau.feature.wiki.stringer

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.feature.wiki.stringer.model.ModeCard

@Preview(widthDp = 320, heightDp = 560, fontScale = 1.5f, locale = "zh-rCN")
@Preview(widthDp = 480, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "zh-rCN")
@Composable
internal fun StringerPushCardPreview() {
    val card = ModeCard("火力全开", "输出", 4, "提升武器伤害", listOf("米雪儿·李", "信"), null)
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PushCardFilterSummary(12, listOf("米雪儿·李", "完美", "品质升序"), onReset = {})
                }
                item { ModeCardGridItem(card, onClick = {}) }
                item { ModeCardGridItem(card.copy(name = "护盾强化", rarity = 3, roles = emptyList()), onClick = {}) }
            }
        }
    }
}
