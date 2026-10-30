# LibreTok Proguard Rules

# ViewModel & Android Lifecycle
-keep class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(...);
}
-keep class com.libretok.downloader.ui.viewmodel.** { *; }

# Gson & Models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.libretok.downloader.data.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Media3 ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.common.** { *; }
-dontwarn androidx.media3.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Compose
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }
-dontwarn androidx.compose.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

