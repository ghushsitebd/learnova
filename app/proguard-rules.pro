# Learnova release rules: preserve Filament's Java/JNI surface and app entry points.
-keep class com.google.android.filament.** { *; }
-keep class com.learnova.app.** { *; }
-keepclassmembers class * {
    native <methods>;
}
-dontwarn com.google.android.filament.**

# AGP/R8 full-mode safety for the WorkManager + Room startup path used
# transitively by the current GMA Next-Gen SDK. WorkManager creates its
# generated Room database implementation reflectively during App Startup.
# Keep only the generated database surface and reflective worker constructors
# instead of retaining the entire AndroidX dependency graph.
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.work.ListenableWorker {
    <init>(...);
}
