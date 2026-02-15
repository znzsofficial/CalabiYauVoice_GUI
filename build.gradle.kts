import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.nekolaska"
version = "1.2.0"

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // See https://github.com/JetBrains/Jewel/releases for the release notes
//    implementation("org.jetbrains.jewel:jewel-int-ui-standalone-243:0.27.0")
//    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window-243:0.27.0")
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation("org.jetbrains.compose.ui:ui-graphics:1.11.0-alpha01")
    //implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.10.0")
    implementation("io.github.compose-fluent:fluent:v0.1.0")
    implementation("io.github.compose-fluent:fluent-icons-extended:v0.1.0")
    //implementation("com.mayakapps.compose:window-styler:0.3.3-SNAPSHOT")
    implementation(fileTree("libs") {
        include("*.jar")
    })
    implementation("net.java.dev.jna:jna-jpms:5.18.1")
    implementation("net.java.dev.jna:jna-platform-jpms:5.18.1")

    //implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    //implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    //implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    implementation("com.formdev:flatlaf:3.7")

    //implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    //implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("com.squareup.okio:okio:3.16.2")
    implementation("org.jsoup:jsoup:1.22.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0-RC")
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            packageName = "CalabiYauVoice_GUI"
            packageVersion = "1.2.0"
            description = "CalabiYau Wiki Voice Downloader GUI"
            copyright = "Apache License, Version 2.0"
            vendor = "NekoLaska"
            licenseFile.set(project.file("LICENSE.txt"))

            windows {
                iconFile.set(project.file("icon.ico"))
                dirChooser = true
                //安装好后自动创建快捷方式
                shortcut = true
                menuGroup = "CalabiYauVoice_GUI"
                upgradeUuid = "20CC6535-B192-4E61-9F4A-6EC79565C1A2"
            }
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
        }


        buildTypes {
            release { // 配置 release 构建类型
                proguard {
                    configurationFiles.from(project.file("proguard-rules.pro"))
                    isEnabled.set(true)
                }
            }
        }
    }
}