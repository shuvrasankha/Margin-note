# Jotter — Behavioral Spec (Phase 1, clean room)

Everything here is written in my own words from REA observations. No decompiled code, pseudocode,
assembly, UI strings, icons or other assets are reproduced. Where a UI string served as evidence it is
cited by its REA string address, not quoted.

Target studied: `/System/Applications/Notes.app/Contents/MacOS/Notes` (arm64e slice, sha256 `51095ac4…de87c`)
and a copy of the local note store at `jotter/research/NoteStore.sqlite` (+ WAL; sha256 `ef32cfc1…0716e`).
The original store was never opened or written. The copy is git-ignored (`research/.gitignore`).

## Evidence index

| Tag | REA tool | Evidence ID | What it shows |
|---|---|---|---|
| **E1** | inspect_sqlite_database (inventory) | `ev_ff63b738b4a2…` | 15 tables, 49 indexes, no views/triggers |
| **E2** | inspect_sqlite_database `Z_PRIMARYKEY` | `ev_343c118be3a3…` | Entity names and their inheritance (Z_SUPER) |
| **E3** | inspect_sqlite_database `ZICNOTEDATA` | `ev_4229f68b3a62…` | Note body table schema |
| **E4** | inspect_sqlite_database `ZICCLOUDSYNCINGOBJECT` | `ev_0e31ea593a46…` | 215-column shared table schema and indexes |
| **E5** | inspect_sqlite_database, structural columns only, 120-row sample | `ev_ba76bd6a0c46…` | Flag and timestamp distributions per entity |
| **M1** | inspect_macho (deps view `ev_690affb2e9b0…`) | `ev_2fcdace8b976…` | 106 linked libraries |
| **C1–C5** | get_objc_classes `Pin` / `Checklist` / `Search` / `Folder` / `Sort` | `ev_05447169…` / `ev_1ff03a25…` / `ev_2f476bdb…` / `ev_e34e9210…` / `ev_54fff165…` | Class names per feature |
| **S1** | search_strings, deletion/trash terms (page `ev_977c81ef…`) | `ev_984e07c13e9e…` | 97 strings |
| **S2** | search_strings, sort/pin terms (page `ev_084e1f68…`) | `ev_c37371eaee6c…` | 158 strings |
| **S3** | search_strings, paragraph styles/checklist (page `ev_2185b9ab…`) | `ev_38787079c1f0…` | 44 strings |
| **E6** | inspect_sqlite_database, `Z_ENT`/inline-type/text-presence/note-link columns, full table (194 rows) | `ev_7dbdf3d7ac27…` | 6 inline hashtag occurrences (entity 9, hashtag inline type), each linked to a different note; 4 hashtag rows (entity 8) |
| **E7** | inspect_sqlite_database, same rows; tag text compared by shape only, never copied | `ev_4a30eb54e674…` | Each hashtag row has display text plus a standardized key that is its uppercase form; each inline occurrence has alt text starting with '#' |
| **E8** | inspect_sqlite_database, `Z_ENT`/folder type/smart query/parent/lock columns, full table (194 rows); query text compared by shape only | `ev_fa343fd54fc5…` | 15 folders: 11 type 0, 1 type 1, 3 type 2 with a JSON query each, all shaped `{"entity":<str>,"type":{"and":[…]}}`; no folder has a parent; no row is password-protected; the account row has a locked-notes mode value |
| **L1** | *Not REA.* `sqlite3` + a small Python walker on a second scratch copy | — | Gzip header, folder-type names, smart-folder query shape, protobuf field shape (field numbers and counts only, no text) |

REA's SQLite tool cannot filter rows or slice blobs, so I used L1 for those checks. **Any claim backed only
by L1 is marked [unverified]**, as the rules require.

Confidence labels: *observed* = REA saw it directly. *derived* = follows from REA facts by naming or structure.
*[unverified]* = REA could not confirm it.

---

