# Potato Player — Native-First Engineering Constitution

This document governs all AI-assisted changes to this codebase.
Read it before making any architectural decision.

---

## The 7 Laws

### 1. NATIVE FIRST

Use Android, Compose, Kotlin, and MPV capabilities before writing custom code.
The question is always: *does the platform already do this?*

### 2. ONE OWNER

Every behavior has exactly one authoritative owner.
If two classes can both affect the same thing, that is an architectural defect.

### 3. NO DUPLICATION

Never recreate functionality already provided by Android, Compose, Kotlin, or MPV.
Custom code that reimplements a platform API is technical debt from day one.

### 4. MINIMAL ABSTRACTION

A class must have a real, distinct responsibility.
File size alone is not justification for extraction.
Complexity alone is not justification for a new manager/controller/handler.

### 5. THIN MPV BOUNDARY

Only `MpvWrapper` talks to raw MPV APIs.
No class above `MpvWrapper` reads or writes raw MPV property strings.
All MPV interactions go through named methods: `setVolume()`, `seekTo()`, `setVideoZoom()`.

### 6. NO SYMPTOM PATCHING

Fix ownership and lifecycle problems at their source.
Do not add flags, callbacks, or managers to work around a structural problem.
If you need a workaround, the architecture is wrong.

### 7. PROVE BEFORE REFACTORING

Read the complete file before touching it.
Search all usages before deleting or merging anything.
Compile and verify after each meaningful change.
Re-audit ownership after the change.

---

## Ownership Map

```
Android
├── Activity
├── Window / WindowInsets
├── Lifecycle (ON_RESUME, ON_STOP, etc.)
├── Surface / SurfaceHolder
├── requestedOrientation
├── PiP
└── BackHandler (via Compose)

Compose / PlayerScreen
├── UI rendering
├── Gesture detection (pointerInput, detectTapGestures, etc.)
├── UI-only state (remember / rememberSaveable)
├── Dialogs and overlays
└── Controls visibility

PlayerViewModel
├── Player and application state (StateFlow)
├── User command handlers
├── History and resume decisions
├── Playlist decisions
└── Error decisions

MpvWrapper
└── Kotlin ↔ MPV boundary (named methods only)

MPV
├── Decoding
├── Seeking
├── A/V sync
├── Audio
├── Subtitles
├── Rendering
└── Playback clock
```

---

## Decision Tree

```
Does the platform (Android / Compose / MPV) already own this behavior?
    │
   YES → Use it. Do not reimplement it.
    │
   NO
    │
   Can a small direct solution (≤ ~50 lines) solve it?
    │
   YES → Use it inline or in the appropriate owner.
    │
   NO
    │
   Create a focused abstraction with a single clear responsibility.
   Name it after what it does, not what it manages.
```

---

## Before Any Refactor — Mandatory Checklist

Do not implement an audit recommendation blindly.

Before deleting, merging, or replacing any component:

1. Read the complete file.
2. Search all references and usages across the project.
3. Identify what state and side effects it owns.
4. Identify who currently owns the behavior.
5. Identify what will own the behavior after the change.
6. Verify lifecycle implications (especially Surface and orientation).
7. Verify there is no hidden dependency.
8. Make the smallest safe change.
9. Compile.
10. Run relevant tests if available.
11. Re-audit ownership after the change.

Never delete a class solely because its filename suggests redundancy.

---

## Patterns That Are Banned

These patterns have been explicitly decided against.
Do not introduce them:

- `PlaybackSessionManager` — session logic belongs in `PlayerViewModel`
- `PrefsApplicator` — preference application belongs in `PlayerViewModel`
- `SpeedController` — speed belongs in `SeekController` / `MpvWrapper`
- Self-observing managers (managers that collect their own StateFlows)
- Passing `MutableStateFlow` references between managers
- Any class that holds an `Activity` reference below the UI boundary
- Raw MPV property strings above `MpvWrapper`
- `*Manager` / `*Controller` classes whose only job is to forward calls

---

## Refactor Priority Order

When in doubt, use this sequence:

1. **Ownership corrections** — Activity leaks, Surface lifecycle, direct MPV access
2. **MPV boundary** — enforce typed `MpvWrapper` API throughout
3. **UI simplification** — remove artificial containers, merge tiny fragments
4. **Performance** — recomposition scope, only after profiling shows it matters

Do not rewrite gestures while simultaneously changing Surface, lifecycle, and ViewModel ownership.
Change one architectural layer at a time.

---

## On Recomposition

Do not scatter `collectAsStateWithLifecycle()` calls throughout the screen to satisfy a
theoretical recomposition optimization.

One `uiState` collection at the screen level is acceptable if the screen is reasonably structured.

Only scope state collection lower when:

- Profiling confirms it is causing real performance problems, or
- A subtree genuinely never needs to recompose with the parent.

Do not replace one `StateFlow` with twelve to appear more efficient.
