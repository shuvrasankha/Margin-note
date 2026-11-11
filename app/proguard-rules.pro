# Room, Hilt, WorkManager and kotlinx.serialization ship their own consumer rules.

# Credential Manager finds its Play services provider by reflection (per the Credential Manager docs).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# PdfBox-Android: the JPEG 2000 codec is an optional extra this app doesn't ship.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
