-keep class com.example.musicplayer.data.** { *; }
-keep class com.example.musicplayer.network.** { *; }
-keep class com.example.musicplayer.service.** { *; }
-keep class com.example.musicplayer.viewmodel.** { *; }
-keep class com.example.musicplayer.ui.** { *; }

-keepattributes Signature
-keepattributes *Annotation*

-dontwarn okhttp3.**
-dontwarn com.google.android.exoplayer.**
-dontwarn androidx.compose.**

-keepclassmembers class * implements androidx.lifecycle.ViewModel {
    public <init>(android.app.Application);
}