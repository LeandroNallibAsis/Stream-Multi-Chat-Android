# StreamMultiChat ProGuard / R8 Release Optimization Rules

# 1. Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class * extends kotlinx.serialization.internal.GeneratedSerializer { *; }

# 2. Core & Platform Data Models (Serialization and JSON deserialization targets)
-keep class com.streammultichat.core.model.** { *; }
-keep class com.streammultichat.platform.kick.** { *; }
-keep class com.streammultichat.platform.youtube.** { *; }
-keep class com.streammultichat.platform.starvios.** { *; }
-keep class com.streammultichat.platform.twitch.** { *; }
-keep class com.streammultichat.app.data.preferences.** { *; }

# 3. OkHttp & Okio (WebSocket & HTTP streaming engine)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 4. Coil Image Loading
-keep class coil.** { *; }
-dontwarn coil.**

# 5. Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# 6. Android Jetpack Navigation & ViewModel
-keep class androidx.navigation.** { *; }
-keep class androidx.lifecycle.ViewModel { *; }
