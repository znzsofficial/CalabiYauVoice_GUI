# 1. 忽略 OkHttp 的可选依赖 (GraalVM, Conscrypt, BouncyCastle, OpenJSSE)
-dontwarn okhttp3.internal.graal.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 2. 忽略 Jsoup 的可选依赖 (Google RE2J 正则库)
-dontwarn org.jsoup.helper.Re2jRegex
-dontwarn com.google.re2j.**

# 3. 忽略 Compose Desktop 和 Material 的内部引用警告
# 你的日志显示 DesktopTheme_jvmKt 找不到 MaterialTheme，这通常是因为使用了 Fluent UI 但没引入完整 Material
-dontwarn androidx.compose.desktop.**
-dontwarn androidx.compose.material.**

# 4. 忽略 FlatLaf 的兼容性检查警告 (Java 版本兼容性代码)
-dontwarn com.formdev.flatlaf.**
-dontwarn java.lang.invoke.MethodHandle

# 5. 保持主入口和协程 (防止运行崩溃)
-keep class kotlinx.coroutines.** { *; }

# 1. 保持 JNA 包下所有类和成员原封不动 (解决 dispose 找不到的问题)
-keep class com.sun.jna.** { *; }

# 2. 保持所有继承自 Library 的接口 (JNA 映射必须保持原名)
-keepclassmembers class * extends com.sun.jna.Library {
    public *;
}

# 3. 保持所有本地方法 (Native methods)
-keepclasseswithmembers class * {
    native <methods>;
}

# 4. 如果用了 window-styler，最好也保护一下它
-keep class com.mayakapps.compose.windowstyler.** { *; }


# 1. 保持 Okio 包下所有类不被混淆或移除
# 这是解决 "Bad return type: BufferedSource vs RealBufferedSource" 的关键
-keep class okio.** { *; }
-dontwarn okio.**

# 2. 保持 OkHttp 的核心类 (为了保险起见)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**