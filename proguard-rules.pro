# ============================================================
# Desktop ProGuard Rules — CalabiYauVoice_GUI
# ============================================================

# ============================================================
# Desktop ProGuard Rules — CalabiYauVoice_GUI
# ============================================================

# ---------- 通用 ----------
-keepattributes *Annotation*,InnerClasses,Signature,SourceFile,LineNumberTable
-dontnote **

# ---------- 抑制警告 ----------
-dontwarn okhttp3.internal.graal.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.jsoup.helper.Re2jRegex
-dontwarn com.google.re2j.**
-dontwarn androidx.compose.desktop.**
-dontwarn androidx.compose.material.**
-dontwarn com.formdev.flatlaf.**
-dontwarn java.lang.invoke.MethodHandle
-dontwarn okio.**
-dontwarn junit.**

# ---------- Kotlin Serialization ----------
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer {
    static **$$serializer INSTANCE;
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class data.**$$serializer { *; }

# ---------- Kotlin Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ---------- OkHttp / Okio ----------
# Okio 的接口/抽象类层次不能被 optimize 破坏
-keep class okio.BufferedSource { *; }
-keep class okio.BufferedSink { *; }
-keep class okio.Source { *; }
-keep class okio.Sink { *; }
-keep class okio.RealBufferedSource { *; }
-keep class okio.RealBufferedSink { *; }
-keep class okio.Okio { *; }
-keep class okio.Okio__OkioKt { *; }
# OkHttp 核心接口
-keep class okhttp3.CookieJar { *; }
-keep class okhttp3.Interceptor { *; }
-keep,includedescriptorclasses class * implements okhttp3.CookieJar { *; }
-keep,includedescriptorclasses class * implements okhttp3.Interceptor { *; }

# ---------- FlatLaf（仅使用 SystemFileChooser） ----------
-keep class com.formdev.flatlaf.util.SystemFileChooser { *; }
-keep class com.formdev.flatlaf.util.SystemFileChooser$* { *; }

# ---------- JNA（反射 + 本地方法，必须保留） ----------
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Library { public *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
    <methods>;
}
-keep class jna.windows.structure.** { <fields>; }
-keepclasseswithmembers class * { native <methods>; }

# ---------- ComposeWebView (Uniffi/JNA 绑定) ----------
-keep,includedescriptorclasses class io.github.kdroidfilter.webview.wry.** { *; }

# ---------- Window Styler（反射调用 Windows API） ----------
-keep class com.mayakapps.compose.windowstyler.** { *; }

# ---------- Java Sound SPI（ServiceLoader 动态加载） ----------
-keep class * implements javax.sound.sampled.spi.AudioFileReader { *; }
-keep class * implements javax.sound.sampled.spi.FormatConversionProvider { *; }
-keep class * implements javax.sound.sampled.spi.MixerProvider { *; }
-keepnames class javazoom.spi.** {}
-keepnames class javazoom.jl.decoder.** {}
-keepnames class org.tritonus.** {}
-keepnames class org.kc7bfi.jflac.** {}
-keepnames class org.jflac.** {}

# ---------- App 数据模型 ----------
-keepclassmembers class data.** { <fields>; }
-keepnames class data.** {}