## 1. Feature list (core → nice-to-have)

| # | Feature | In Jotter v1? | Evidence |
|---|---|---|---|
| 1 | Notes with a rich-text body; the first line is the title | yes | E3, E4 (`ZTITLE1`, `ZSNIPPET`, `ZNOTEDATA`); S3 |
| 2 | Folders, plus a default folder that can't be renamed | yes | E2 (`ICFolder`), E5, S1 @0x1004f31e0 |
| 3 | Recently Deleted: soft delete, recover, delete forever, auto-purge after 30 days | yes | S1 @0x1004e0282, @0x1004e55ff, #61, #63 |
| 4 | Note list sorted by date edited, with a pinned section on top | yes (date edited kept separate from the sync time since schema v2, see 3.3) | S2 #12–13, #16, #127 |
| 5 | Pin/unpin notes | yes | E4 `ZISPINNED`; S2 #134–135 |
| 6 | Search across titles and bodies | yes | C3; M1 (CoreSpotlight) |
| 7 | Paragraph styles: title, heading, subheading, body, monospaced | yes (subheading and monospaced added after v1) | S3 #9–13 |
| 8 | Lists: bulleted, dashed, numbered, checklist | yes (dashed added after v1) | S3 #14; L1 [unverified] |
| 9 | Checklist done-state, option to auto-sort checked items to the bottom | yes (auto-sort added after v1; exact rules [unverified], see 3.6) | S3 #3–5; E4 `ZHASCHECKLIST*` |
| 10 | Per-folder sort override (date edited / date created / title, asc/desc) | yes (added after v1, schema v2; title key [unverified], see 3.3) | C5; S2 #36, #77–79, #131; E4 `ZCUSTOMNOTESORTTYPEVALUE` |
| 11 | Date-grouped list headers (e.g. by period) | yes (added after v1; the periods are a Jotter choice [unverified], see 3.3) | E4 `ZDATEHEADERSTYPE`; S2 #97 |
| 12 | Nested folders | yes (added later, schema v3, see 3.10) | E4 `ZPARENT`, `ZPARENTMODIFICATIONDATE`; E5, E8 (no sampled folder has a parent) |
| 13 | Tags (#hashtags); smart folders | yes (tags added after v1 with no schema change, see 3.8; smart folders schema v3, see 3.9) | E2 `ICHashtag`; E6, E7, E8; C4 SmartFolder* classes; E4 `ZSMARTFOLDERQUERYJSON` |
| 14 | Attachments, drawing, scans, OCR | no (out of scope) | E2 `ICAttachment`, `ICMedia`; M1 PencilKit/PaperKit/VisionKit; E4 `ZOCRSUMMARY` |
| 15 | Locked notes | yes (added later, schema v3, see 3.11) | E4 `ZISPASSWORDPROTECTED`, `ZCRYPTO*`, `ZPASSWORDHINT`, `ZLOCKEDNOTESMODE`; E8; M1 LocalAuthentication |
| 16 | Sharing/collaboration | no (out of scope) | E1 `ZICINVITATION`, `ZICNOTEPARTICIPANT` |
| 17 | Cloud sync (their implementation: CloudKit + CRDT merge) | Jotter uses its own Drive design | M1 CloudKit; E4 `ZSERVERRECORDDATA`, `ZMERGEABLEDATA*` |

## 2. Data model

### 2.1 What the source store does (observed)
- It's a Core Data store (*observed*: `Z_PRIMARYKEY`/`Z_METADATA` tables, E1; CoreData.framework linked, M1).
- Most entities share one table, `ZICCLOUDSYNCINGOBJECT`, and `Z_ENT` tells them apart (E2, E4).
  Entities with that base include Account, Folder, Note, Attachment, Media, Hashtag and InlineAttachment.
  Account and Folder both inherit from a "note container" entity (E2: Z_SUPER = 13).
- Every row has a unique string identifier (`ZIDENTIFIER`, unique index, E4). *Derived:* this is the stable sync ID.
- Note bodies live in a separate 1:1 table, `ZICNOTEDATA` (`ZNOTE` FK, `ZDATA` blob, crypto IV/tag columns for
  locked notes) (E3).
- Columns with numeric suffixes (`ZCREATIONDATE1..3`, `ZMODIFICATIONDATE1`, `ZTITLE1/2`) are per-entity columns
  packed into the shared table. *Derived from E5:* for Note rows, `ZCREATIONDATE3`, `ZMODIFICATIONDATE1` and
  `ZFOLDERMODIFICATIONDATE` are the populated date columns.

### 2.2 Body encoding
- [unverified, L1] All 63 non-null bodies start with the gzip magic bytes. Decompressed, they're protobuf:
  a document wrapping a note, which holds (a) the plain text string, (b) CRDT merge records (replica/clock
  pairs), and (c) a sequence of *attribute runs*. Each run has a length and an optional paragraph-style
  sub-message.
- [unverified, L1] Paragraph-style codes seen across runs: absent (most runs, i.e. plain body), 0, 1, 4, 100, 101,
  102, 103. Only code 103 carries a nested done flag, so 103 = checklist. The first run is code 0 in 47 of 63
  notes, so 0 = title. Mapping the others (1 heading, 4 monospaced, 100/101/102 bulleted/dashed/numbered) to
  the style names REA found (S3 #9–14) is my inference; REA didn't tie numbers to names.
- **Jotter doesn't reuse this format.** Importing is out of scope, and Jotter needs plain JSON for Drive.

### 2.3 Timestamps
- Declared `TIMESTAMP`, stored as floats (E4, E5). Sampled note modification values run from about 7.06e8
  to 7.57e8 (E5).
- *Derived:* these are seconds since 2001-01-01 UTC (the Core Data reference date). That puts the sample
  between 2023 and 2024, which fits. Jotter will use epoch milliseconds (UTC), so this doesn't matter for v1.

### 2.4 Jotter model (built from the behaviors above, not copied)

```
Folder  { id: UUID, name, kind: USER | DEFAULT, createdAt, updatedAt, deleted: Boolean,
          sortBy: EDITED | CREATED | TITLE, sortAscending,    // v2
          parentId: UUID?, smartFilter: SmartFilter? }         // v3: nesting; non-null filter = smart folder
Note    { id: UUID, folderId, title (derived), body (block JSON), isPinned,
          createdAt, updatedAt, editedAt,          // v2: editedAt = last text change; updatedAt = any change (sync)
          trashedAt: Long?,                        // non-null = in Recently Deleted
          deleted: Boolean,                        // tombstone for sync, set after purge / delete-forever
          cipher: String? }                        // v3: locked note's sealed body; blocks then hold only the title
SmartFilter { tags: [tag key], allTags, hasChecklist, pinnedOnly, editedWithinDays? }   // v3
Block   { type: TITLE | HEADING | BODY | BULLET | NUMBERED | CHECK, text, checked? }
SyncState { lastSyncAt, per-record lastSyncedUpdatedAt (for conflict detection) }
```
- Recently Deleted is a **virtual folder** (`trashedAt != null`), not a row. The source uses a real type-1 folder
  (E5: one type-1 folder; L1: it's the system Recently Deleted folder). A virtual folder keeps the original
  `folderId`, so recovery can put the note back where it came from.
- Every note belongs to exactly one folder. *Derived:* the source has a maintenance task that repairs notes
  with no folder (C4 `ICFixNotesWithoutFoldersLaunchTask`), so a missing folder is an error state there.

## 3. Behaviors

### 3.1 Folders
- There's one default folder. It, and Recently Deleted, can't be renamed (*observed*: S1 @0x1004f31e0, @0x100563b2c).
  **Jotter:** the default "Notes" folder can't be renamed or deleted.
- Folder types observed: 0 = user/default folder, 1 = Recently Deleted, 2 = smart folder (E5 distribution; L1 names
  and query JSON for type 2 [unverified]).
- Normal lists and counts leave out trashed notes. *Observed:* predicates filter on a "deleted or in trash" flag
  per folder and per account (S1 #2, #13).
- Deleting a folder: [unverified]. REA found no evidence of what happens to its notes. **Jotter proposal
  (needs your OK):** move the folder's notes to Recently Deleted, then remove the folder.

### 3.2 Pinning
- Pinned is a per-note boolean (E4 `ZISPINNED`). Pin/unpin works on a multi-selection (S2 #134–135, #34, #102).
- The list shows a pinned section header and a separate header for the other notes (S2 #12–13, #25–29). The
  pinned section only shows when there are pinned notes (S2 #80, #125–126), and the user can collapse it (S2 #63, #93).
- Order inside each section: *derived*. Pinned notes come first, then both groups follow the current sort key
  (S2 #127 "sort descriptors … including pinned notes").
- Pinned notes in Recently Deleted: *derived*. A query takes a flag for including recently deleted notes (S1 #75).
  **Jotter:** no pinned section in Recently Deleted.
- **Jotter, added later:** long-press a note to select it, then tap more notes. The selection can be pinned
  (or unpinned, when every selected note is already pinned), moved to another folder, or deleted. The Pinned
  header collapses and expands on tap; the collapsed state is remembered per list on this device (not synced).
  Where the source draws its controls is [unverified]; these placements are Jotter's.

- **Moving notes (Jotter):** a note can be moved to another folder from the editor or from a multi-selection.
  Moving changes the folder and `updatedAt` (so it syncs) but not `editedAt`. *Derived:* the source has a
  folder link per note (E4 `ZFOLDER`); its move UI isn't REA-verified [unverified].

### 3.3 Sort order
- Available keys: date edited and date created (*observed*: S2 #16–17, #37, #47). Title as a key: [unverified]
  (the matching strings are too generic to tell).
- Each key has a direction (S2 #36, #77–79). Folders can override the global sort (C5 `ICFolderCustomNoteSortType`;
  E4 `ZCUSTOMNOTESORTTYPEVALUE`, which was 0 = no override on all 14 sampled folders, E5). The global default is
  stored in user preferences (S2 #87).
- **Jotter v1:** date edited, newest first, everywhere (your scope).
- **Jotter, added after v1 (schema v2):**
  - Each folder has its own key (date edited, date created, title) and direction, stored on the folder and
    synced with it. A device-local setting picks the sort new folders start with (default: date edited,
    newest first). The source keeps its global default in user preferences (S2 #87); Jotter's version only
    affects new folders, and that is a Jotter choice [unverified].
  - Title as a key is [unverified] in the source (see above); it was added because you asked for it.
  - "Date edited" is a new `editedAt` field that only changes when the text changes. `updatedAt` still changes
    on every change (pin, delete, recover) because sync needs it. The v1 → v2 migration sets
    `editedAt = updatedAt`; a record from an older build (no `editedAt`) falls back to `updatedAt`.
  - Date headers (E4 `ZDATEHEADERSTYPE`, S2 #97 show the source has them; the periods are not REA-verified,
    so these are my choice [unverified]): Today, Yesterday, Previous 7 Days, Previous 30 Days, then the month
    for the rest of this year, then the year. They group by the active date key; title sort, search results
    and Recently Deleted show no date headers. Pinned notes stay in their own section on top.

### 3.4 Recently Deleted retention
- Deleting a note moves it to Recently Deleted. It doesn't destroy it (*observed*: S1 @0x1004e5152, #29).
- On-device retention is **30 days** (*observed*: S1 @0x1004e0282 and @0x1004e55ff). Cloud removal can take
  longer (same string). That part doesn't apply to Jotter.
- A periodic job marks old trashed notes for deletion (S1 #61 `markOldTrashedNotesForDeletionInContext:`).
  The purge may need the app to be running (S1 @0x1004e0282).
- Notes in Recently Deleted **can't be edited** (*observed*: S1 @0x1004f54a0, @0x100563daa).
- Users can recover notes (S1 #63) or empty the whole folder (S1 #19). Trashing can be undone (S1 #88–91).
- A one-time explanatory alert appears the first time a note is moved there (S1 #47, #53, #77).
- Where a recovered note goes: [unverified]. **Jotter:** back to its original folder, or to "Notes" if that
  folder is gone.
- **Jotter:** purge any note where `now - trashedAt >= 30 days`. This runs on app start and inside the 6h
  WorkManager job, and writes a tombstone so the purge reaches Drive.

### 3.5 Search scope
- Search runs on a system full-text index (*observed*: CoreSpotlight linked, M1; `ICSearchIndexer`,
  `ICModernSearchIndexerDataSource`, `ICSearchQueryOperation`, C3).
- Results can include notes, attachments and tables (C3 `NoteSearchResult`, `AttachmentSearchResult`,
  `TableSearchResult`). Matches are highlighted with a pattern-based finder (C3 `ICSearchResultRegexMatchFinder`).
  Search also offers token suggestions (C3 `ICSearchToken`, `ICSearchSuggestion`).
- Indexable text includes title and body, plus OCR and handwriting summaries (E4 `ZOCRSUMMARY`,
  `ZHANDWRITINGSUMMARY`, `ZADDITIONALINDEXABLETEXT`).
- Whether search includes Recently Deleted: [unverified].
- **Jotter v1:** case-insensitive substring match on title and body text, across all folders, excluding
  Recently Deleted. Results reuse the normal list with pinned notes first.

### 3.6 Checklist behavior
- A checklist is a paragraph style with a per-line done flag ([unverified, L1]: code 103 with a nested done field;
  *observed*: a style named checklist exists, S3 #14, #35).
- Each note has derived flags for "has a checklist" and "has unchecked items" (E4 `ZHASCHECKLIST`,
  `ZHASCHECKLISTINPROGRESS`; a launch task recomputes them, C2).
- There's a setting to auto-sort checked items (S3 #3–5). *Derived from the name:* checked items move to the end
  of their list. Exact rules: [unverified]. **Jotter (added after v1):** a device-wide switch in Settings.
  When it's on, checking or unchecking a line stable-sorts that unbroken checklist run so unchecked lines come
  first and checked lines last. Order within each group is kept. This rule is my choice, not REA-verified.
- **Jotter v1:** tap the box to toggle. Enter on a non-empty checklist line starts a new unchecked line. Enter on an
  empty line ends the list. I chose these editing rules for Jotter; REA didn't verify them.

### 3.7 Formatting styles
- Paragraph style names *observed*: title, heading, subheading, body, monospaced, checklist (S3 #9–14, #30–35).
  Bulleted, dashed and numbered list names weren't found by REA string search, but L1 shows three list codes
  [unverified]. Notes can also contain inline emphasis (E4 `ZHASEMPHASIS`).
- The first line is the title. *Derived:* each note has a stored title column and a last-title-change date
  (E4 `ZTITLE1`, `ZDATEFORLASTTITLEMODIFICATION`). The first run of a note is title-styled (L1 [unverified]).
- The list preview is a snippet of the body (E4 `ZSNIPPET`).
- **Jotter v1:** the first line always renders as the title and becomes `Note.title`. Each later line has one
  block type: heading, body, bullet, numbered, or check. Numbered lines number themselves within an unbroken run.
  An empty note gets the title "New Note" (Jotter's own wording).

### 3.8 Tags
- *Observed:* a tag is written inline in the note text, starting with '#' (E7 alt text), and each place it's
  used is linked to its note (E6). Each distinct tag is stored once with its display spelling plus an
  uppercase standardized key (E7). *Derived:* tags match regardless of case, and a tag keeps one display spelling.
- *Not REA-verified:* which characters a tag can contain, where the tag browser sits in the UI, and which
  spelling wins. [unverified]
- **Jotter (added after v1, no schema change):** tags are read from the note text every time, so they need no
  table and sync as part of the note. A tag is '#' plus letters, digits, '_' or '-', with at least one letter,
  and it can't follow a letter, digit or '#' (so "#1", "C#" and "a#b" aren't tags). A trailing '-' isn't part
  of the tag. Matching ignores case. The list shows a tag the way the oldest note using it spells it.
  The folders screen has a Tags section (A to Z, with note counts) under Recently Deleted. Tapping a tag
  lists its notes, newest edit first, with date headers. That list has no Sort button and no new-note
  button. Notes in Recently Deleted don't count. In the editor, tags are drawn in the accent color, semibold.
  All of these are Jotter choices [unverified].
- **Jotter, added later:** while you type `#` plus letters, a bar above the formatting bar offers up to five
  existing tags that start with what you typed; tapping one completes it and adds a space. Long-pressing a tag
  on the folders screen renames it in every note outside Recently Deleted (a text edit, so those notes count
  as edited). Renaming to an existing tag merges the two. Both are Jotter choices [unverified].

### 3.9 Smart folders
- *Observed:* a smart folder is a folder of its own type (type 2) that carries a JSON query; all 3 sampled
  queries are an `and` of conditions over an entity (E8, E4 `ZSMARTFOLDERQUERYJSON`). The source has a
  smart-folder composer (C4). *Not REA-verified:* which conditions it offers, and whether it can also do "or".
  [unverified]
- **Jotter (schema v3):** a smart folder is a folder with a `smartFilter`. It holds no notes; it lists every
  note outside Recently Deleted that matches every condition that's set: tags (any of them, or all of them),
  has a checklist, pinned, edited in the last 7 or 30 days. An empty filter matches every note. Smart folders
  sit at the top level, after the regular folders, with their match count; they have their own sort but no
  new-note button, and notes can't be moved into them. Long-press to edit or delete; deleting one doesn't
  touch any note. Body tags of a locked note are hidden, so a locked note only matches on its title.
  The conditions on offer are Jotter choices [unverified].

### 3.10 Nested folders
- *Observed:* folders have a parent link and a last-parent-change date (E4 `ZPARENT`, `ZPARENTMODIFICATIONDATE`).
  No sampled folder has a parent (E5, E8), so how the source shows or deletes nested folders is [unverified].
- **Jotter (schema v3):** `parentId` (null = top level). The folders screen shows the tree, indented. Long-press
  a folder for "New folder inside" and "Move" (to the top level or into another folder, never into itself or
  its own subfolders). The default folder can hold subfolders but can't move. Deleting a folder deletes its
  subfolders too, and all their notes go to Recently Deleted. A folder's count is its own notes only.
  After a sync, a folder whose parent is gone, is a smart folder, or forms a loop with it (two devices each
  moved one folder into the other) moves to the top level. These are Jotter choices [unverified].

### 3.11 Locked notes
- *Observed:* notes have a password-protected flag, per-note crypto fields (salt, iteration count, IV, tag,
  wrapped key) and a password hint (E4 `ZISPASSWORDPROTECTED`, `ZCRYPTO*`, `ZPASSWORDHINT`); the account has a
  locked-notes mode (E8). The app links LocalAuthentication (M1), so device authentication can unlock. No
  sampled note is locked (E8), so the exact flow is [unverified].
- **Jotter (schema v3):** one notes password for all locked notes, created the first time a note is locked
  (at least 4 characters, entered twice, with a warning that it can't be recovered). Each locked note's body
  (everything after the title line) is sealed with AES-256-GCM under a PBKDF2-HMAC-SHA256 key (120,000
  iterations, per-note salt) and stored as text in `cipher`, so it syncs like any other field and opens on
  any device with the password. The title stays readable: it shows in lists, search and tags; the list shows
  "Locked" instead of a preview, and the body isn't searchable. Entering the password unlocks every locked
  note until Jotter goes to the background. Editor: More > Lock note / Remove lock. Settings: Lock now, Change
  password (re-seals every locked note; a note sealed with some other password is left as it is and counted),
  and "Unlock with fingerprint" when the device has a strong biometric: the password is encrypted with an
  Android Keystore key that needs a fingerprint and is invalidated when fingerprints change; it stays on the
  device. No password hint. These are Jotter choices [unverified].

## 4. UI/UX patterns

Note: this is the macOS app, so mobile gestures aren't observable here.

**Screen inventory** (*observed* class names):
- Folder list / sidebar with a new-folder button (C4 `ICMFolderListController`, `ICMFolderListNewFolderButtonCell`,
  `ICMAddFolderToolbarItem`)
- Note list and note grid with pinned/unpinned headers (S2 #25–29) and a Recently Deleted header (S1 #33)
- Editor with a checklist toolbar item (C2 `ICMChecklistToolbarItem`)
- Search field with tokens (C3 `ICMSearchField`, `ICMTokenizedSearchField`)
- Smart-folder composer (C4). Out of scope.

**Navigation**
- *Derived:* folder → note list → editor. Search filters the note list (C3 `ICMSearchNoteListArrayController`).
- **Jotter (phone):** Folders screen → Note list → Editor, a search bar on the note list, and Settings reached
  from the Folders screen.

**Gestures**
- [unverified] for all of them. Drag-and-drop of notes onto folders exists on Mac (C4 `ICMFolderListDragNoteInfo`).
- **Jotter proposal:** swipe a note row to pin or delete, long-press to rename or delete a folder, and a FAB for
  a new note.

**Empty states**
- [unverified]. REA found no strings for these. **Jotter:** short neutral messages in Jotter's own wording, e.g.
  empty folder, no search results, empty Recently Deleted (with a 30-day explanation).

**Theme**
- [unverified] for the source. **Jotter:** Material 3 light and dark, following the system setting.

## 5. Unknowns and limitations

1. **Not checked by REA (L1 only):** the gzip/protobuf body layout, the numeric style codes, and the folder type
   names. REA's SQLite tool samples rows with no filtering and can't slice blobs.
2. **Shared-cache frameworks weren't analyzed.** Most logic lives in NotesShared, NotesEditor, NotesUI and
   NotesSupport (M1 dependencies, C1–C5 bindings). These sit in the dyld shared cache, not on disk, so
   REA only covered the app executable. Exact algorithms (purge timing, sort tie-breaks, checklist auto-sort)
   are therefore [unverified].
3. **Hopper class/string discovery is partial.** REA reports identity coverage as partial and says missing
   matches don't prove absence (C1–C5, S1–S3 unknowns).
4. **The DB sample is bounded and unordered** (E5: 120 rows, truncated). Of the 109 Note-entity rows in the full
   copy, 46 had no folder and no creation date (L1) [unverified]. Their meaning is unknown, possibly legacy or
   partially synced rows. Jotter doesn't depend on this.
5. **The snapshot isn't atomic.** REA warns the copy wasn't app-quiesced (E1 limitations). That's fine for schema
   work.
6. **No runtime observation.** I didn't launch or instrument Notes. UI behaviors come from class and string
   names only.
7. **No pinned notes and no trashed notes** in this store (L1: all 109 notes unpinned, 0 in Recently Deleted), so
   I couldn't watch those states in the data.

## 6. Decisions I need from you before Phase 2

- 3.1 Deleting a folder moves its notes to Recently Deleted (proposed).
- 3.4 Recovered notes go back to their original folder, or to "Notes" if it's gone (proposed).
- 3.5 Search excludes Recently Deleted (proposed).
- 4 The swipe/long-press/FAB gestures above (proposed).
