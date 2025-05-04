import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.nekolaska"
version = "1.0-SNAPSHOT"

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
    implementation("com.konyaco:fluent:0.0.1-dev.8")
    //implementation("com.konyaco:fluent-icons-extended:0.0.1-dev.8") // If you want to use full fluent icons.

    //implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    //implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    //implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    implementation("com.formdev:flatlaf:3.6")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.11.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            packageName = "CalabiYauVoice_GUI"
            packageVersion = "1.0.0"
            description = "CalabiYau Wiki Voice Downloader GUI"
            copyright = "© 2025 NekoLaska"
            vendor = "NekoLaska"

            windows {
                //安装好后自动创建快捷方式
                shortcut = true
                menuGroup = "CalabiYauVoice_GUI"
                upgradeUuid = "20CC6535-B192-4E61-9F4A-6EC79565C1A2"
            }
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "CalabiYauVoice_GUI"
            packageVersion = "1.0.0"
        }


        buildTypes {
            release { // 配置 release 构建类型
                proguard {
                    isEnabled.set(false)
                }
            }
        }
    }
}
