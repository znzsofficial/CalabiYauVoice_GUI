pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/firework/dev")
    }

    plugins {
        kotlin("multiplatform").version(extra["kotlin.version"] as String)
        kotlin("plugin.serialization").version(extra["kotlin.version"] as String)
        id("org.jetbrains.compose").version(extra["compose.version"] as String)
        id("org.jetbrains.kotlin.plugin.compose").version(extra["kotlin.version"] as String)
        id("com.android.application").version(extra["agp.version"] as String)
        id("org.jetbrains.compose.hot-reload").version("1.1.0-beta02")
    }
}

// R8 版本覆盖：AGP 8.9.1 自带的 R8 不支持 Kotlin 2.3.x metadata，
// 需要使用更新的 R8 版本。参考：https://developer.android.com/studio/build/kotlin-d8-r8-versions
buildscript {
    repositories {
        maven("https://storage.googleapis.com/r8-releases/raw")
    }
    dependencies {
        classpath("com.android.tools:r8:8.13.19")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "CalabiYauVoice_GUI"
