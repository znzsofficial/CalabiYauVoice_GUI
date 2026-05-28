package buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register

class DownloadPagePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            tasks.register<WebDistTask>("webDist") {
                group = "distribution"
                description =
                    "Assembles Android release APK and copies it to downloadPage/downloads."

                dependsOn(":androidApp:assembleRelease")
                androidBuildFile.set(layout.projectDirectory.file("androidApp/build.gradle.kts"))
                latestJsonFile.set(layout.projectDirectory.file("downloadPage/downloads/latest.json"))
                apkOutputDirectory.set(layout.projectDirectory.dir("androidApp/build/outputs/apk/release"))
                downloadsDirectory.set(layout.projectDirectory.dir("downloadPage/downloads"))
            }

            tasks.register<Exec>("webPush") {
                group = "distribution"
                description = "Builds and deploys the download page to Cloudflare Pages."

                val npxCommand = if (System.getProperty("os.name").lowercase()
                        .contains("windows")
                ) "npx.cmd" else "npx"

                workingDir = rootDir
                commandLine(
                    npxCommand,
                    "wrangler",
                    "pages",
                    "deploy",
                    "downloadPage/dist",
                    "--project-name",
                    "calabiyauwiki",
                    "--branch=main"
                )
            }

            tasks.register<Exec>("webBuild") {
                group = "distribution"
                description = "Builds the Svelte download page."

                val npmCommand = if (System.getProperty("os.name").lowercase()
                        .contains("windows")
                ) "npm.cmd" else "npm"

                workingDir = layout.projectDirectory.dir("downloadPage").asFile
                commandLine(npmCommand, "run", "build")
            }

            tasks.register<Copy>("webStatic") {
                group = "distribution"
                description =
                    "Copies static Cloudflare and download assets into the web build output."

                dependsOn("webBuild")
                from(layout.projectDirectory.dir("downloadPage")) {
                    include("_headers")
                    include("_worker.js")
                include("download.html")
                include("downloads/**")
                include("icon.svg")
            }
                into(layout.projectDirectory.dir("downloadPage/dist"))
            }

            tasks.register<Exec>("webRun") {
                group = "distribution"
                description = "Builds and serves the Svelte download page locally."

                dependsOn("webStatic")

                val npmCommand = if (System.getProperty("os.name").lowercase()
                        .contains("windows")
                ) "npm.cmd" else "npm"

                workingDir = layout.projectDirectory.dir("downloadPage").asFile
                commandLine(
                    npmCommand,
                    "run",
                    "preview",
                    "--",
                    "--host",
                    "127.0.0.1",
                    "--port",
                    "4173"
                )
            }

            val webDistTask = tasks.named("webDist")
            val webBuildTask = tasks.named("webBuild")
            val webStaticTask = tasks.named("webStatic")
            val webRunTask = tasks.named("webRun")
            val webPushTask = tasks.named("webPush")

            webBuildTask.configure {
                mustRunAfter(webDistTask)
            }

            webStaticTask.configure {
                mustRunAfter(webDistTask)
            }

            webRunTask.configure {
                mustRunAfter(webStaticTask)
            }

            webPushTask.configure {
                dependsOn(webStaticTask)
                mustRunAfter(webStaticTask)
            }

            tasks.register("webDeploy") {
                group = "distribution"
                description = "Prepares download page release and deploys it to Cloudflare Pages."

                dependsOn(webDistTask)
                dependsOn(webPushTask)
            }
        }
    }
}
