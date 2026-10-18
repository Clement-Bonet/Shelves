# kotlinx.serialization : conserve les sérialiseurs générés (sauvegarde JSON, réponses des API).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.katachilabs.shelves.** {
    *** Companion;
}
-keepclasseswithmembers class com.katachilabs.shelves.** {
    kotlinx.serialization.KSerializer serializer(...);
}
