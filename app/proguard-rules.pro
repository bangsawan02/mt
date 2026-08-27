# Project specific ProGuard / R8 rules
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# APK signing tool rules
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Keep models & ViewModel state
-keepclassmembers class * implements java.io.Serializable { *; }
-dontwarn com.example.**

