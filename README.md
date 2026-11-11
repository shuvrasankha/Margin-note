# Jotter

An offline-first notes app for Android. You can back it up and sync it through your Google account. Notes
are stored on the device (Room is the source of truth). Signing in is optional. When you're signed in, notes
sync to the hidden app-data storage in your Google Drive.

The behavior is based on the clean-room spec in [`research/SPEC.md`](research/SPEC.md).

## Features
- Folders: create, rename, delete (the folder's notes move to Recently Deleted). There's a default "Notes"
  folder you can't rename or delete.
- Note list: pinned section on top, then date headers (Today, Yesterday, Previous 7 Days, Previous 30 Days,
  month, year). Each folder has its own sort: date edited, date created or title, either direction (the
  **Sort** button); Settings picks the sort new folders start with. Tap **Pinned** to collapse or expand it.
  Long-press a note to select several, then pin, move or delete them together. Pinning, moving or deleting a
  note doesn't count as editing it. Search covers titles and bodies in
  all folders.
- Editor: the first line is the title. Lines can be heading, subheading, body, monospaced, bulleted,
  dashed or numbered lists, or checklists. Settings has an option to move checked items to the bottom
  of their checklist (stored per device, not synced).
- Math: end a line with `=` and the answer appears next to the cursor; tap it to add it
  (`12 × 4 + 3 =`, `Lunch 18 + 15% =`, `sin(30°) =`, `5! =`). Supports + − × ÷ ^ % !, brackets, `|abs|`, √,
  sin/cos/tan (radians, or degrees with °), log, ln, sqrt, π and e. Name values and functions on one line and
  use them below (`rent = 1200`, `f(x) = x^2 + 1`, then `rent × 12 =`, `f(4) =`); values can also be set in
  a sentence (`Let a=10 and b=2`, `a = 2, b = 3`, `where r = 5 cm`), then `(a+b)^2 =`. Type an equation with one
  unknown (`x^2 + x - 6 = 0`) to get its solutions (up to 4 real ones, found numerically). Type `y = …` or
  `f(x) = …` and tap **Show graph** to plot every function in the note (drag, pinch or double-tap to zoom).
  **Add to note** on the graph saves what's on screen as a picture below the equation.
- Grammar: common mistakes get a blue underline (spelling stays red); tap one to see why and pick a fix.
  Offline rules catch repeated words, a/an, could of, its/it's, your/you're, then/than, their is, he don't,
  missing apostrophes (dont, im), a lowercase i, sentences that don't start with a capital and spaces before
  punctuation. Lines with `=` (math), tags and links are skipped. **Settings > Better grammar checking** (off
  by default) also sends note text to [LanguageTool](https://languagetool.org)'s free API for a full check
  (about 2 seconds after you stop typing, first 18,000 characters). Locked notes are never sent.
- Drawings (**Attach > Drawing**) sit in the note like handwriting: no card or paper, just the ink on the
  page at the width of the text, and only as tall as the ink. Tap one to edit it; while editing the note, a
  small × removes it. Ink follows the theme (black ink shows white on a dark page; colours are stored
  unchanged). The squared-paper grid shows only in the drawing editor.
- Reminders: **More > Remind me…** in a note. Pick in 1 hour, this evening, tomorrow morning, next week, or
  a date and time. The reminder shows under the note's date (tap it to change or remove it). At that time a
  notification shows the note's title and first line (just "Locked note" for a locked one); tap it to open
  the note, or use **Snooze 10 min** / **Done**. Reminders stay on the device they were set on (not
  synced), come back after a restart, and are dropped if the note is deleted. Without Android's "Alarms &
  reminders" permission they can arrive a few minutes late; the reminder dialog links to that setting.
- Text from photos (OCR): **Attach > Scan text** takes a photo and reads the text in it; for a photo already
  in the note, open it and tap **Get text from photo**. The text shows in a box to fix before **Add to note**
  puts it in as normal note text (or **Copy** it). It runs on the phone with ML Kit (Latin script), so
  nothing is uploaded. A scan photo isn't kept.
- Handwriting to text (English and বাংলা): write in a drawing, then tap the handwriting button next to
  **Done** (or lasso some strokes and tap it on the selection). Pick **English** or **বাংলা**; the Bengali model
  also reads Latin letters, so mixed notes work there. Fix the text if needed, then **Add to note**; with
  **Remove the handwriting** on, the ink is replaced by the text (one undo brings it back). Runs on the phone
  with ML Kit Digital Ink; each language's model (about 20 MB) downloads once, then it works offline.
- Move a note to another folder with **Move** in the editor (the current folder shows next to it).
- Tags: type `#word` anywhere in a note; while typing, matching tags are suggested above the toolbar.
  Long-press a tag on the folders screen to rename it everywhere. Tags are highlighted in the editor and listed under **Tags** on the
  folders screen with note counts; tap one to see its notes. `#Work` and `#work` are the same tag; `#1` isn't
  a tag. Tags come from the note text, so they sync with the note.
- Recently Deleted: notes there are read-only. Recover a note or delete it for good. Notes are purged
  automatically after 30 days.
- Light and dark theme (follows the system).
- Gestures: swipe a note right to pin or unpin it, swipe left to delete it. Long-press a folder to rename or
  delete it.

- Folders inside folders: long-press a folder for **New folder inside** or **Move**. Deleting a folder deletes
  its subfolders too (their notes go to Recently Deleted).
- Smart folders (**New > New smart folder**): show every note that matches tags, has a checklist, is pinned,
  or was edited recently. They hold no notes themselves.
- Locked notes: **More > Lock note** in the editor. One notes password locks them all; the body is encrypted
  (AES-256-GCM, key from your password) on the device and in Drive, and only the title stays readable.
  Notes unlock until Jotter goes to the background. Settings has Lock now, Change password and fingerprint
  unlock. **There's no password recovery.**

**Not built:** sharing/collaboration, importing from other note apps, password hints.

## Build

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Requires JDK 17+ and Android SDK 37. minSdk is 26.

### Release build
`./gradlew assembleRelease` shrinks the app with R8. Without signing details it produces an unsigned APK.
To sign it, create `keystore.properties` in the project root (it's git-ignored, as are `*.jks` and
`*.keystore`; never commit them):

```
storeFile=path/to/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## How sync works
- Storage is Google Drive REST v3, `appDataFolder` only. The scope is `https://www.googleapis.com/auth/drive.appdata`.
  Jotter can't see your other Drive files, and you can't see Jotter's files in the Drive UI.
- Each note is its own file, `note-<uuid>.json`. Every folder is in a single `manifest.json`. Each record
  has `id` (UUID), `updatedAt` and `deleted` (a tombstone). Each file also carries `updatedAt` in its Drive
  `appProperties`, so a sync only downloads files that actually changed.
- Conflicts use last-write-wins on `updatedAt`. If both copies changed since the last sync, the newer one
  wins and the other is kept as a separate note titled `<title> (conflict)`. Folders have no body to keep,
  so they use plain last-write-wins.
- Sync runs on app start, 5 seconds after your last edit (debounced) and every 6 hours (WorkManager). All
  three need a network connection. Edits made offline wait in the queue: any record whose `updatedAt`
  differs from the last synced version gets sent on the next successful sync.
- Settings has your account, **Sync now**, the last sync time, and **Restore from Google account**. Restore
  copies the Drive versions over the ones on this device and keeps notes that exist only on this device.

## Google Cloud setup (one time)

Use your own Google Cloud project. **Never commit real IDs, keys or `google-services.json` files.** Jotter
doesn't need `google-services.json` at all.

1. **Create or select a project** at <https://console.cloud.google.com/>.
2. **Enable the Google Drive API:** *APIs & Services → Library → Google Drive API → Enable*.
3. **Configure the OAuth consent screen** (*Google Auth Platform → Branding / Audience*):
   - App name: `Margin`, plus a support email.
   - Add the scope `https://www.googleapis.com/auth/drive.appdata` under *Data access*.
   - While the app is in *Testing*, add your Google account under *Test users*.
4. **Get your signing certificate's SHA-1.** For the debug key:
   ```
   keytool -list -v -alias androiddebugkey -storepass android \
     -keystore ~/.android/debug.keystore | grep SHA1
   ```
   You'll see something like `SHA1: AA:BB:CC:...` (use your own value).
   Repeat this with your release keystore when you ship, and add a second Android client for it.
5. **Create an Android OAuth client:** *Clients → Create client → Android*
   - Package name: `com.margin.app` (and `com.margin.app.debug` for debug builds, as a separate client)
   - SHA-1: the value from step 4
   - Nothing from this client goes into the app. Google matches it by package and signature.
6. **Create a Web OAuth client:** *Clients → Create client → Web application*. Credential Manager needs
   its client ID as the `serverClientId`. Copy the ID, which looks like
   `123456789012-xxxxxxxx.apps.googleusercontent.com`.
7. **Give the Web client ID to the build, outside the repo.** Add it to `~/.gradle/gradle.properties`:
   ```
   JOTTER_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
   ```
   Without it, the app still builds and works fully offline, but sign-in fails.

Then run the app, open **Settings → Sign in with Google**, and approve Drive access when asked.

## Project layout
```
app/src/main/java/com/shuva/jotter/
  data/   Room entities + DAOs, NoteRepository (all local edits), SyncPrefs
  sync/   SyncMerge (pure merge rules), SyncEngine, Drive REST client, auth, WorkManager worker
  ui/     Compose screens + ViewModels
app/src/test/   sync merge, editor, note-list, tag, folder-tree, smart-filter and encryption tests
app/schemas/    exported Room schemas (v1–v3; the migrations are in data/Db.kt)
research/       Phase 1 clean-room spec (the analyzed database copy is git-ignored)
```
