import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
}

group = "com.nekolaska"
version = "1.3.0"

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

kotlin {
    jvmToolchain(17)

    jvm("desktop")

    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.foundation:foundation:1.10.1")
            implementation("org.jetbrains.compose.ui:ui:1.10.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
            implementation("com.squareup.okhttp3:okhttp:5.3.2")
            implementation("com.squareup.okio:okio:3.16.4")
        }

        named("desktopMain").dependencies {
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
                exclude(group = "org.jetbrains.compose.material3")
                exclude(group = "org.jetbrains.compose.material3.adaptive")
            }
            implementation("org.jetbrains.compose.foundation:foundation-desktop:1.10.1")
            implementation("org.jetbrains.compose.ui:ui-desktop:1.10.1")
            implementation("io.github.compose-fluent:fluent:v0.1.0")
            implementation("io.github.compose-fluent:fluent-icons-extended:v0.1.0")
            implementation(fileTree("libs") {
                include("*.jar")
            })
            implementation("net.java.dev.jna:jna-jpms:5.18.1")
            implementation("net.java.dev.jna:jna-platform-jpms:5.18.1")
            implementation("com.formdev:flatlaf:3.7")
            implementation("com.squareup.okhttp3:okhttp-urlconnection:5.3.2")
            implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
            implementation("org.jflac:jflac-codec:1.5.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
        }

        named("desktopTest").dependencies {
            implementation("junit:junit:4.13.2")
        }

        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.compose.material3:material3:1.3.1")
            implementation("androidx.compose.material:material-icons-extended:1.7.6")
            implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
            implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
            implementation("com.squareup.okhttp3:okhttp-urlconnection:5.3.2")
            implementation("io.coil-kt.coil3:coil-compose:3.2.0")
            implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
        }
    }
}

android {
    namespace = "com.nekolaska.calabiyau"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nekolaska.calabiyau"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.3.0"
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("/META-INF/DEPENDENCIES")
            pickFirsts.add("META-INF/*")
            pickFirsts.add("META-INF/versions/**")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-android.pro"
            )
        }
    }

    lint {
        disable += "NullSafeMutableLiveData"
        checkReleaseBuilds = false
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            packageName = "CalabiYauVoice_GUI"
            packageVersion = "1.3.0"
            description = "CalabiYau Wiki Voice Downloader GUI"
            copyright = "Apache License, Version 2.0"
            vendor = "NekoLaska"
            licenseFile.set(project.file("LICENSE.txt"))

            windows {
                iconFile.set(project.file("icon.ico"))
                dirChooser = true
                shortcut = true
                menuGroup = "CalabiYauVoice_GUI"
                upgradeUuid = "20CC6535-B192-4E61-9F4A-6EC79565C1A2"
            }
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
        }

        buildTypes {
            release {
                proguard {
                    optimize = true
                    isEnabled = true
                    configurationFiles.from(project.file("proguard-rules.pro"))
                }
            }
        }
    }
}
