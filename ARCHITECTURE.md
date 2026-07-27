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

* `PlayerViewModel.kt`: 339 lines
* `PlayerScreen.kt`: 295 lines
* `PlayerGestureOverlay.kt`: 279 lines
* `MpvEventProcessor.kt`: 66 lines
* `PlayerModals.kt`: 62 lines
* `ControlsVisibilityState.kt`: 44 lines
* `PlayerUiState.kt`: 36 lines
* `PlayerSurface.kt`: 21 lines
* `TrackUiModel.kt`: 20 lines
* `PlayerViewModelFactory.kt`: 16 lines
* `PlayerUiConstants.kt`: 8 lines

**`feature/player/controls/`**

* `SubtitleTrackDialog.kt`: 207 lines
* `PlayerDecoderDialog.kt`: 192 lines
* `PlayerBottomControls.kt`: 156 lines
* `AudioTrackDialog.kt`: 123 lines
* `SubtitleAppearanceDialog.kt`: 123 lines
* `DoubleTapSeekOverlay.kt`: 74 lines
* `PlayerSeekBar.kt`: 69 lines
* `PlayerTopBar.kt`: 63 lines
* `PlayerQuickActions.kt`: 59 lines
* `SeekPreviewBubble.kt`: 57 lines
* `HoldToFastForward.kt`: 50 lines
* `TrackSelectionRow.kt`: 49 lines
* `PlayerControlsStyles.kt`: 22 lines

**`feature/player/controls/sheet/`**

* `PlayerSpeedSection.kt`: 160 lines
* `PlayerRightSideSheet.kt`: 132 lines
* `PlayerTracksSection.kt`: 72 lines
