pluginManagement {
    val foojayResolverVersion = file("gradle/libs.versions.toml")
        .readLines()
        .first { it.startsWith("foojay-resolver = ") }
        .substringAfter("\"")
        .substringBefore("\"")

    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverVersion
    }

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "CalabiYauVoice_GUI"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/kpm/public/")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io")
    }
}

include(":androidApp")
include(":desktopApp")
include(":shared")
