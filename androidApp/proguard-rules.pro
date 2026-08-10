# kotlinx.serialization keeps its generated serializers via @Serializable companions.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.elozino.splitify.**$$serializer { *; }
-keepclassmembers class com.elozino.splitify.** {
    *** Companion;
}
-keepclasseswithmembers class com.elozino.splitify.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor client engines are resolved reflectively through ServiceLoader.
-keep class io.ktor.client.engine.okhttp.** { *; }
-dontwarn org.slf4j.**
-dontwarn io.ktor.**

# Koin reflection over constructors.
-keepclassmembers class * {
    public <init>(...);
}
