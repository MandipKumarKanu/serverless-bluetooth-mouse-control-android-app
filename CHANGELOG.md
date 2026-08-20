# Changelog

All notable changes to AirMouse will be documented in this file.

## [Unreleased]

## [1.10.8] - 2026-08-20

### Changed
- **Settings screen cleanup** — streamlined global Settings to focus on app-level preferences (theme, notification, keep awake, auto reconnect) while per-device pointer settings live cleanly in Device Specific Settings.
- **Dialog theming & contrast** — fixed dialog background and text colors in Touchpad and Shortcuts screens for consistent dark/light theme rendering.

## [1.10.7] - 2026-08-20

### Added
- **HidDeviceManager interface** — `BluetoothHidManager` now implements a clean `HidDeviceManager` interface, making the Bluetooth HID layer mockable and testable. The ViewModel and Service depend on the interface, not the concrete singleton.
- **Repository layer** — Extracted `SettingsRepository`, `GestureRepository`, and `ConnectionHistoryRepository` from the ViewModel. The ViewModel no longer touches Room DAOs or SharedPreferences directly.
- **SettingsViewModel & GestureViewModel** — Split the monolithic `AirMouseViewModel` into focused sub-ViewModels for settings CRUD and gesture/shortcut CRUD. The main ViewModel delegates to them.
- **Type-safe navigation** — New `NavRoute` sealed class with compile-time-checked route definitions. All navigation calls now use `navController.navigateTo(NavRoute.Dashboard)` instead of raw strings.
- **Connection error UI** — Failed connection attempts now show an inline error banner on the Dashboard with a dismiss button, replacing silent Toast-only feedback.
- **Auto-install OTA updates** — Downloaded APKs now trigger the system package installer automatically via `DownloadCompleteReceiver`. The "Download & Install" button shows a progress indicator while downloading.
- **Compose previews** — Added `@Preview` annotations to `AboutScreen` and `SplashScreen` for visual verification without running the app.
- **Detekt static analysis** — Added detekt Gradle plugin with sensible rules (line length, function complexity, unused members). Run with `./gradlew detekt`.

### Changed
- **Centralized color tokens** — 26 semantic color constants (`StatusConnected`, `StatusConnecting`, `GamepadA`, `TileTouchpad`, etc.) replace 43 hardcoded `Color(0xFF...)` values across 6 screen files.
- **Structured coroutine scope in AirMouseService** — Replaced 2 orphan `CoroutineScope` instances with a single `serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())`, cancelled in `onDestroy()`. No more coroutine leaks.
- **Convenience overloads on BluetoothHidManager** — Added 4-arg `sendMouseInput` and 4-arg `sendGamepadInput` overloads for callers on the concrete type, preserving backward compatibility.

## [1.10.6] - 2026-08-15

### Changed
- **Dependency modernization** — upgraded the AndroidX stack about two years forward for better performance, fixes, and future-proofing:
  - **Compose** BOM 2024.09 → 2026.06 — Compose UI 1.7.2 → 1.11.4 and Material 3 1.3.0 → 1.4.0 (newer components, bug fixes, faster lists/animations).
  - **Navigation** Compose 2.8.9 → 2.9.8, **Room** 2.7.0 → 2.8.4, **Lifecycle** 2.8.7 → 2.10.0, **Activity** 1.10.1 → 1.13.0.
  - The deprecated `material-icons-*` module is pinned at its final release (1.7.8) so all existing icons keep working now that newer Compose BOMs no longer manage it; upgrading to the Material Symbols successor remains an option for a future release.
  - Note: the very latest Compose (1.12, BOM 2026.08) requires compileSdk 37, which isn't published yet — this release uses the newest compileSdk-36-compatible stack and can bump again once platform 37 is available.

## [1.10.5] - 2026-08-15

