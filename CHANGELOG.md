# Changelog

All notable changes to AirMouse will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
