# ============================================================
# Android ProGuard / R8 Rules — CalabiYauVoice_GUI
# ============================================================

# ---------- 通用属性 ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses

# ---------- Kotlin ----------
# R8 已内置 Kotlin 支持，无需 -keep class kotlin.**
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ---------- Kotlin Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---------- Kotlin Serialization ----------
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer {
    static **$$serializer INSTANCE;
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
# 保留 app 内所有 @Serializable 生成的 $$serializer（含 commonMain 的 data 包）
-keep,includedescriptorclasses class com.nekolaska.calabiyau.**$$serializer { *; }
-keep,includedescriptorclasses class data.**$$serializer { *; }

# ---------- OkHttp / Okio ----------
# R8 已内置 OkHttp 规则；仅需抑制可选依赖警告
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- Jetpack Compose ----------
# R8 已内置 Compose 规则，无需 -keep class androidx.compose.**
-dontwarn androidx.compose.**

# ---------- App 数据模型 ----------
# 保留 @Serializable 数据类的字段名（JSON 解析依赖字段名）
-keepclassmembers class com.nekolaska.calabiyau.data.** {
    <fields>;
}
-keepclassmembers class data.** {
    <fields>;
}
# 保留 API object 单例（反射/序列化可能用到）
-keepnames class com.nekolaska.calabiyau.data.** {}
-keepnames class data.** {}
