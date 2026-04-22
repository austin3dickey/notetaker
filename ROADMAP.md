# notetaker roadmap

Feature milestones, tracked as vertical slices — each one builds end-to-end (data → UI → tests)
so the app stays demo-able at every stop. Check items off as they ship.

See `CLAUDE.md` for the full feature spec.

## Current status

M0 and M1 done. **Next up: M2** — note-level undo/redo, color picker, archive, delete.

## Tooling baseline (shared across all milestones)

- Kotlin 2.3.20, Jetpack Compose (BOM 2026.03.01), Material 3
- AGP 9.1.1, Gradle 9.4.1, JDK 17
- `compileSdk` 36, `minSdk` 24
- Room 2.8.4 (KSP 2.3.6)
- MVVM: `ViewModel` + `StateFlow`, repository layer
- Tests: JUnit 4 + Robolectric (JVM), Turbine for Flow, Compose UI tests for screens
  (live in `src/testDebug/` so Robolectric runs them in the CI unit-test job)
- CI: GitHub Actions — lint, unit tests across both debug and release variants (via
  `-PtestBuildType=…`), `assembleDebug`, `assembleRelease`. SHA-pinned actions, grouped
  Dependabot updates with a 7-day cooldown.

## M0 — Project scaffolding ✓

- [x] Gradle project with version catalog
- [x] Empty Compose Activity renders
- [x] `.gitignore`, CI workflow green
- [x] Smoke unit test proves the harness works
- [x] `assembleRelease` in CI (R8 + resource shrinking exercised on every PR)
- [x] Release-variant unit tests in CI so both `BuildConfig.DEBUG` paths are covered

## M1 — Local checklist notes, editable end-to-end

Goal: open the app, create a note, add/check/uncheck/delete items, leave and return, everything persists.

### Data / logic layer ✓

- [x] `Note` + `ChecklistItem` Room entities, DAOs, Robolectric DB tests
- [x] Unique `(noteId, position)` index + `Room.withTransaction` around every item
  mutation so ordering invariants can't break under concurrent writes, and `updatedAt`
  is stamped atomically with the item change
- [x] `id DESC` tiebreaker on note queries for deterministic overview ordering
- [x] `NoteRepository` exposing `Flow<List<Note>>` / `Flow<List<ChecklistItem>>`
- [x] `NoteEditorViewModel` with add/edit/check/delete, `EditorState` split into
      unchecked/checked sections; check/uncheck is idempotent of `position`
- [x] Schema-fallback policy: `fallbackToDestructiveMigration` gated to `BuildConfig.DEBUG`
      so release builds fail loudly rather than wiping notes

### UI layer ✓

- [x] Editor: title field, item rows with checkbox + text field, Enter adds,
      Backspace on blank deletes, X icon deletes (appears on focus)
- [x] Checked items section at bottom, greyed text with line-through
- [x] Long items wrap and stay vertically centered with the checkbox (Row +
      `Alignment.CenterVertically`)
- [x] Overview: list of notes with title + unchecked-item preview
- [x] Navigation (overview ↔ editor) + "new note" FAB
- [x] Compose UI tests on Robolectric (`src/testDebug` — `compose-ui-test-manifest`
      only merges into the debug variant)

## M2 — Local organization

- [x] Note-level undo/redo stack (cap depth, clear on close)
- [ ] Background color picker (locally stored, not synced — see M5)
- [ ] Archive / unarchive (archived are read-only, in their own section)
- [x] Delete note with confirmation dialog (blocked while shared — see M5)

## M3 — Item reordering and indentation

- [ ] Drag item vertically to reorder (within its section only)
- [ ] Drag item horizontally to indent/dedent
- [ ] Edge-safe gesture zone (leave room for OS back-swipe on both sides)
- [ ] Reorder/indent covered by ViewModel tests + Compose UI tests

## M4 — URL detection

- [ ] Detect URLs inside item text at render time
- [ ] Clickable links open in the system browser
- [ ] Editing an item still shows plain text; only rendered (non-edited) items show links

## M5 — Sharing

> **Open design question.** The spec says "does not need a running server; all storage/compute
> on the device side." True device-to-device sync without a server means something like
> Nearby Connections / Bluetooth / Wi-Fi Direct, which limits sharing to users physically
> nearby. Decide the mechanism before starting M5. Candidate approaches:
>
> 1. **Nearby Connections API** — Google Play Services, peer-to-peer, proximity-bound.
> 2. **Self-hosted sync (e.g. Matrix/libp2p)** — contradicts "no server" unless the user runs
>    their own.
> 3. **End-to-end-encrypted relay service** — small server that only stores ciphertext.
>    Arguably still fits "security-focused" if we never hold keys.
>
> Flag to user before implementing.

- [ ] Share a note (pair devices, exchange note + items)
- [ ] Show "shared with" footer on shared notes
- [ ] Push notification when a shared note changes
- [ ] Unshare: both keep a local copy, other side is notified
- [ ] Re-share creates a new synced copy (original local copy untouched)
- [ ] Delete/archive are gated behind "unshare first"

## M6 — Polish, accessibility, hardening

- [ ] Dark mode / dynamic color
- [ ] TalkBack labels and content descriptions on every interactive element
- [ ] Screen-reader ordering for checked section
- [ ] Security review: storage encryption, IPC surface, notification content
- [ ] Performance pass on large notes (1000+ items)
- [ ] Periodic dependency audit (Dependabot handles ongoing bumps; CVE audit is separate)
- [ ] **Freeze schema for release:** destructive fallback is already gated to debug
      builds, but every version bump between now and the first release must add a
      `Migration` so release builds don't fail on open. Enable `exportSchema = true` +
      `room.schemaLocation` KSP arg so migrations can be tested with `MigrationTestHelper`.
- [ ] Release signing config + signed `assembleRelease` in CI (currently unsigned)

## Notes / deferred items

Small things that aren't milestone work but shouldn't get lost. Revisit next time you're
touching the adjacent code.

- **KSP DSL workaround.** `android.disallowKotlinSourceSets=false` in `gradle.properties`
  exists because KSP's Gradle plugin registered generated sources via the legacy
  `kotlin.sourceSets` DSL that AGP 9 rejects. KSP 2.3.6's notes claim improved AGP 9
  integration; next time we touch build wiring, try removing the flag and see if the
  build stays green.
- **Schema export.** `exportSchema = false` today. Flip to `true` alongside
  `room.schemaLocation` when the release-migration story lands (M6), so
  `MigrationTestHelper` can cover v→v+1 rather than the ad-hoc raw-SQLite setup we'd
  otherwise write.
- **BuildConfig-test plumbing.** `testBuildType` is driven by a `-PtestBuildType` property
  because AGP only generates unit-test tasks for one variant at a time. If we add more
  variant-sensitive tests, revisit whether a matrix job in CI scales better than the
  current two-step run.
- **Sharing transport.** M5 is blocked on picking a sync mechanism (see the callout in
  M5). Resolve this before breaking ground on the sharing data model.
- **Release APK is unsigned.** CI builds it, but we can't distribute it. Set up a signing
  config sourced from GitHub secrets before the first beta.
