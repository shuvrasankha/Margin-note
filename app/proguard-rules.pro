# Room, Hilt, WorkManager and kotlinx.serialization ship their own consumer rules.

# Credential Manager finds its Play services provider by reflection (per the Credential Manager docs).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# PdfBox-Android: the JPEG 2000 codec is an optional extra this app doesn't ship.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder

# ML Kit (text recognition, handwriting, scanning) creates its components by reflection from manifest
# metadata. R8 full mode drops constructors it never sees called, which left ML Kit uninitialised in release
# builds ("NoSuchMethodException: ...Registrar.<init>", then a crash or a null error). Keep them.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# ML Kit Digital Ink ships a shared java.util.Random subclass whose setSeed() is allowed only during
# construction, guarded by a field. R8's field optimisation broke that guard, so the handwriting model manager
# crashed on creation ("Setting the seed on the shared Random object is not permitted"). Leave such fields alone.
-keepclassmembers class * extends java.util.Random { <fields>; }
