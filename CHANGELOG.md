# Changelog

All notable changes to AirMouse will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
