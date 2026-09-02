# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Model classes (used by kotlinx.serialization)
-keep class com.lovebrain.app.model.** { *; }

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lovebrain.app.**$$serializer { *; }
-keepclassmembers class com.lovebrain.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.lovebrain.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Koin
-keep class org.koin.** { *; }

# Compose：AndroidCompositionLocals_androidKt.getLocalLifecycleOwner() 被 R8 移除后，
# lifecycle-runtime-compose 的条件 keep 失效 → LocalLifecycleOwner 注册器丢失 → release 崩溃
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }

# Tink (security-crypto 依赖)：errorprone 注解类运行时不需要，R8 缺失时忽略
-dontwarn com.google.errorprone.annotations.**