### Added
- **AMOLED theme** — new theme mode in Settings (System Default, Light, Dark, AMOLED): identical to Dark but with pure-black backgrounds, so OLED panels turn pixels fully off for true blacks. Pairs with the existing dynamic-color option (pure black takes precedence).

## [1.10.4] - 2026-08-15

### Added
- **Device Specific Settings screen** — Settings now has a dedicated "Device Specific Settings" screen listing every host that has a saved pointer profile. Tap a host to edit its profile (sensitivity, smoothing, dead zone, acceleration, scroll speed, axis inversion) — changes save automatically to that host — and a "Reset to Global" button per host deletes the profile so it falls back to your global settings. The connected host is highlighted with a CONNECTED badge.
- **Gamepad mode help** — a "?" button in the top-right of the Gamepad screen explains the difference between Keyboard Mode and Gamepad Mode.
- **Gamepad mode haptics** — buttons and D-pad now vibrate on press in Gamepad Mode, matching Keyboard Mode's feedback (previously Gamepad Mode was silent).
- **Measured signal strength on the connection bar** — when connected, the sticky bar now shows `CONNECTED: <device>` with a signal icon and the measured RSSI in dBm (captured from discovery scans; hidden when unknown).
- **One entry per device in Recent Connections** — reconnecting the same host updates its timestamp instead of adding a duplicate row; pre-existing duplicates are cleaned up once on launch.

