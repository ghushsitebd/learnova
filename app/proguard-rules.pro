# Learnova release rules: preserve Filament's Java/JNI surface and app entry points.
-keep class com.google.android.filament.** { *; }
-keep class com.learnova.app.** { *; }
-keepclassmembers class * {
    native <methods>;
}
-dontwarn com.google.android.filament.**
