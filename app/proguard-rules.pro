# Room, Hilt, WorkManager and kotlinx.serialization ship their own consumer rules.

# Credential Manager finds its Play services provider by reflection (per the Credential Manager docs).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
