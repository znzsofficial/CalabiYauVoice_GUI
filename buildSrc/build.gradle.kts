import org.gradle.kotlin.dsl.`kotlin-dsl`
import org.gradle.kotlin.dsl.repositories

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

gradlePlugin {
    plugins {
        register("downloadPage") {
            id = "download-page"
            implementationClass = "buildlogic.DownloadPagePlugin"
        }
    }
}
