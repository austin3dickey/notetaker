# Room generates code referenced via reflection at runtime.
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# Keep Compose runtime metadata for @Composable functions.
-keepclassmembers class androidx.compose.runtime.** { *; }
