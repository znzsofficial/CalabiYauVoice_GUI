package com.nekolaska.calabiyau.feature.wiki.stringer

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nekolaska.calabiyau.feature.wiki.stringer.model.TalentItem
import com.nekolaska.calabiyau.feature.wiki.stringer.model.TalentSection

@Preview(widthDp = 320, heightDp = 500, fontScale = 1.5f, locale = "zh-rCN")
@Preview(widthDp = 480, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "zh-rCN")
@Composable
internal fun StringerTalentPreview() {
    val entries = listOf(
        TalentItem("瞬时开镜", "Lv.12", "Lv.10", listOf("主武器开镜速度提升 0.8% / 1.6% / 2.4% / 3.2% / 4% / 4.8% / 5.6% / 6.4% / 7.2% / 8%"), null),
        TalentItem("灵巧步伐", "Lv.15", "Lv.5", listOf("最大跑速提升 1% / 2% / 3% / 4% / 5%"), null)
    )
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface { TalentSectionList(listOf(TalentSection("机能", entries))) }
    }
}
