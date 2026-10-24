package com.nekolaska.calabiyau.feature.settings

import java.nio.file.Files
import java.io.File
import kotlin.test.*

class UpdateApkCleanupTest {
    @Test fun pendingInstallMarkerSurvivesStorageAndOnlyTargetsTheExactApk() {
        val pending = PendingAppUpdateInstall(52, "2.1.22", "D:/Android/data/app/files/CalabiYauVoice-2.1.22.apk")
        assertEquals(pending, PendingAppUpdateInstall.decode(pending.encode()))
        assertNull(PendingAppUpdateInstall.decode("broken"))
        assertNull(PendingAppUpdateInstall.decode(null))
    }

    @Test fun currentInstalledVersionMustReachTheRecordedTargetBeforeCleanup() {
        val pending = PendingAppUpdateInstall(52, "2.1.22", "/private/CalabiYauVoice-2.1.22.apk")
        assertFalse(UpdateApkCleanup.targetVersionInstalled(pending, 51, "2.1.21"))
        assertTrue(UpdateApkCleanup.targetVersionInstalled(pending, 52, "2.1.22"))
        assertTrue(UpdateApkCleanup.targetVersionInstalled(pending, 53, "2.1.23"))
        assertFalse(UpdateApkCleanup.targetVersionInstalled(PendingAppUpdateInstall(0, "2.1.22", "/x"), 51, "2.1.21"))
        assertTrue(UpdateApkCleanup.targetVersionInstalled(PendingAppUpdateInstall(0, "2.1.22", "/x"), 52, "2.1.22"))
    }

    @Test fun legacyCleanupRemovesOnlyAnInstalledPackageArchiveNotAnActiveOrFutureOrForeignApk() {
        val packageName = "com.nekolaska.calabiyau"
        assertTrue(UpdateApkCleanup.isObsoleteUpdaterApk("CalabiYauVoice-2.1.20.apk",
            UpdateArchiveInfo(packageName, 50), packageName, 51, false))
        assertTrue(UpdateApkCleanup.isObsoleteUpdaterApk("CalabiYauVoice-latest.apk",
            UpdateArchiveInfo(packageName, 51), packageName, 51, false))
        assertFalse(UpdateApkCleanup.isObsoleteUpdaterApk("CalabiYauVoice-2.1.22.apk",
            UpdateArchiveInfo(packageName, 52), packageName, 51, false))
        assertFalse(UpdateApkCleanup.isObsoleteUpdaterApk("CalabiYauVoice-2.1.20.apk",
            UpdateArchiveInfo("other.package", 50), packageName, 51, false))
        assertFalse(UpdateApkCleanup.isObsoleteUpdaterApk("random.apk",
            UpdateArchiveInfo(packageName, 50), packageName, 51, false))
        assertFalse(UpdateApkCleanup.isObsoleteUpdaterApk("CalabiYauVoice-2.1.20.apk",
            UpdateArchiveInfo(packageName, 50), packageName, 51, true))
        assertFalse(UpdateApkCleanup.isObsoleteUpdaterApk("CalabiYauVoice-2.1.20.apk",
            null, packageName, 51, false))
    }

    @Test fun legacySweepFailsClosedIfDownloadManagerHasUnknownActiveDestinations() {
        assertTrue(UpdateApkCleanup.canSweepLegacyApks(true, false))
        assertFalse(UpdateApkCleanup.canSweepLegacyApks(false, false))
        assertFalse(UpdateApkCleanup.canSweepLegacyApks(true, true))
    }

    @Test fun pendingCleanupWaitsForAProvablyInactiveTarget() {
        assertFalse(UpdateApkCleanup.canDeletePendingApk(false, "/app/update.apk", emptySet(), false))
        assertFalse(UpdateApkCleanup.canDeletePendingApk(true, null, emptySet(), false))
        assertFalse(UpdateApkCleanup.canDeletePendingApk(true, "/app/update.apk", setOf("/app/update.apk"), false))
        assertFalse(UpdateApkCleanup.canDeletePendingApk(true, "/app/update.apk", emptySet(), true))
        assertTrue(UpdateApkCleanup.canDeletePendingApk(true, "/app/update.apk", emptySet(), false))
    }

