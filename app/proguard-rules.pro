# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools version of proguard-android-optimize.txt.

# Keep data classes for Room
-keep class dev.filips.twistcounter.data.local.** { *; }
-keep class dev.filips.twistcounter.domain.model.** { *; }

# Keep sensor processing classes
-keep class dev.filips.twistcounter.sensor.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}