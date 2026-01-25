# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Keep ObjectBox
-keep class io.objectbox.** { *; }
-keepclassmembers class * {
    @io.objectbox.annotation.Entity <fields>;
}

# Keep LangChain4j
-keep class dev.langchain4j.** { *; }

# Xmov Digital Human SDK
-keep public class com.xmov.metahuman.sdk.data.* { *; }
-keep public class com.xmov.metahuman.sdk.impl.data.* { *; }
-keep public class com.xmov.metahuman.sdk.impl.transport.http.* { *; }
-keep public interface com.xmov.metahuman.sdk.IXmovAvatar { *; }
-keep class com.xmov.metahuman.sdk.IXmovAvatar$Companion { *; }
-keep public interface com.xmov.metahuman.sdk.IAvatarListener { public protected *; }
-keep public interface com.xmov.metahuman.sdk.PreCacheListener { public protected *; }

# Aliyun NUI SDK (ASR)
-keep class com.alibaba.idst.nui.* { *; }

# ===========================================
# 数据模型保护 (Gson/Retrofit 序列化必需)
# ===========================================
# 保持所有 data model 类，防止混淆导致 JSON 解析失败
-keep class com.soulmate.data.model.** { *; }
-keep class com.soulmate.data.model.llm.** { *; }
-keep class com.soulmate.core.data.memory.MemoryEntity { *; }

# Gson 特定规则
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Retrofit 规则
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# OkHttp 规则
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }