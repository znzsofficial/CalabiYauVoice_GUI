import buildlogic.TimestampedApkCopyTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okio)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.lifecycle.viewmodelCompose.android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.graphics.path)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.capsule)
    implementation(libs.jsoup)

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.nekolaska.calabiyau"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.nekolaska.calabiyau"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 26
        versionName = "2.0.6"
    }

    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { localProps.load(it) }
    }
    val keystoreFile = localProps.getProperty("KEYSTORE_FILE")

    if (keystoreFile != null && file(keystoreFile).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = localProps.getProperty("KEYSTORE_PASSWORD")
                keyAlias = localProps.getProperty("KEY_ALIAS")
                keyPassword = localProps.getProperty("KEY_PASSWORD")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            pickFirsts += "META-INF/*"
            pickFirsts += "META-INF/versions/**"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-android.pro"
            )
            if (keystoreFile != null && file(keystoreFile).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
    }
}

val releaseVersionName = android.defaultConfig.versionName.orEmpty()

tasks.register<TimestampedApkCopyTask>("copyReleaseApkWithTimestamp") {
    group = "build"
    description = "Copies release APK with a fresh timestamped file name."
    dependsOn("assembleRelease")

    releaseApkDirectory.set(layout.buildDirectory.dir("outputs/apk/release"))
    outputDirectory.set(layout.buildDirectory.dir("outputs/apk/timestampedRelease"))
    versionName.set(releaseVersionName)
}
