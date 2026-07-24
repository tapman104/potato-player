# PotatoMPV — Codebase Lookup

> Principle: thin wrapper, stable surface, feature-rich UI. All MPV complexity lives in `MpvWrapper`. All state lives in `PlayerViewModel`. Compose layer is display-only.

---

## File Map

```
engine/
  MpvWrapper.kt               — sole MPV contact point; all JNI calls go here
  MpvOptionsConfigurator.kt   — init/post-init options, font assets, property observers
  MpvConstants.kt             — MpvProp, MpvEventId, MpvFmt constants
  MpvEvent.kt                 — sealed class: Id | PropertyLong | PropertyBool | PropertyString | PropertyDouble

feature/player/
  PlayerViewModel.kt          — all state + business logic; consumes MpvWrapper.events
  PlayerUiState.kt            — data class snapshot of everything the UI needs
  PlayerScreen.kt             — root Composable; hosts SurfaceView + lifecycle effects
  PlayerModals.kt             — dialog routing (which ActiveSheet is open)
  PlayerUiConstants.kt        — dp/duration constants shared across controls
  ControlsVisibilityState.kt  — auto-hide timer logic

  controls/
    PlayerTopBar.kt           — title, back, pip button
    PlayerBottomControls.kt   — play/pause, seek bar, time, speed
    PlayerSeekBar.kt          — draggable seek with preview bubble
    SeekPreviewBubble.kt      — timestamp tooltip on seek drag
    PlayerQuickActions.kt     — overlay icon row (lock, fit, decoder, subs, audio)
    PlayerRightSideSheet.kt   — slide-in sheet (speed + tracks)
    AudioTrackDialog.kt       — modal track picker for audio
    SubtitleTrackDialog.kt    — modal track picker for subtitles
    SubtitleAppearanceDialog.kt — scale + position sliders
    PlayerDecoderDialog.kt    — hwdec mode picker
    DoubleTapSeekOverlay.kt   — animated ±10s double-tap zones
    HoldToFastForward.kt      — hold-to-2× gesture
    TrackSelectionRow.kt      — shared row component for track dialogs

    sheet/
      PlayerSpeedSection.kt   — speed chips inside right sheet
      PlayerTracksSection.kt  — track lists inside right sheet

data/
  UserPreferencesRepository.kt — DataStore: autoRotation, subScale, subPos
  AppDatabase.kt / VideoHistory.kt / VideoHistoryDao.kt — Room: watch history

util/
  MediaMetadataRepository.kt  — resolveDisplayName, resolveSubtitlePath
  TimeFormatter.kt            — ms → HH:MM:SS string
  ContextExtensions.kt        — small Context helpers
```

---

## Threading Rules

| What | Thread | Why |
|------|--------|-----|
| All `MPVLib.*` JNI calls | **Main** | MPV requires it |
| `MpvWrapper.events` collection | Main (via `Dispatchers.Main` in VM) | StateFlow update + JNI reads |
| File I/O (subtitle path resolve) | `Dispatchers.IO` | blocking |
| `_uiState.update` | any | StateFlow is thread-safe |
| `loadTracks()` | **Main only** | calls `getPropertyInt` / `getPropertyString` |

Pattern for mixed work:

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    val path = MediaMetadataRepository.resolveSubtitlePath(context, uri) ?: uri.toString()
    withContext(Dispatchers.Main) {
        wrapper.addExternalSubtitle(path)
        loadTracks()
    }
}
```

---

## MpvWrapper — Stable API

```kotlin
// Lifecycle
attachSurface(surface)          // called from SurfaceHolder.Callback.surfaceCreated
detachSurface()                 // called from surfaceDestroyed + destroy()
destroy()                       // idempotent; call from ViewModel.onCleared()

// Playback
play(uri)                       // loadfile + unpause
pause()
togglePlay()                    // uses cachedPause, safe from any thread after event wired
resume()                        // setPropertyBoolean(PAUSE, false) ONLY — no surface setup

// Seek
seekTo(ms)                      // absolute+exact, clamps to 0
seekRelative(sec)               // relative+exact

// Tracks
setAudioTrack(id)               // -1 = "no"
setSubTrack(id)                 // -1 = "no"
addExternalSubtitle(path)       // sub-add select; call on Main after IO resolve

// Options
setSpeed(speed)
setDecoder(hwdec)               // "auto", "mediacodec-copy", "no"
setSubScale(scale)
setSubPos(pos)

// Read (Main thread only)
getPropertyInt(name): Int?
getPropertyString(name): String?

