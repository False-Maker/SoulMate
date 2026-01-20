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
