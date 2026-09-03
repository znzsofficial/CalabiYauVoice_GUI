package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@UntrackedTask(because = "Uploads APK to remote R2 and rewrites latest.json on each release")
abstract class WebDistTask : DefaultTask() {
    @get:InputFile
    abstract val androidBuildFile: RegularFileProperty

    @get:InputFile
    abstract val latestJsonFile: RegularFileProperty

    @get:InputDirectory
    abstract val apkOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val downloadsDirectory: DirectoryProperty

    @get:OutputFile
    abstract val stagedApkFile: RegularFileProperty

    @get:Input
    abstract val r2Bucket: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

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
            ?.toInt()
            ?: error("Cannot find android defaultConfig.versionCode")

        val apkDir = apkOutputDirectory.get().asFile
        val apkFile = apkDir
            .listFiles { file -> file.isFile && file.extension.equals("apk", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: error("No release APK found in ${apkDir.path}")

        val downloadsDir = downloadsDirectory.get().asFile
        downloadsDir.mkdirs()

        val versionedName = "CalabiYauVoice-$versionName.apk"
        val stagedApk = stagedApkFile.get().asFile
        apkFile.copyTo(stagedApk, overwrite = true)
        apkFile.copyTo(downloadsDir.resolve(versionedName), overwrite = true)

        val latestJson = latestJsonFile.get().asFile
        val previous = if (latestJson.exists()) parseLatestJson(latestJson.readText()) else emptyMap()
        val publishedAt = LocalDate.now().toString()
        val apkUrl = "/downloads/$versionedName"
        val next = linkedMapOf<String, Any?>(
            "versionName" to versionName,
            "versionCode" to versionCode,
            "apkUrl" to apkUrl,
            "apkSize" to stagedApk.length(),
            "releaseUrl" to "/",
            "changelog" to (previous["changelog"] ?: emptyList<String>()),
            "publishedAt" to publishedAt,
            "releases" to mergeReleases(previous, versionName, versionCode, apkUrl, stagedApk.length(), publishedAt),
        )
        latestJson.writeText(toPrettyJson(next))

        val bucket = r2Bucket.get()
        uploadToR2(bucket, "android/$versionedName", stagedApk, versionedName, immutable = true)
        uploadToR2(bucket, "android/CalabiYauVoice-latest.apk", stagedApk, "CalabiYauVoice-latest.apk", immutable = false)

        logger.lifecycle("Staged ${apkFile.name} -> ${stagedApk.path}")
        logger.lifecycle("Updated ${latestJson.path} to version $versionName ($versionCode)")
        logger.lifecycle("Uploaded $versionedName and CalabiYauVoice-latest.apk to R2 $bucket")
    }

    private fun uploadToR2(bucket: String, key: String, file: File, filename: String, immutable: Boolean) {
        val npx = if (System.getProperty("os.name").lowercase().contains("windows")) "npx.cmd" else "npx"
        val cacheControl = if (immutable) "public, max-age=31536000, immutable" else "public, max-age=300"
        val result = execOps(npx, "wrangler", "r2", "object", "put", "$bucket/$key",
            "--file=${file.absolutePath}",
            "--content-type=application/vnd.android.package-archive",
            "--content-disposition=attachment; filename=\"$filename\"",
            "--cache-control=$cacheControl",
            "--remote",
        )
        if (result != 0) error("Failed to upload $key to R2 bucket $bucket (exit $result)")
    }

    private fun execOps(vararg command: String): Int {
        return execOperations.exec {
            commandLine(*command)
            isIgnoreExitValue = true
        }.exitValue
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeReleases(
        previous: Map<String, Any?>,
        versionName: String,
        versionCode: Int,
        apkUrl: String,
        apkSize: Long,
        publishedAt: String,
    ): List<Map<String, Any?>> {
        val current = linkedMapOf<String, Any?>(
            "versionName" to versionName,
            "versionCode" to versionCode,
            "apkUrl" to apkUrl,
            "apkSize" to apkSize,
            "publishedAt" to publishedAt,
        )
        val existing = ((previous["releases"] as? List<*>) ?: emptyList<Any>())
            .mapNotNull { item -> (item as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } }
            .filter { it["versionName"] != versionName }
        val previousLatest = previous["versionName"]?.toString()
        val archived = if (!previousLatest.isNullOrBlank() && previousLatest != versionName) {
            listOf(
                linkedMapOf<String, Any?>(
                    "versionName" to previousLatest,
                    "versionCode" to previous["versionCode"],
                    "apkUrl" to previous["apkUrl"],
                    "apkSize" to previous["apkSize"],
                    "publishedAt" to previous["publishedAt"],
                )
            )
        } else {
            emptyList()
        }
        return (listOf(current) + archived + existing)
            .distinctBy { it["versionName"] }
    }

    private fun parseLatestJson(text: String): Map<String, Any?> {
        val groovyJson = groovy.json.JsonSlurper().parseText(text)
        @Suppress("UNCHECKED_CAST")
        return groovyJson as Map<String, Any?>
    }

    private fun toPrettyJson(value: Any?): String {
        return groovy.json.JsonBuilder(value).toPrettyString() + "\n"
    }
}
