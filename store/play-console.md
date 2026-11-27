# Publishing Margin on Google Play

Everything Play Console asks for, in the order you meet it. The listing text and graphics are in [listing.md](listing.md).

## App facts

| | |
|---|---|
| Package name | `com.margin.note` (it can't change after the first upload; `com.margin.app` was taken). The code's own namespace stays `com.margin.app`. |
| Version | 1.0.1 (versionCode 2). 1.0.0 (versionCode 1) went to internal testing first. |
| Min / target SDK | 26 (Android 8.0) / 37 |
| Upload file | `app/build/outputs/bundle/release/app-release.aab` (`./gradlew bundleRelease`) |
| Upload key | `margin-release.jks` (with `keystore.properties`). Back up both somewhere safe, outside the repo. Losing them means asking Google for an upload key reset. |
| Upload key SHA-1 | `DB:0C:6E:B7:0E:7F:1D:61:D9:35:83:7A:06:D1:8F:97:C3:5D:1C:5B` |
| Upload key SHA-256 | `7A:56:58:BD:8F:AC:5A:AB:0C:16:E8:CD:78:23:85:34:E4:EE:44:83:86:C3:AE:70:A0:16:B9:14:7E:E9:8A:E5` |
| Play app signing key SHA-1 | `B3:72:C8:43:93:57:5A:36:86:A4:6D:FD:7A:C9:C1:28:9F:68:4E:96`, read from the Play-installed APK with `apksigner verify --print-certs`. Don't trust the copy buttons in Play Console's App signing page: they gave `59:17:…:1F:72`, which matches no key Play signs with. |
| Play app signing key SHA-256 | `65:03:A1:EE:75:E3:7B:3E:40:9E:50:FF:27:16:C2:51:6F:6E:18:39:4D:5F:11:29:E5:E2:B6:46:4E:C4:AC:00` |

For every later release, raise `versionCode` (and `versionName`) in `app/build.gradle.kts`.

## 1. Create the app

Play Console → Create app:
- App name: `Margin: Notes & Checklists`
- Default language: English (United States)
- App or game: **App**
- Free or paid: **Free** (a free app can never become paid later)
- Accept the declarations.

## 2. Store settings

- App category: **Productivity**
- Tags: Notes, Productivity, To-do list (choose from Play's list)
- Contact email: `shuvrasankha1@gmail.com`
- Website: `https://shuvrasankha.github.io/Margin-note/`
- Phone: leave it empty

## 3. App content (Policy → App content)

### Privacy policy
`https://shuvrasankha.github.io/Margin-note/privacy.html`

### App access
**All functionality is available without special access.** There's no login. Google sign-in is only needed for the optional Drive backup, and Ask AI needs the user's own key. Reviewers can test everything else without them.

### Ads
**No, my app does not contain ads.**

### Content rating (IARC questionnaire)
- Email: `shuvrasankha1@gmail.com`
- Category: **All other app types** (a utility or productivity app, not a game, social or communication app)
- Violence, sexuality, language, controlled substances, gambling and crude humour: **No** to all
- Can users interact with or exchange content with other users? **No**. Sharing a note hands it to another app chosen by the user, and nothing is shared inside Margin.
- Does the app share the user's current physical location with other users? **No**
- Does the app allow users to buy digital goods? **No**
- Is the app a web browser or search engine? **No**. Web link previews only load the page the user pasted.
- Expected result: Everyone / PEGI 3 / USK 0.

### Target audience and content
- Age groups: **13–15, 16–17 and 18 and over**. Don't choose under 13: the privacy policy says the app isn't for children, and choosing under 13 brings in the Families policy.
- Could the app unintentionally appeal to children? **No**

### News app: **No**
### COVID-19 contact tracing or status app: **No**
### Government app: **No**
### Financial features: **My app doesn't provide any financial features**
### Health apps: **My app does not have any health features**. Habits are general tick-offs, not health tracking.

### Data safety
Choose these answers. They match the privacy policy, and Play checks the two against each other.

**Overview**
- Does your app collect or share any of the required user data types? **Yes**
- Is all of the user data collected by your app encrypted in transit? **Yes**. Drive, WebDAV over HTTPS, AI providers and LanguageTool all use HTTPS.
- Which of the following methods of account creation does your app support? **My app does not allow users to create an account**
- Do you provide a way for users to request that their data is deleted? **Yes**. Data is deleted in the app or by uninstalling it, and Drive data from Drive's "Manage apps". Data deletion URL (if asked): the privacy policy URL.

**Data types.** Mark each as *Collected*, *Not shared*, *Optional* (users can choose), with the purpose **App functionality**, and *not processed ephemerally* unless it says so below.

| Category → type | Why |
|---|---|
| App activity → **Other user-generated content** | Note text goes to the AI provider the user picks (Ask AI), to LanguageTool if cloud grammar is on, and to the user's own Drive or backup place. Processed ephemerally: No. |
| Personal info → **Email address** | Received from Google sign-in for Drive backup and shown on the phone. Purpose: App functionality and **Account management**. |
| Personal info → **Name** | Same as email. |
| Photos and videos → **Photos** | Photos in notes are copied to the user's Drive or backup place. |
| Audio → **Voice or sound recordings** | Voice notes are copied to the user's Drive or backup place. |
| Files and docs → **Files and docs** | Attached files and PDFs are copied to the user's Drive or backup place. |
| Calendar → **Calendar events** | Only if the user turns on "Add to phone calendar". The event is written to the phone's calendar, which may sync it to the user's account. |

**Not collected:** location (place reminders keep it on the phone; the current spot is not looked up as an address, and only typed place searches go to the phone's geocoder), contacts, financial info, health, messages, web history, app info and performance, device or other IDs.

Why "Not shared": Google counts transfers that the user starts and expects (sending a note to the AI provider they chose, or a backup to their own Drive) as not sharing. If you'd rather be stricter, also tick **Shared** for "Other user-generated content" for Ask AI. That's also accurate.

### Advertising ID
**No**. The app doesn't use the advertising ID. If Play warns about `AD_ID`, check the merged manifest of the AAB. No dependency should add it, but if one does, add `<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove"/>`.

## 4. Permission declarations

Play Console shows these forms once the AAB is uploaded (App content → Sensitive permissions, or on the release page).

### Foreground service: not used since 1.0.1
1.0.0 recorded voice notes in a microphone foreground service, which needs a declaration with a video. From 1.0.1 (versionCode 2) there's no foreground service: leaving Margin while recording stops the recording and adds it to the note. So the declaration goes away once no active track has the 1.0.0 bundle (put 1.0.1 on internal and closed testing). The plain `FOREGROUND_SERVICE` permission in the merged manifest comes from WorkManager and needs no form.

### Full-screen intent: `USE_FULL_SCREEN_INTENT`
- Use case: **Alarms set by the user**. Reminders can be set to ring as an alarm, and the full-screen alert shows them when the phone is locked.
- Play grants this by default only to calling and alarm-clock apps. If Google turns the declaration down, it's still safe: the app checks `canUseFullScreenIntent()`, falls back to a normal heads-up notification, and offers a button that opens the system setting. In that case, remove nothing and answer that it's not core functionality.

### Exact alarms: `SCHEDULE_EXACT_ALARM`
No form needed. It's the user-granted version (not `USE_EXACT_ALARM`). The app asks the user to allow it and still works without it, with reminders that may arrive a few minutes late.

### Location: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
Background location needs the **Location permissions** declaration (App content → Sensitive permissions) before a release that requests it can be reviewed.
- Core feature: **place reminders**. A note can remind you when you arrive at or leave a place, including when Margin is closed, which needs location all the time.
- In-app disclosure: the prompt before the system one (`place_location_background_explain` in `strings.xml`). It says why, that it works when the app is closed, and that the location stays on the phone.
- Video: a short screen recording that sets a place reminder, shows that prompt, chooses "Allow all the time" and shows the reminder ringing on arrival. Upload it unlisted (for example on YouTube) and paste the link.
- Data safety stays "location not collected": it never leaves the phone. The privacy policy names the permission (Permissions → Location).

### Calendar: `READ_CALENDAR`, `WRITE_CALENDAR`
No declaration form. It's covered by the Data safety answers and the privacy policy, and it's only requested when the user turns on "Add to phone calendar".

## 5. Google sign-in (Drive backup) set-up. Do this before release.

Play re-signs the app with its own **app signing key**, so Google sign-in from a Play install fails until Google Cloud knows that key.

Already done on 19 Nov 2026 in the Google Cloud project "My First Project" (`midyear-node-233902`):
- Android client **Margin release (com.margin.note)**, with the upload key's SHA-1.
- Android client **Margin debug (com.margin.note.debug)**, with the debug key's SHA-1 `D1:C2:97:5A:08:DD:0A:C1:E1:7B:A4:B0:ED:E9:41:21:DA:52:9B:81`.
- `drive.appdata` is declared under Data access. Google treats it as a non-sensitive scope.
- Branding (name, home page, privacy and terms links, `shuvrasankha.github.io`) is filled in, and the publishing status is **In production**.
- Android client **Margin Play signing (com.margin.note)**, with the Play app signing SHA-1 `B3:72:C8:…:4E:96` (fixed on 19 Nov 2026; it first had a wrong one and sign-in failed).
- Android client **Margin Play quantum-ready key (com.margin.note)**, with SHA-1 `8A:59:…:D7:42` from the same copy buttons. It's probably not a real key and can be deleted.
- The old `com.margin.app` clients (Android client 1 and 2) are kept for the builds already installed.

Steps 1 to 4 below are done. Optionally, also submit the branding for verification so the logo and name show on the consent screen. That needs `shuvrasankha.github.io` verified in Google Search Console.

1. After the first upload: Play Console → Test and release → **App integrity** → App signing. Copy the **App signing key certificate SHA-1**.
2. Google Cloud Console (the project that has the Drive API): APIs & Services → Credentials → **Create credentials → OAuth client ID → Android**. Package `com.margin.note`, with that SHA-1. Keep the existing client for the upload key (SHA-1 above) and the debug one too.
3. APIs & Services → **OAuth consent screen**:
   - App name Margin, support email, logo `store/icon-512.png`;
   - Home page `https://shuvrasankha.github.io/Margin-note/`;
   - Privacy `…/privacy.html`, Terms `…/terms.html`;
   - Authorised domain `shuvrasankha.github.io`;
   - Scope `…/auth/drive.appdata`.
4. Publishing status: **Push to production**. While it's in "Testing", only listed test users can sign in, and their tokens expire after 7 days. Google may ask to verify the brand (home page, privacy link, domain ownership via Search Console). `drive.appdata` doesn't need the paid security assessment.

## 6. Testing and release

Status on 19 Nov 2026: 1.0.1 is on internal testing, and the closed test (Alpha, 5 countries, the "Alpha testers" list) was sent for review. Play's pre-check flagged `android.hardware.screen.portrait`, which ML Kit's document scanner implies. It was acknowledged as "plans to resolve", and the manifest now marks it as not required from the next build (versionCode 3).

- **New personal developer account?** Accounts made after 13 Nov 2023 must run a **closed test with at least 12 testers, opted in for 14 days in a row**, before they can apply for production. Start this first, since it's the longest wait.
  - Test and release → Testing → Closed testing → Create track.
  - Upload the AAB, add testers by email list or Google Group, and share the opt-in link.
- Internal testing (up to 100 testers, no review wait) is the quickest way to check the Play-signed build on your own phone. Check Drive sign-in there after step 5.
- Countries: all, or choose your own.
- Production: after the closed test, Dashboard → Apply for production. Answer the questions about the test, then create the production release with the same AAB (or a newer versionCode) and the release notes from listing.md.
- The first review usually takes a few days to a week.

## 7. Before you press send: check list

- [ ] Install the release AAB on a real phone (internal testing) and try:
  - [ ] write a note;
  - [ ] a reminder with an alarm and with calendar;
  - [ ] a voice note;
  - [ ] the app lock;
  - [ ] Drive sign-in.
- [ ] Back up `margin-release.jks` and `keystore.properties` off this computer.
- [ ] Play app-signing SHA-1 added to Google Cloud. OAuth consent screen in production.
- [ ] Privacy policy URL opens (it's live on GitHub Pages).
