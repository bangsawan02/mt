# Project specific ProGuard / R8 rules
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep APK signing tool
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**

# Keep models and ViewModel state classes
-keep class com.example.** { *; }
-keepclassmembers class com.example.** { *; }
