# Kable BLE
-keep class com.juul.kable.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.gopro.rebuild.**$$serializer { *; }
-keepclassmembers class com.gopro.rebuild.** {
    *** Companion;
}
-keepclasseswithmembers class com.gopro.rebuild.** {
    kotlinx.serialization.KSerializer serializer(...);
}
