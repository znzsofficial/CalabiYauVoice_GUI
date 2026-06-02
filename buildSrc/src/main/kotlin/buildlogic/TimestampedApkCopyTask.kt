package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

abstract class TimestampedApkCopyTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseApkDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val versionName: Property<String>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun copyApk() {
        val apkDir = releaseApkDirectory.get().asFile
        val outputDir = outputDirectory.get().asFile.apply { mkdirs() }
        val sourceApk = apkDir
            .listFiles { file ->
                file.isFile &&
                    file.extension.equals("apk", ignoreCase = true) &&
                    !file.name.startsWith("卡丘Wiki助手_")
            }
            ?.maxByOrNull { it.lastModified() }
            ?: error("No release APK found in ${apkDir.path}")

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val targetApk = outputDir.resolve("卡丘Wiki助手_${versionName.get()}_${timestamp}.apk")
        sourceApk.copyTo(targetApk, overwrite = true)
        logger.lifecycle("Copied ${sourceApk.name} -> ${targetApk.path}")
    }
}
