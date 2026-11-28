package com.nekolaska.calabiyau.feature.wiki.extraction

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.feature.wiki.extraction.model.knownMaterialTags

@Preview(widthDp = 320, fontScale = 1.5f, locale = "zh-rCN")
@Preview(widthDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "zh-rCN")
@Composable
internal fun MaterialTagFilterPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface { Box(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) { MaterialTagFilter(knownMaterialTags, "能量") {} } }
    }
}
