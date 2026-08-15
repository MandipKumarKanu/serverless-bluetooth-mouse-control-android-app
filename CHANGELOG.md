# Changelog

All notable changes to AirMouse will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
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
