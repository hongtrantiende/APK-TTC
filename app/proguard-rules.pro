# Rhino JS Engine — keep all classes for JS interop
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# Extension models — used via reflection in JS bridge
-keep class com.nam.novelreader.extension.runtime.api.** { *; }
-keep class com.nam.novelreader.extension.model.** { *; }

# Room entities
-keep class com.nam.novelreader.data.local.entity.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
