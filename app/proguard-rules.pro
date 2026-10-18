# kotlinx.serialization : conserve les sérialiseurs générés (sauvegarde JSON, réponses des API).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.katachilabs.shelves.** {
    *** Companion;
}
-keepclasseswithmembers class com.katachilabs.shelves.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ML Kit (scanner de codes-barres Google) : en mode release, R8 retire des classes que la
# bibliothèque retrouve par réflexion, et le scanner plante dès son ouverture (NullPointerException
# dans com.google.mlkit.vision.codescanner.internal). On conserve donc ses classes telles quelles.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
