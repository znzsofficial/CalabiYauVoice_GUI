package com.nekolaska.calabiyau.feature.wiki.extraction

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.feature.wiki.extraction.model.DropRate
import data.ApiResult
import java.math.BigDecimal

// A long phase name and a genuine tie from the November 24 切割器/Q3 fixture.
private val sampleRates = listOf(
    DropRate("晶骸核心（阶段 6·升变）", "Boss阶段", 3, "掉落池", BigDecimal("0.936")),
    DropRate("商务手提箱", "容器", 3, "掉落池", BigDecimal("0.191")),
    DropRate("潮流旅行箱", "容器", 3, "掉落池", BigDecimal("0.191"))
)

@Preview(widthDp = 320, heightDp = 650, fontScale = 1.5f, locale = "zh-rCN")
@Preview(widthDp = 480, heightDp = 650, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "zh-rCN")
@Composable
internal fun DropRateLayoutPreview() {
    val rates = rememberLoadState(sampleRates, enabled = false, initiallyLoaded = true,
        cachedFetch = { ApiResult.Success(sampleRates) }, fetch = { ApiResult.Success(sampleRates) })
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            ExtractionDropLookupList(emptyMap(), "切割器", "Q3 卓越", {}, {}, rates = rates, onShowRates = {})
        }
    }
}
