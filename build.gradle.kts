import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

abstract class PrepareDownloadPageReleaseTask : DefaultTask() {
    @get:InputFile
    abstract val androidBuildFile: RegularFileProperty

    @get:InputFile
    abstract val latestJsonFile: RegularFileProperty

    @get:OutputDirectory
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

tasks.register<PrepareDownloadPageReleaseTask>("prepareDownloadPageRelease") {
    group = "distribution"
    description = "Assembles Android release APK and copies it to downloadPage/downloads."

    dependsOn(":androidApp:assembleRelease")
    androidBuildFile.set(layout.projectDirectory.file("androidApp/build.gradle.kts"))
    latestJsonFile.set(layout.projectDirectory.file("downloadPage/downloads/latest.json"))
    apkOutputDirectory.set(layout.projectDirectory.dir("androidApp/build/outputs/apk/release"))
    downloadsDirectory.set(layout.projectDirectory.dir("downloadPage/downloads"))
}

tasks.register<Exec>("deployDownloadPage") {
    group = "distribution"
    description = "Prepares download page release and deploys it to Cloudflare Pages."

    dependsOn("prepareDownloadPageRelease")

    val npxCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "npx.cmd" else "npx"

    workingDir = rootDir
    commandLine(
        npxCommand,
        "wrangler",
        "pages",
        "deploy",
        "downloadPage",
        "--project-name",
        "calabiyauwiki",
        "--branch=main"
    )
}