    @Test fun updaterPathGuardAcceptsOnlyDirectChildOfAppOwnedDirectories() {
        val root = Files.createTempDirectory("update-owned-root").toFile()
        val other = File(root.parentFile, root.name + "-other").apply { mkdirs() }
        try {
            val valid = File(root, "CalabiYauVoice-2.1.21.apk")
            val nested = File(File(root, "nested"), "CalabiYauVoice-2.1.21.apk")
            val traversal = File(root, "../${other.name}/CalabiYauVoice-2.1.21.apk")
            assertTrue(UpdateApkCleanup.isOwnedUpdaterPath(valid.absolutePath, listOf(root)))
            assertFalse(UpdateApkCleanup.isOwnedUpdaterPath(nested.absolutePath, listOf(root)))
            assertFalse(UpdateApkCleanup.isOwnedUpdaterPath(traversal.absolutePath, listOf(root)))
            assertFalse(UpdateApkCleanup.isOwnedUpdaterPath(File(root, "user.apk").absolutePath, listOf(root)))
        } finally { root.deleteRecursively(); other.deleteRecursively() }
    }

    @Test fun obsoleteArchiveFilenameCannotBeInferredAsObsoleteWithoutItsPackageAndCode() {
        // A user-named file may share the updater naming convention; metadata mismatch must retain it.
        assertFalse(UpdateApkCleanup.isObsoleteUpdaterApk("CalabiYauVoice-2.1.20.apk", null,
            "com.nekolaska.calabiyau", 51, false))
        assertFalse(UpdateApkCleanup.isObsoleteUpdaterApk("CalabiYauVoice-2.1.22.apk",
            UpdateArchiveInfo("com.nekolaska.calabiyau", 52), "com.nekolaska.calabiyau", 51, false))
    }

    @Test fun pendingApkIsDeletedOnlyAfterTargetInstallAndOnlyWithinOwnedRoot() {
        val root = Files.createTempDirectory("update-cleanup-private").toFile()
        try {
            val apk = File(root, "CalabiYauVoice-2.1.22.apk").apply { writeText("installer") }
            val marker = PendingAppUpdateInstall(52, "2.1.22", apk.absolutePath)
            assertFalse(UpdateApkCleanup.cleanupInstalledPendingApk(marker, 51, "2.1.21", listOf(root)))
            assertTrue(apk.exists())

            val removedRecords = mutableListOf<String>()
            assertTrue(UpdateApkCleanup.cleanupInstalledPendingApk(marker, 52, "2.1.22", listOf(root), removedRecords::add))
            assertFalse(apk.exists()); assertEquals(listOf(apk.canonicalPath), removedRecords)

            val outside = File(root.parentFile, "${root.name}-other/CalabiYauVoice-2.1.22.apk")
            assertTrue(UpdateApkCleanup.cleanupInstalledPendingApk(
                marker.copy(apkPath = outside.absolutePath), 52, "2.1.22", listOf(root)))
            assertFalse(outside.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun failedDeleteRetainsMarkerForANewerStartupAttempt() {
        val root = Files.createTempDirectory("update-cleanup-failed").toFile()
        try {
            val path = File(root, "CalabiYauVoice-2.1.22.apk").apply { writeText("apk") }
            val marker = PendingAppUpdateInstall(52, "2.1.22", path.absolutePath)
            assertFalse(UpdateApkCleanup.cleanupInstalledPendingApk(marker, 52, "2.1.22", listOf(root), delete = { false }))
            assertTrue(path.exists())
        } finally { root.deleteRecursively() }
    }
}