// Events
events: SharedFlow<MpvEvent>    // collect in ViewModel, never in Compose
onSurfaceReady: (() -> Unit)?   // set by ViewModel; fires after attachSurface
```

**Do not add** surface setup (`vo`, `force-window`) outside `attachSurface()`. `resume()` is pause-flag only.

---

## Surface Lifecycle

```
surfaceCreated  → attachSurface(holder.surface) → onSurfaceReady?.invoke()
surfaceChanged  → setPropertyString("android-surface-size", "${w}x${h}")
surfaceDestroyed→ detachSurface()

detachSurface() sequence (order matters):
  1. setPropertyString("vo", "null")
  2. setPropertyString("force-window", "no")
  3. MPVLib.detachSurface()
  4. currentSurface = null          ← null only, no .release()
```

On re-attach (app returns from background): MPV automatically routes frames back. `onSurfaceReady` guard in ViewModel skips `loadFile` if `currentUri == uri` — no restart.

---

## PlayerViewModel — Key Decisions

**First launch vs re-attach guard:**

```kotlin
fun onSurfaceReady(uri: String, title: String = "") {
    if (currentUri != uri) loadFile(uri, title)
    // else: re-attach, MPV resumes itself, do nothing
}
```

**Event collection pattern:**

```kotlin
viewModelScope.launch {
    wrapper.events.collect { event ->
        when (event) {
            is MpvEvent.PropertyLong  -> { /* position, duration */ }
            is MpvEvent.PropertyBool  -> { /* pause state */ }
            is MpvEvent.PropertyString-> { /* hwdec active */ }
            is MpvEvent.Id            -> { /* FILE_LOADED → loadTracks() on Main */ }
            else -> {}
        }
    }
}
```

**DataStore cold-start note:** `combine` on three DataStore flows won't emit until all three have emitted at least once. Subtitle/orientation prefs apply after first DataStore read. Acceptable — MPV isn't playing at that point. Watch for orientation flip on cold start if prefs are slow.

---

## Adding a Feature — Checklist

1. **New MPV property** → add constant to `MpvConstants.kt` (MpvProp object), add setter to `MpvWrapper`, expose via `PlayerUiState`, wire in `PlayerViewModel`.
2. **New dialog** → add entry to `ActiveSheet` sealed class in `PlayerModals.kt`, add composable to `controls/`, route in `PlayerModals.kt` when block.
3. **New track type** → extend `TrackType` enum, update `loadTracks()` in ViewModel, add dialog following `AudioTrackDialog` pattern.
4. **New persisted setting** → add DataStore key to `UserPreferencesRepository`, join into existing `combine` block in ViewModel init, apply to wrapper on Main.

**Do not:**

- Call `MPVLib.*` directly from Compose or ViewModel — route through `MpvWrapper`.
- Add state to `MpvWrapper` beyond what's needed to implement its API (`currentSurface`, `cachedPause`, `destroyed` are the only fields justified).
- Call `loadTracks()` off Main thread.
- Call surface setup (`vo`, `force-window`) from `resume()` or anywhere except `attachSurface()`.

---

## MpvConstants Quick Reference

```kotlin
// MpvProp (property name strings)
PAUSE, POSITION, DURATION, SPEED, HWDEC, HWDEC_CURRENT,
AID, SID, TRACK_LIST, TRACK_LIST_COUNT, SUB_SCALE, SUB_POS

// MpvEventId (int event IDs from MPVLib.MpvEvent)
FILE_LOADED, END_FILE, SEEK, PLAYBACK_RESTART
```

---

## Build

```
./gradlew assembleDebug              # debug APK (arm64 + armeabi-v7a + x86 + x86_64)
./gradlew assembleDebug -Pabi=arm64  # single ABI (faster iteration)
```

AAR: `libs/mpv-android-lib-v0.0.3.aar` — loaded via `fileTree` in `build.gradle.kts`. Do not change to direct path dependency (causes stale UP-TO-DATE outputs).

ABI filters set to `arm64-v8a` only in release to keep APK size down. Debug builds all four for emulator compat.

---

## Known Edge Cases

| Symptom | Cause | Status |
|---------|-------|--------|
| Audio underrun on resume | MPV audio buffer drains while backgrounded; brief gap on restart | Expected, not fixable |
| Choreographer 30-frame skip on background | MPV GL thread cleanup cost during `detachSurface` | Not actionable |
| Orientation flip on cold start | DataStore `combine` hasn't emitted all three prefs yet | Acceptable, monitor user reports |