### Changed
- **Keyboard screen** — the CAPS/NUM/SCROLL lock indicators are now tappable toggles that flip the lock on the host PC (with instant optimistic feedback that syncs back to the host's real LED state), and the "Beam Clip" button is now a compact icon-only clipboard button.
- **Media Remote** — the Back / Apps / Enter row is now text-only (icons removed from that row only).
- **Every screen fills the window height** — screen bodies now enforce `min-height: 100vh` (the content column is at least the window height), so short content fills the screen and the controller screens (Air Mouse, Presentation, Media Remote, Gamepad) distribute their controls across the full height again, scrolling when they overflow.
- **Gesture workspace** — removed the emoji note from the drawing-canvas header; the RECORDING indicator is right-aligned on its own.
- **Snappier navigation with a transition guard** — screen transitions are now quick 200 ms fades and all touch input is blocked while a transition runs, so buttons on the screen you're leaving can no longer be pressed mid-animation.

## [1.10.3] - 2026-08-15

### Changed
- **Fluid responsive UI on every screen** — screens now share adaptive layout helpers (`Responsive.kt`): content is centered within a comfortable max width on large displays (tablets, landscape phones, foldables) instead of stretching edge-to-edge, and every screen is vertically scrollable so nothing clips on short windows. The large circular controls (air-mouse activation pad, media-remote D-pad) scale with the window, and the dashboard's control-mode grid shows three tiles per row on wide screens (two on phones).

## [1.10.2] - 2026-08-15

### Added
- **Host → phone HID feedback** — the phone now reads reports the PC sends back to it over the HID link:
  - **Keyboard lock indicators** — Caps Lock, Num Lock, and Scroll Lock states from the PC light up on the Keyboard screen (HID LED output report). The on-screen keys also mirror Caps Lock (uppercase) while it's active.
  - **Gamepad rumble** — the HID descriptor now exposes a force-feedback output report on the gamepad; when a DirectInput game or emulator sends rumble, the phone vibrates at the reported intensity and the Gamepad screen shows a RUMBLE indicator.

### Fixed
- **Unit test flake on release builds** — `AirMouseViewModelTest` hung (and leaked DB coroutines) because Room 2.7 flow emissions resumed from the query executor are never delivered to a virtual-time test dispatcher, and the DB seeding coroutine raced the tests' writes. Tests now run on real dispatchers and wait for the seed before writing.

## [1.10.1] - 2026-08-15

### Fixed
- **Upgrade crash on Android 7-12** — The v4→v5 database migration used `ALTER TABLE ... DROP COLUMN`, which requires SQLite 3.35+ (Android 13+) and crashed every upgrade from a pre-1.10 database on older devices. It is now a table rebuild, and the migration test runs at SDK 28 and 33.
- **Phone battery no longer disappears after toggling Bluetooth** — the BLE battery GATT server is now reset when Bluetooth turns off, and torn down properly on a Windows virtual-cable unplug.
- **Sleep shortcut removed** — the seeded "Sleep" macro actually pressed F12 (the HID System Sleep usage can't be sent in a keyboard report).
- **Update dialog sub-bullets** — changelog sub-bullets (indented `-` items) now render as sub-items instead of being swallowed as top-level items.
- **Gesture scroll actions** — `scroll up`/`scroll down` gestures now honor the scroll-speed setting like the touchpad.
- **Sensor handoff ordering** — backgrounding the app now stops the in-app gyro listener before handing off to the foreground service, removing a window where both listeners ran and doubled every cursor report.
- **Unit test sources compile under CI** — fixed `BluetoothClass` construction (package-private constructor → reflection helper), `assertEquals(emptyList(), …)` type inference in the touchpad tests, and protected `onCleared()` access in the ViewModel tests (via a `@VisibleForTesting` `clearForTest()` hook).

### Changed
- Removed unused public ViewModel flows (`isAppRegistered`, `batteryLevel`, `isCharging`) and ~580 unused imports across the screen files.

## [1.10.0] - 2026-08-15

### Added
- **Real HID gamepad** — The HID descriptor now includes a true Game Pad report (joystick axes, hat switch, 12 buttons) recognized by DirectInput games and emulators. Gamepad screen has a **Keyboard / Gamepad mode toggle**; buttons and D-pad use real press-and-hold semantics in both modes (D-pad becomes a hat switch with diagonal support in gamepad mode).
- **Touchpad depth** —
  - **Two-finger horizontal scroll** — the mouse report now carries a horizontal wheel (second Wheel usage), so two-finger left/right scrolls horizontally in apps that support it.
  - **Long-press drag-hold** — holding one finger still for a moment starts a drag (left button held); move and lift to drop. No more separate drag toggle needed.
  - **Three-finger window drag** — a slow, sustained three-finger movement drags (moves a window); a quick three-finger flick still opens Task View.
- **Per-device settings profiles** — Pointer settings (sensitivity, smoothing, dead zone, acceleration, scroll speed, axis inversion) are now remembered per paired host. First connection inherits your global settings; Settings shows a "Device-Specific Settings" section with a Reset-to-Global button. App-level settings (theme, vibration, keep-awake) remain global. DB migration v5 → v6.
- **Recent connections on Dashboard** — The last connected devices (with timestamps) now appear in a collapsible "Recent Connections" section with a Clear button; previously this data was recorded but never shown.
- **In-app device scanning & pairing** — New "Scan for nearby devices" section on the Dashboard finds and pairs hosts directly in the app (with RSSI display and a stop-scan control), removing the need to pair in system Bluetooth settings.
- **OTA direct APK download** — The update dialog now downloads the release APK directly via the system download manager when the release has an attached APK, instead of opening the GitHub page in a browser.
- **Quick Settings tile toggle** — The QS tile now starts/stops the air mouse when a device is connected (with active/inactive tile state and status subtitle); tapping while disconnected still opens the app.
- **Gesture action unification** — The gesture-assign dialog now exposes all executable actions (keyboard shortcuts, media controls, and mouse actions) instead of a partial list.
- **Notification Volume Up button** — The persistent notification now includes a `Vol+` action alongside `Prev`, `Play/Pause`, `Next`, and `Vol-`.
- **Robolectric test coverage for the core** — New tests for the Room DAO (CRUD + flows), the database migration chain (v1 → v5, including the `themeDark` → `themeMode` conversion), `BluetoothHidManager` device-classification helpers and connection guards, and `AirMouseViewModel` persistence flows. The release CI now runs the full suite plus `lintRelease` (reporting-only) before building the APK.
- **R8/minify for release builds** — The release APK is now minified and optimized (with keep rules for Moshi codegen, ViewModels, and line numbers in stack traces). Verbose/debug `Log.d`/`Log.v` calls are compiled out of release builds while `Log.i/w/e` remain for diagnostics.
- **Per-screen files** — The 4,281-line `AirMouseScreens.kt` monolith is split into focused files (`Routes`, `SplashScreen`, `PermissionsScreen`, `DashboardScreen`, `TouchpadScreen`, `AirMouseScreen`, `KeyboardScreen`, `MediaRemoteScreen`, `PresentationScreen`, `ShortcutsScreen`, `SettingsScreen`, `AboutScreen`, `StickyConnectionIndicator`). No behavior changes.
- **Unit tests run in CI** — The release workflow now runs `testDebugUnitTest` before building the APK, so failing tests block the release.

### Changed
- **Dead code cleanup** — Removed unused state collects in the Dashboard/Air Mouse screens (`batteryLevel`, `isCharging`, `isAppRegistered`, `isProfileReady`), the unused `GestureActionType` enum, the never-read `AirMouseService.isRunning`/`connectedDeviceName` flows, and a leak-prone throwaway coroutine scope in the sensor startup path.
- **Receiver hardening** — Context-registered receivers (`BatteryMonitor`, `BluetoothHidManager`) now use `RECEIVER_NOT_EXPORTED` so other apps can't spoof Bluetooth/battery state broadcasts.
- **Deprecated GATT API** — `BleBatteryService` uses the offset-based `setValue`/`notifyCharacteristicChanged` overloads on Android 13+.
- **Debug signing fallback** — Debug builds fall back to the standard Android debug keystore when the repo-local `debug.keystore` is absent (fixes debug builds/CI on machines without the file).

## [1.9.91] - 2026-08-15

### Fixed
- **Foreground Service crash edge case** — AirMouseService now re-enters the foreground when started while not yet foreground (widget/ViewModel air-mouse intents) or restarted by the system with a null intent, preventing `ForegroundServiceDidNotStartInTimeException`.
- **Widget boot refresh** — Added missing `RECEIVE_BOOT_COMPLETED` permission and declared `BOOT_COMPLETED` and `CONNECTION_STATE_CHANGED` actions so the widget refreshes after reboot and on connection changes.
- **Single source of truth for ASCII→HID key mapping** — Extracted the duplicated char-mapping logic from `sendText` and the Keyboard screen into `HidKeyMapper`, and added previously unsupported backtick/tilde characters.
- **Robust gesture serialization** — Gesture points are now persisted as Moshi JSON instead of the fragile data-class `toString()` format (legacy gestures still parse via fallback). Gesture `actionType` is now categorized correctly (keyboard/media/mouse).
- **Update checker version comparison** — Pre-release tags (e.g. `1.9.9-rc1`) are no longer reported as newer than released versions.
- **Removed fake RSSI display** — The connected-bar showed a hardcoded dBm value that was never measured; it now shows just the connected device name.
- **Pointer Acceleration setting** — The previously hidden `acceleration` setting now has a slider in Settings.
- **About screen version** — Now shows the real app version from `BuildConfig` instead of the stale hardcoded `1.2.0`.
- **Dead code & unused dependencies removed** — Deleted inert tremor-calibration pipeline and unused fields/methods in `AdaptiveSmoothingFilter`, unused `ThemeColors`, unused `BluetoothHidManager.destroy()`, and unused camera/Coil/Accompanist/Play Services/Retrofit/OkHttp dependencies.

### Added
- Unit tests for the gesture recognizer, adaptive smoothing filter, version comparison, gesture serialization, and the HID key mapper.

### Changed
- **Touchpad gesture engine refactor** — Replaced the ad-hoc multi-touch pointer handling with a dedicated, unit-tested `TouchpadGestureRecognizer` state machine. Fixes:
  - **Drag no longer clicks** — Moving the cursor with one finger and lifting no longer fires a spurious left click on release; a click only fires when the pointer barely moved.
  - **Two-finger scroll** — Two fingers now scroll the page (respects the scroll-speed setting), in addition to pinch-to-zoom.
  - **Continuous pinch & scroll** — Pinch-to-zoom and two-finger scroll emit repeated ticks while the gesture continues (previously a pinch fired at most one zoom tick).
  - **Safe finger transitions** — Lifting from 3 → 2 fingers (or 2 → 1) can no longer accidentally trigger a scroll, pinch, or click mid-gesture; the gesture mode is locked to the max finger count.
  - **Correct double-click** — Double-tap now produces exactly two clicks (previously it sent three).
- **Auto-scroll to top on connect** — Dashboard now smoothly scrolls back to the top when a device connects, so the connected-device card and control modes are immediately visible even when the tapped device was at the bottom of a long paired-device list.

## [1.9.9] - 2026-08-14

### Added
- **Beam Phone Clipboard to PC** — Added 1-tap "Beam Clip" button on Keyboard screen to automatically transmit phone copied text character-by-character over Bluetooth HID.
- **PC App Switcher / Task View Button** — Added "Apps" button (`Win + Tab`) to Media Remote navigation bar.

## [1.9.8] - 2026-08-14

### Added
- **Screen Mirroring & Wireless Display Button** — Added "Mirror TV" (Cast) button on Media Remote screen. Simultaneously sends `Win + K` wireless display connect hotkey over Bluetooth HID and opens native Android Cast panel for 1-tap TV screen mirroring.

## [1.9.7] - 2026-08-14

### Added
- **Google TV Voice Assistant Remote** — Added "Voice TV" button on Media Remote screen. Opens Google TV search on TV and speech recognition on phone, then beams spoken text over Bluetooth HID to TV search box.
- **Hardware Volume Keys Integration** — Mapped phone physical Volume Up / Volume Down keys to control host volume across Media Remote, Touchpad, and Air Mouse screens.

### Fixed
- **Notification Media Action Buttons** — Added explicit `setPackage(packageName)` targeting on notification pending intents so `Prev`, `Play/Pause`, `Next`, and `Vol-` notification buttons work reliably across Android 14/15 system UI.

## [1.9.6] - 2026-08-14

### Fixed
- **Air Mouse Motion Sensor Engine Overhaul** — Fixed inverted EMA low-pass filter logic to eliminate input lag on fast movements and hand jitter/shakiness on slow movements.
- **Natural 360-Degree Horizontal Tracking** — Combined Gyroscope Yaw (Z-axis) and Roll (Y-axis) for fluid, effortless left/right cursor motion regardless of phone tilt angle.
- **Micro-Movement Deadzone Tuning** — Reduced deadzone threshold scaling for immediate response to small wrist movements.

## [1.9.5] - 2026-08-14

### Added
- **Multi-Finger Touchpad Gestures** — 2-finger tap for Right-Click, 2-finger pinch for Zoom In/Out (`Ctrl + Scroll`), and 3-finger swipe up/down for Task View (`Win + Tab`).
- **Hardware Volume Button Clicker** — Physical phone volume buttons (`Vol Up` / `Vol Down`) act as presentation slide clickers (Page Up/Page Down) in Presentation Mode and volume controls in Media Remote Mode.
- **Persistent Media Control Notification** — Displays ongoing notification with action buttons (`Prev`, `Play/Pause`, `Next`, `Vol-`, `Start/Stop Mouse`) when connected.

## [1.9.4] - 2026-08-14

### Added
- **Automatic 10-Second Connection Timeout** — Connection attempts automatically time out after 10 seconds if target device is unreachable, returning to Disconnected state with Toast notification.
- **One-Tap Device Switching & Manual Cancellation** — Tap connecting device card again to cancel, or tap another device to instantly switch connection target.
- **Explicit Cancel Controls** — Added Cancel buttons on status card, device list item, and bottom sticky status bar.
- **Touchpad Top-Right Settings Menu** — Added top-right Settings button on Touchpad screen with sliders for live Touch Pointer Sensitivity and Scroll Bar Sensitivity adjustments.
- **Clean Connection Status Card** — Removed battery percentage text/row from under the main connection status text.
- **Enhanced Sticky Connection Bar** — Removed blinking dot animation and added network signal icon with RSSI strength display (`CONNECTED: Device (network icon) -62 dBm`).

## [1.9.3] - 2026-08-14

### Fixed
- **Android 14 Compatibility & Crash Fixes** — Added FGS connectedDevice type and receiver export flags to prevent connection crashes.

## [1.9.2] - 2025-07-20

### Added
- **Notification Action Buttons** — Media controls + air mouse toggle directly from notification
  - Play/Pause, Next Track, Volume Down buttons when connected
  - Air Mouse toggle button (Start/Stop) in notification
  - No need to open the app for basic controls

## [1.9.1] - 2025-07-20

### Added
- **BLE GATT Battery Service** — Phone battery level now visible in host's Bluetooth settings
  - Standard Bluetooth Battery Service (UUID 0x180F) exposed via BLE GATT
  - Works like Bluetooth earphones — host shows battery in its Bluetooth settings
  - Battery updates pushed via GATT notifications when level changes
  - Automatically starts on HID connection, stops on disconnect
- **Live Battery Monitoring** — Battery level updates in real-time throughout the app
  - Foreground notification shows battery level and charging status
  - Dashboard connection card displays battery with color-coded icon
  - Battery icon: red (<=15%), amber (<=30%), green (charging)

### Fixed
- Fixed all deprecation warnings from CI build
- `BluetoothAdapter.getDefaultAdapter()` replaced with `BluetoothManager.adapter`
- Deprecated Material icons replaced with AutoMirrored versions

## [1.9.0] - 2025-01-16

### Added
- System Keys shortcuts (Sleep, Browser Back, Browser Forward)
- Horizontal scroll support via mouse wheel

## [1.8.4] - 2025-01-16

### Fixed
- Paired devices now sort with connected/last used device at top

## [1.8.3] - 2025-01-16

### Changed
- Replaced Dark Theme toggle with Theme Mode dropdown
- Three options: System Default, Light, Dark
- System Default follows device theme setting

## [1.8.2] - 2025-01-16

### Fixed
- Update dialog now parses markdown changelog into clean format
- System theme follows device theme by default
- Material You (dynamic colors) disabled by default

## [1.8.1] - 2025-01-16

### Fixed
- **Windows Bluetooth HID Compatibility** — Complete implementation per PRD
  - Replaced hardcoded subclass with `BluetoothHidDevice.SUBCLASS1_COMBO`
  - Added all required HID callbacks (onGetReport, onSetReport, onSetProtocol, onInterruptData, onVirtualCableUnplug)
  - Fixed registration flow - connectHost() now waits for registration to complete
  - Updated Consumer Control descriptor for Windows compatibility
  - Added report ID validation for all HID reports
  - Added pending connection mechanism for registration-complete flow
  - Comprehensive logging for all HID operations
  - Fixed reportError() API call

## [1.8.0] - 2025-01-16

### Fixed
- **Windows Bluetooth HID Compatibility** — Device now recognized on Windows
  - Updated HID descriptor for Windows compliance
  - Fixed SDP settings (provider name, subclass)
  - Added Windows-compatible callbacks (onGetReport, onSetProtocol, onVirtualCableUnplug)
  - Added report validation for mouse/keyboard/consumer input
  - Added registration check before connection
  - Enhanced logging for debugging

### Added
- Comprehensive Bluetooth HID logging throughout lifecycle
- Report validation for mouse buttons, X/Y values, scroll
- Windows protocol negotiation handlers

## [1.7.2] - 2025-01-16

### Added
- **Register Gesture button** — Clear button to enter registration mode
- Blue indicator shows when in register mode
- Clear flow: Register → Draw → Save → Done

### Fixed
- Gesture recognition now works properly
- Saved gestures list with tap-to-execute
- Better UX with visual feedback

## [1.7.1] - 2025-01-16

### Fixed
- Service now stops when app is swiped away from recent apps
- Bluetooth disconnects when app is fully killed
- Bluetooth stays connected only when app is in background (not killed)

## [1.7.0] - 2025-01-16

### Added
- **Gesture Mode** — Draw gestures and assign actions
  - Record custom gestures by drawing on canvas
  - Assign keyboard shortcuts, media controls, or mouse actions
  - Quick action buttons for common gestures
  - Visual trail while drawing
  - Gesture recognition using $1 Unistroke algorithm
  - Database storage for saved gestures

## [1.6.2] - 2025-01-16

### Fixed
- Sensors now pause when app is minimized (saves battery)
- Bluetooth connection stays alive via Foreground Service
- Sensors resume when app comes back to foreground
- Proper lifecycle management for background/foreground transitions

## [1.6.1] - 2025-01-16

### Fixed
- Control mode icons are now colorful when Material You is OFF
- Icons use accent color only when Material You is enabled

## [1.6.0] - 2025-01-16

### Added
- **Foreground Service** — Bluetooth stays connected when app is minimized
  - Persistent notification shows connection status
  - Service starts automatically on Bluetooth connect
  - Service stops on explicit disconnect
  - Connection maintained even when app is in background

## [1.5.2] - 2025-01-16

### Fixed
- Connected status text is now white on green background (consistent with home screen)
- Control mode icons now use Material You accent color when enabled
- Icons fall back to cyan when Material You is disabled

## [1.5.1] - 2025-01-16

### Added
- **Dynamic Widget Status** — Home screen widget now shows real-time status
  - Bluetooth connection status (ON/OFF with color indicator)
  - Phone battery level
  - Updated every 15 minutes automatically
  - Button text changes based on Bluetooth state

## [1.5.0] - 2025-01-16

### Added
- **Adaptive Gyroscope Smoothing** — Professional-grade cursor control
  - Speed-adaptive filtering: strong smoothing when still, minimal when fast
  - Hysteresis transitions: prevents jarring mode switches
  - Predictive filtering: cursor feels ahead of hand movement
  - User-specific tremor calibration: learns your hand characteristics
  - Logarithmic acceleration: natural speed feel like a real mouse
  - Noise floor detection: distinguishes tremor from intentional movement

## [1.4.3] - 2025-01-16

### Fixed
- Updated dark theme tile color to #0E162A for better contrast
- Restored AirMouseScreens.kt file (was accidentally emptied)

## [1.4.2] - 2025-01-16

### Fixed
- Drag & Drop mode now properly sends button state with touch moves
- Update Dialog now supports dark/light/Material You themes
- Added spacing between click buttons and drag & drop toggle
- Fixed syntax errors in TouchpadScreen

## [1.4.1] - 2025-01-16

### Added
- **Laser Pointer** — Touch area in Presentation mode for pointing during slides
- **F1-F12 Function Keys** — Added to Keyboard screen for quick access
- **Drag & Drop Mode** — Toggle in Touchpad to hold/release mouse button for dragging

## [1.4.0] - 2025-01-16

### Added
- **Gamepad Mode** — New control mode that turns your phone into a game controller
- D-Pad with arrow key mapping (Up/Down/Left/Right)
- Action buttons (A/B/X/Y) mapped to keyboard keys
- Shoulder buttons (L1/R1) with modifier keys
- Start/Select buttons
- Home button for media control
- Color-coded action buttons (A=Green, B=Red, X=Blue, Y=Yellow)
- **Quick Settings Tile** — Access AirMouse from Android notification shade
- **Home Screen Widget** — Quick access widget with app launch button
- **Connection History** — Shows last 5 connected devices with timestamps
- **Battery Indicator** — Phone battery level with color-coded bar
- **Landscape Mode** — Full landscape support for tablets and phones

### Changed
- Database updated to v3 with connection_history table
- Dashboard now shows battery and recent connections

## [1.3.3] - 2025-01-16

### Fixed
- Control mode tiles now have visible background in Material You mode
- Connected status text is white on green background (both dark and light themes)
- Removed redundant "Bluetooth Serverless HID" text from connection card
- Added subtle border to control mode tiles for better visibility

## [1.3.2] - 2025-01-16

### Added
- **Material You Support** — Dynamic colors based on wallpaper (Android 12+)
- Toggle in Settings to enable/disable Material You theming
- Database migration for new setting

## [1.3.1] - 2025-01-16

### Fixed
- Green color restored for connected device status
- Streaming/active states now show green instead of cyan
- Update dialog responsiveness - buttons stack vertically on small screens
- Switch thumb visibility - white thumb now visible in both ON and OFF states

## [1.3.0] - 2025-01-16

### Added
- **Complete Dark/Light Theme** — Full theme support across all screens with proper color schemes
- Theme-aware backgrounds, surfaces, text colors, buttons, and UI elements
- Material 3 color system integration for consistent theming

### Changed
- All screens now use `MaterialTheme.colorScheme` instead of hardcoded colors
- Splash screen, Dashboard, Touchpad, Air Mouse, Keyboard, Media Remote, Presentation, Shortcuts, Settings, and About screens updated
- Improved visual consistency between dark and light modes

## [1.2.0] - 2025-01-16

### Added
- **OTA Update Checker** — Automatic update notifications on app launch via GitHub Releases
- Shows update dialog with changelog when new version is available
- "Download" button opens GitHub release page

## [1.1.0] - 2025-01-16

### Added
- **Scroll Inertia** — Momentum-based scrolling in Touchpad mode with gradual deceleration
- **Dark/Light Theme Toggle** — Switch between dark and light themes in Settings
- **Keep Screen Awake Toggle** — Configurable screen timeout prevention

### Changed
- Updated Settings screen with new "Appearance" section
- Improved touchpad scroll bar with physics-based inertia

### Fixed
- Bluetooth connect/disconnect loop on some devices
- Executor resource leak in consumer input handling
- All deprecated Compose API warnings resolved

## [1.0.2] - 2025-01-15

### Fixed
- Resolved all deprecation warnings (Icons.Filled → Icons.AutoMirrored.Filled)
- Replaced deprecated `Divider()` with `HorizontalDivider()`
- Suppressed legacy `BluetoothAdapter.enable()` warning

## [1.0.1] - 2025-01-15

### Fixed
- Bluetooth connection instability causing connect/disconnect loops
- Auto-reconnect triggering multiple concurrent attempts
- Executor leak in `sendConsumerInput()`
- Profile proxy not reconnecting after disconnection

### Changed
- Added connection state guards and cooldown timers
- Improved auto-reconnect with debouncing

## [1.0.0] - 2025-01-14

### Added
- Initial release
- **Air Mouse** — Gyroscope-based cursor control with hold/free modes
- **Touchpad** — Multi-touch laptop-style touchpad with scroll bar
- **Keyboard** — Full QWERTY with modifier keys and text transmission
- **Media Remote** — Volume, playback, and track controls
- **Presentation Remote** — Slide navigation with fullscreen toggle
- **Custom Shortcuts** — Save and execute keyboard shortcuts
- Auto-reconnect to last paired device
- Haptic feedback on interactions
- Configurable sensitivity, smoothing, dead zone, and acceleration
