# 1. 忽略非关键的警告（避免打包时被警告打断）
# - OkHttp 可选依赖 (GraalVM, Conscrypt, BouncyCastle, OpenJSSE)
-dontwarn okhttp3.internal.graal.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# - Jsoup 可选依赖 (Google RE2J 正则库)
-dontwarn org.jsoup.helper.Re2jRegex
-dontwarn com.google.re2j.**

# - Compose Desktop/Material 内部引用警告（使用 Fluent UI 时常见）
-dontwarn androidx.compose.desktop.**
-dontwarn androidx.compose.material.**

# - FlatLaf 兼容性检查警告 (Java 版本相关)
-dontwarn com.formdev.flatlaf.**
-dontwarn java.lang.invoke.MethodHandle

# - Okio 非关键警告
-dontwarn okio.**

# ==============================
# 核心功能保护（防止运行崩溃）
# ==============================

# 1. 协程相关（Compose 核心依赖）
-keep class kotlinx.coroutines.** { *; }

# 2. OkHttp & Okio 核心保护（解决 BufferedSource 类型错误）
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# 3. JNA 完整保护（解决反射/结构体/本地方法问题）
# - 基础 JNA 类和接口
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Library {
    public *;
}

# - JNA 结构体（包括 WindowMargins）
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
    <methods>;
}
-keep class jna.windows.structure.WindowMargins {
    <fields>;
}

# - 本地方法（Native Method）
-keepclasseswithmembers class * {
    native <methods>;
}

# 4. Window Styler 保护（桌面窗口样式相关）
-keep class com.mayakapps.compose.windowstyler.** { *; }

# 5. Java Sound SPI 保护
# mp3spi 和 tritonus 通过 META-INF/services 动态加载，ProGuard 无法静态分析到引用，
# 必须显式保留，否则打包后 AudioSystem 找不到 MP3/PCM 解码器导致播放失败。
-keep class javazoom.spi.** { *; }
-keep class javazoom.jl.** { *; }
-keep class org.tritonus.** { *; }
-keep class javax.sound.sampled.spi.** { *; }
-keep class javax.sound.sampled.AudioSystem { *; }

# 保留所有 SPI 实现类（AudioFileReader / AudioInputStream / FormatConversionProvider）
-keep class * implements javax.sound.sampled.spi.AudioFileReader { *; }
-keep class * implements javax.sound.sampled.spi.AudioInputStream { *; }
-keep class * implements javax.sound.sampled.spi.FormatConversionProvider { *; }
-keep class * implements javax.sound.sampled.spi.MixerProvider { *; }

# 同时防止 SPI 服务文件里的类名被混淆（obfuscate 关闭时无影响，但作为保障）
-keepnames class javazoom.** { *; }
-keepnames class org.tritonus.** { *; }
