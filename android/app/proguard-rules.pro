# ProGuard rules for UHF/VHF Mic
# © 2024 Iwinosa Aigbokhaevbo. All Rights Reserved.

# Keep application class
-keep class com.uhfvhfmic.app.** { *; }

# Obfuscate all other classes
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5

# Keep Kotlin metadata
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep USB classes
-keep class android.hardware.usb.** { *; }

# Keep coroutines
-keepnames class kotlinx.coroutines.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
