import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.compose.fluent)
    implementation(libs.compose.fluent.iconsExtended)
    implementation(fileTree("libs") {
        include("*.jar")
    })
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.compose.foundation.desktop)
    implementation(libs.compose.ui.desktop)
    implementation(libs.jna.jpms)
    implementation(libs.jna.platform.jpms)
    implementation(libs.flatlaf)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.mp3spi)
    implementation(libs.justflac)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.composewebview)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        jvmArgs += listOf(
            "-Dskiko.renderApi=OPENGL",
            //"-Dskiko.verbose=true" // 开启详细日志，方便验证
        )
        nativeDistributions {
            packageName = "CalabiYauVoice_GUI"
            packageVersion = "2.0.1"
            description = "CalabiYau Wiki Voice Downloader GUI"
            copyright = "Apache License, Version 2.0"
            vendor = "NekoLaska"
            licenseFile.set(rootProject.file("LICENSE.txt"))

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
