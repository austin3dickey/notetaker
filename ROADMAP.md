# notetaker roadmap

Feature milestones, tracked as vertical slices — each one builds end-to-end (data → UI → tests)
so the app stays demo-able at every stop. Check items off as they ship.

See `CLAUDE.md` for the full feature spec.

## Tooling baseline (shared across all milestones)

- Kotlin 2.2.10 (bundled with AGP 9), Jetpack Compose (BOM 2026.03.00), Material 3
- AGP 9.1.1, Gradle 9.1.0, JDK 17
- `compileSdk` 36, `minSdk` 24
- Room 2.8.0 for persistence
- MVVM: `ViewModel` + `StateFlow`, repository layer
- Tests: JUnit 4 + Robolectric (JVM), Compose UI tests (instrumented), Turbine for Flow
- CI: GitHub Actions — lint, unit tests, `assembleDebug`

## M0 — Project scaffolding

- [ ] Gradle project with version catalog
- [ ] Empty Compose Activity renders
- [ ] `.gitignore`, CI workflow green
- [ ] One passing unit test to prove the test harness works

## M1 — Local checklist notes, editable end-to-end

Goal: open the app, create a note, add/check/uncheck/delete items, leave and return, everything persists.

- [ ] `Note` + `ChecklistItem` Room entities, DAO, in-memory DB tests
- [ ] `NoteRepository` exposing `Flow<List<Note>>` and `Flow<NoteWithItems>`
- [ ] `NoteEditorViewModel` with add/edit/check/delete/reorder-within-section
- [ ] Editor UI: title field, item rows with checkbox + text field, Enter adds, Backspace on blank deletes, X icon deletes
- [ ] Checked items section at bottom, greyed text, idempotent check/uncheck ordering
- [ ] Long items wrap and center vertically with the checkbox
- [ ] Overview screen: list of notes with title + preview
- [ ] Navigation (overview ↔ editor) + "new note" FAB

## M2 — Local organization

- [ ] Note-level undo/redo stack (cap depth, clear on close)
- [ ] Background color picker (locally stored, not synced — see M5)
- [ ] Archive / unarchive (archived are read-only, in their own section)
- [ ] Delete note with confirmation dialog (blocked while shared — see M5)

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
- [ ] Dependency audit + Dependabot config
- [ ] **Freeze schema for release:** destructive fallback is already gated to debug
  builds, but every version bump between now and the first release must add a
  `Migration` so release builds don't fail on open. Enable `exportSchema = true` +
  `room.schemaLocation` KSP arg so migrations can be tested with `MigrationTestHelper`.
