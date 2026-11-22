package com.nekolaska.calabiyau.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadDirectorySummaryTest {
    @Test fun localizesOnlyThePublicDownloadDirectory() {
        for (volume in listOf("/storage/emulated/0", "/storage/emulated/10", "/sdcard", "/storage/self/primary", "/storage/ABCD-1234")) {
            assertEquals("下载", downloadDirectorySummary("$volume/Download/"))
            assertEquals("下载 › CalabiYauVoice", downloadDirectorySummary("$volume/Download/CalabiYauVoice"))
        }
        assertEquals("Download › 语音", downloadDirectorySummary("/storage/emulated/0/Music/Download/语音"))
        assertEquals("DownloadBackup › 语音", downloadDirectorySummary("/storage/emulated/0/DownloadBackup/语音"))
    }

    @Test fun showsTheActualParentAndLeafForCustomOrDeeperDirectories() {
        assertEquals("角色 › 新 语音", downloadDirectorySummary("/storage/emulated/0/Download/角色/新 语音/"))
        assertEquals("files › CalabiYauVoice", downloadDirectorySummary("/storage/emulated/0/Android/data/app/files/CalabiYauVoice"))
        assertEquals("Music › 语音", downloadDirectorySummary("/storage/emulated/0/Download/../Music/语音"))
        assertEquals("素材", downloadDirectorySummary("/素材/"))
        assertEquals("/", downloadDirectorySummary("/"))
        assertEquals("未设置", downloadDirectorySummary(""))
        assertEquals("CalabiYauVoice", downloadDirectorySummary("/sdcard/Download/CalabiYauVoice", includeParent = false))
        assertEquals("目录 › 自选", downloadDirectorySummary("/sdcard/Download/目录 › 自选", includeParent = false))
    }
}
