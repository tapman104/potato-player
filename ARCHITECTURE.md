# Potato Player Architecture

```text tt
┌───────────────────────────── UI LAYER ──────────────────────────────────────┐
│  ┌──────────────┐    ┌──────────────┐    ┌───────────────┐   ┌────────────┐ │
│  │PlayerSurface │ ←─ │ PlayerScreen │ ←─ │ AppNavigation │ ← │MainActivity│ │
│  └──────┬───────┘    └──────────────┘    └───────────────┘   └────────────┘ │
│         │                                                                   │
└─────────┼───────────────────────────────────────────────────────────────────┘
          │ SurfaceHolder.Callback
┌─────────┼─────────────────── VIEWMODEL LAYER ───────────────────────────────┐
│         │   ┌─────────────────┐┌─────────────────────────────────────────┐  │
│         │   │MpvEventProcessor├┤            PlayerViewModel             │←┐ │
│         │   └─────────────────┘│                                        │ │ │
│         │                      │                                        │ │ │
│         │                      └────┬───────────────┬───────────┬─────┬─┘ │ │
└─────────┼───────────────────────────┼───────────────┼───────────┼─────┼───┼─┘
          │                           │               │           │     │   │  
┌─────────┼─────────────────── REPOSITORY LAYER ──────┼───────────┼─────┼───┼─┐
│         │                           │               │           │     │   │ │
│         │                  ┌────────┴───────┐┌──────┴─────┐┌────┴─────┐   │ │
│         │                  │MediaMetadataRep││VideoHistory││UserPrefs │   │ │
│         │                  └────────┬───────┘│ Repository ││Repository│   │ │
│         │                           │        └──────┬─────┘└────┬─────┘   │ │
└─────────┼───────────────────────────┼───────────────┼───────────┼─────┼───┼─┘
          │                           │               │           │     │   │  
┌─────────┼─────────────────── DATA LAYER ────────────┼───────────┼─────┼───┼─┐
│         │                           │               │           │     │   │ │
│         │                  ┌────────┴───────┐┌──────┴─────┐┌────┴─────┐   │ │
│         │                  │ContentResolver ││AppDatabase/││DataStore │   │ │
│         │                  └────────────────┘│HistoryDao  │└──────────┘   │ │
│         │                                    └────────────┘               │ │
└─────────┼─────────────────────────────────────────────────────────────┼───┼─┘
          │                                                             │   │  
┌─────────┼─────────────────── ENGINE LAYER ────────────────────────────┼───┼─┐
│         │                                        SharedFlow<MpvEvent> │   │ │
│         │                                                   Commands  │   │ │
│         │                                                         ↓   │   │ │
│    ┌────┴─────────────────────────────────────────────────────────┴──┐│   │ │
│    │                              MpvWrapper                         ├───┘  │
│    │                                                                 │      │
│    │   ┌───────────────┐                                             │      │
│    │   │TrackListParser│ (used by MpvWrapper on track-list event)    │      │
│    │   └───────────────┘                                             │      │
│    └────────────────────────────────────┬────────────────────────────┘      │
└─────────────────────────────────────────┼───────────────────────────────────┘
                                          │ JNI Calls
┌─────────────────────────── NATIVE LAYER ┼───────────────────────────────────┐
│                                         ↓                                   │
│                                  ┌────────────┐                             │
│                                  │   MPVLib   │                             │
│                                  └────────────┘                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Refactoring Debt: Player Feature Package

### 1. File Line Counts

**`feature/player/`**
*   `PlayerViewModel.kt`: 339 lines
*   `PlayerScreen.kt`: 295 lines
*   `PlayerGestureOverlay.kt`: 279 lines
*   `MpvEventProcessor.kt`: 66 lines
*   `PlayerModals.kt`: 62 lines
*   `ControlsVisibilityState.kt`: 44 lines
*   `PlayerUiState.kt`: 36 lines
*   `PlayerSurface.kt`: 21 lines
*   `TrackUiModel.kt`: 20 lines
*   `PlayerViewModelFactory.kt`: 16 lines
*   `PlayerUiConstants.kt`: 8 lines

**`feature/player/controls/`**
*   `SubtitleTrackDialog.kt`: 207 lines
*   `PlayerDecoderDialog.kt`: 192 lines
*   `PlayerBottomControls.kt`: 156 lines
*   `AudioTrackDialog.kt`: 123 lines
*   `SubtitleAppearanceDialog.kt`: 123 lines
*   `DoubleTapSeekOverlay.kt`: 74 lines
*   `PlayerSeekBar.kt`: 69 lines
*   `PlayerTopBar.kt`: 63 lines
*   `PlayerQuickActions.kt`: 59 lines
*   `SeekPreviewBubble.kt`: 57 lines
*   `HoldToFastForward.kt`: 50 lines
*   `TrackSelectionRow.kt`: 49 lines
*   `PlayerControlsStyles.kt`: 22 lines

**`feature/player/controls/sheet/`**
*   `PlayerSpeedSection.kt`: 160 lines
*   `PlayerRightSideSheet.kt`: 132 lines
*   `PlayerTracksSection.kt`: 72 lines

### 2. Files over 300 lines that should be split

**[PlayerViewModel.kt](file:///c:/Users/tapman/Desktop/potato%20ultra%20x/app/src/main/java/com/potato/player/feature/player/PlayerViewModel.kt)** (339 lines)
*   **What needs to be done:** The ViewModel is currently a god-class. It should be split into smaller, focused delegates or use cases. 
    *   Extract the `PlayerDialogStateHolder` class into its own file (or remove it from the ViewModel entirely, as it's UI state).
    *   Extract video history logic (`VideoHistoryRepository` interactions, `saveHistoryIfNeeded`) into a `PlaybackHistoryManager` or similar.
    *   Extract `UserPreferencesRepository` interactions (subtitle appearance, orientation settings) into a separate coordinator or use case.
    *   Leave only the core MPV playback state coordination in the ViewModel.

### 3. Mixed Concerns (Files doing more than one job)

**[PlayerViewModel.kt](file:///c:/Users/tapman/Desktop/potato%20ultra%20x/app/src/main/java/com/potato/player/feature/player/PlayerViewModel.kt)**
*   **What needs to be done:** It mixes playback state management with dialog UI state (`PlayerDialogStateHolder`), persistence (Video History), and user preferences (Subtitle zoom/position/orientation). These should be separated.

**[PlayerScreen.kt](file:///c:/Users/tapman/Desktop/potato%20ultra%20x/app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt)**
*   **What needs to be done:** It mixes UI composition with Android framework side effects. The `PlayerLifecycleEffect` handles window insets, decor fitting, and screen orientation locking, which bloats the UI code. 
    *   Extract the `PlayerLifecycleEffect` to a dedicated `SystemUIEffect.kt` or `OrientationEffect.kt` file.

**[PlayerGestureOverlay.kt](file:///c:/Users/tapman/Desktop/potato%20ultra%20x/app/src/main/java/com/potato/player/feature/player/PlayerGestureOverlay.kt)**
*   **What needs to be done:** It mixes the detection of gestures with the mathematical calculation of zoom boundaries, panning clamps, and volume/brightness increment scaling. 
    *   Extract the calculation logic (e.g., converting drag pixels to volume percentages or zoom scales) into a separate testable state holder or logic class.

### 4. Logic in the wrong layer

**UI Logic in ViewModel**
*   **`PlayerViewModel.kt`**: The `PlayerDialogStateHolder` class is currently housed inside the ViewModel. This manages which bottom sheet or dialog is open (`ActiveSheet.AUDIO`, `ActiveSheet.SPEED`, etc.). This is pure UI state and does not belong in the ViewModel. 
    *   **What needs to be done:** Move this state holder out of the ViewModel and scope it to the Compose layer (e.g., `rememberPlayerDialogState()`).

**Business / Side-Effect Logic in Composables**
*   **`PlayerGestureOverlay.kt`**: The double-tap seek gesture directly calculates the accumulated seek time (`current.totalSeconds + PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS`). 
    *   **What needs to be done:** The composable should just emit `onDoubleTap(isForward = true)`, and the ViewModel or domain layer should calculate the accumulated seek time.
*   **`PlayerScreen.kt`**: It directly interacts with `Activity` APIs like `activity?.enterPictureInPictureMode(...)` and `lockOrientation`. 
    *   **What needs to be done:** Composables shouldn't reach out to manipulate `Activity` directly. These actions should be passed up as events (e.g., `onEnterPipRequest`), or managed by a dedicated side-effect handler that abstracts the Activity away from the UI code.
