package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class WebDistTask : DefaultTask() {
    @get:InputFile
    abstract val androidBuildFile: RegularFileProperty

    @get:InputFile
    abstract val latestJsonFile: RegularFileProperty

    @get:InputDirectory
    abstract val apkOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val downloadsDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val androidBuildText = androidBuildFile.get().asFile.readText()
        val versionName = Regex("versionName\\s*=\\s*\"([^\"]+)\"")
            .find(androidBuildText)
            ?.groupValues
            ?.get(1)
            ?: error("Cannot find android defaultConfig.versionName")
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)")
            .find(androidBuildText)
            ?.groupValues
            ?.get(1)
            ?: error("Cannot find android defaultConfig.versionCode")

        val apkDir = apkOutputDirectory.get().asFile
        val apkFile = apkDir
            .listFiles { file -> file.isFile && file.extension.equals("apk", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: error("No release APK found in ${apkDir.path}")

        val downloadsDir = downloadsDirectory.get().asFile
        downloadsDir.mkdirs()

        val targetApk = downloadsDir.resolve("CalabiYauVoice-latest.apk")
        apkFile.copyTo(targetApk, overwrite = true)

        val latestJson = latestJsonFile.get().asFile
        if (latestJson.exists()) {
            val oldJson = latestJson.readText()
            val updatedJson = oldJson
                .replace(Regex("\"versionName\"\\s*:\\s*\"[^\"]*\""), "\"versionName\": \"$versionName\"")
                .replace(Regex("\"versionCode\"\\s*:\\s*\\d+"), "\"versionCode\": $versionCode")
                .replace(Regex("\"apkSize\"\\s*:\\s*\\d+"), "\"apkSize\": ${targetApk.length()}")
                .replace(Regex("\"publishedAt\"\\s*:\\s*\"[^\"]*\""), "\"publishedAt\": \"${java.time.LocalDate.now()}\"")
            latestJson.writeText(updatedJson)
        }

        logger.lifecycle("Copied ${apkFile.name} -> ${targetApk.path}")
        logger.lifecycle("Updated ${latestJson.path} to version $versionName ($versionCode)")
    }
}
