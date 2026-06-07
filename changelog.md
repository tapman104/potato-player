# Changelog

All notable changes to the Potato Player project will be documented in this file.

## [Unreleased] - Volume & Brightness Persistence
### Added
- Added volume and brightness level persistence across sessions using `AppPreferences` ("saved_volume" and "saved_brightness").
- Implemented volume and brightness restoration in `PlayerScreen` upon opening a media URI.
- Connected drag gesture handlers in `PlayerGestureHandler` to trigger saving of current levels when adjusted.

### Fixed
- Fixed an initialization order crash (`NullPointerException`) in `HomeViewModel` by ensuring all `MutableStateFlow` properties are initialized before the `init` block runs.

---

## [Phase 5] - Media Browser Refactor
### Added
- Created `MediaFile` data class storing properties of local media files (URI, display name, duration, size, folder info, MIME type).
- Added formatting extensions `Long.toFormattedDuration()` and `Long.toFormattedSize()` for media metadata presentation.
- Implemented `MediaFileRepository` to query video and audio files from `MediaStore` asynchronously using `ContentResolver` on `Dispatchers.IO`.
- Created `HomeViewModel` using a factory class to manage state, query filtering, and folder expansion state.
- Created `HomeUiState` supporting `Loading`, `PermissionRequired`, and `Ready` states with support for `FolderGroup`.
- Implemented stateless Compose components under `home/components/`:
  - `PermissionCard`: Manages runtime permission requests (`READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`) dynamically.
  - `MediaSearchBar`: Single-responsibility search bar to filter media items.
  - `FolderCard`: Collapsible card showing file counts and list of folder contents.
  - `MediaFileRow`: Renders a media item with async video thumbnail generation or fallback audio icon.
  - `RecentFilesRow`: Displays recently played files horizontally.
- Added `getAllSavedPositionUris()` to `AppPreferences` to fetch URIs of files with saved playback positions.

### Changed
- Refactored `HomeScreen.kt` to act as a stateless wrapper collecting state flows from `HomeViewModel` and rendering the browser view.
- Updated `MainActivity.kt` to instantiate `HomeViewModel` using its companion factory and integrated it with the new `HomeScreen`.

---

## [Phase 4] - Gesture Refinement & Settings Modularization
### Added
- Completed gesture handlers and settings for volume, brightness, and playback speed control.
- Designed and built modular settings screens (`GestureSettingsScreen`, `AppearanceSettingsScreen`, `SubtitleAppearanceSettingsScreen`, `PlaybackSettingsScreen`, `AboutScreen`) using Jetpack Compose.
- Implemented `PlayerControlsState` to handle the player controls overlay overlay, visibility logic, and auto-hide behaviors.

### Changed
- Decoupled settings layouts from the main activity into modular composables.
- Improved custom player control UI logic, including volume/brightness HUD indicator integrations.

---

## [Phase 3] - Core Media Engine & Player UI Setup
### Added
- Implemented core media engine using `ExoPlayer` (`ExoPlayerEngine`) with track selection, playback state flow, and lifecycle awareness.
- Developed `PlayerScreen` composable holding the video surface layout, custom gesture handling layers, and playback overlays.
- Created `BottomControlBar` offering playback speed options, aspect-ratio resizing controls, and rotation lock toggle.
- Added orientation logic and video dimension caching inside `PlayerViewModel` to handle dynamic screen rotations seamlessly.
- Configured persistent settings for subtitles (size and padding scaling) and appearance configurations via custom shared preferences.

---

## [Phase 2] - Gesture Engine & Initial Player Layouts
### Added
- Developed `PlayerGestureHandler` supporting horizontal swipes for seeking and vertical swipes (left/right screen sides) for brightness and volume control.
- Created adaptive launcher icons supporting all major density classes.
- Integrated screen wake-lock behaviors within `MainActivity` to prevent the device from sleeping during video playback.
- Designed initial custom controls overlay featuring play, pause, seek, and fast-forward buttons.

---

## [Phase 1] - Project Setup & Foundation
### Added
- Initialized project repository and workspace config.
- Created `MainActivity` with layout boilerplate and Android Media3 dependencies.
- Added basic file picker `HomeScreen` with simple system file intent triggers.
- Introduced `AppPreferences` for key-value configuration storage.
