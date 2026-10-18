# kotlinx.serialization : conserve les sérialiseurs générés (sauvegarde JSON, réponses des API).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class fr.shelves.** {
    *** Companion;
}
-keepclasseswithmembers class fr.shelves.** {
    kotlinx.serialization.KSerializer serializer(...);
}
