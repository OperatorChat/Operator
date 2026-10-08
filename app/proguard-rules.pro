# Trixnity's libolm driver loads native code through JNA; keep both intact.
# (Rules carried over from fenleon/chats, MIT.)
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-keep class de.connect2x.trixnity.libolm.** { *; }

# kotlinx.serialization: keep serializers for Trixnity's event model.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class de.connect2x.trixnity.**$$serializer { *; }
-keepclassmembers class de.connect2x.trixnity.** { *** Companion; }
-keepclasseswithmembers class de.connect2x.trixnity.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class chat.operator.**$$serializer { *; }
-keepclassmembers class chat.operator.** { *** Companion; }
-keepclasseswithmembers class chat.operator.** { kotlinx.serialization.KSerializer serializer(...); }

# Optional Ktor/OkHttp integrations that are not on the classpath.
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
