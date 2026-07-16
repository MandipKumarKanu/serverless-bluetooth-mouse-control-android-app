<div align="center">

# AirMouse

**Serverless Bluetooth Air Mouse, Touchpad, Keyboard & Presentation Remote**

Turn your Android phone into a full-featured wireless input device — no server, no companion app, no internet required. AirMouse registers directly as a Bluetooth HID device, so your phone becomes a mouse, keyboard, and remote control for any paired PC, Mac, or TV.

[![Android](https://img.shields.io/badge/Android-7.0%2B-blue.svg)](https://developer.android.com/about/versions/nougat)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-green.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## Features

| Mode | Description |
|------|-------------|
| **Air Mouse** | Gyroscope-based cursor control — wave your phone to move the pointer. Hold-to-move or free streaming modes with adjustable sensitivity, dead zone, smoothing, and acceleration. |
| **Touchpad** | Multi-touch laptop-style touchpad with tap-to-click, double-tap for double-click, drag for cursor movement, and a dedicated scroll bar with **inertia scrolling**. |
| **Keyboard** | Full QWERTY virtual keyboard with modifier toggles (Ctrl, Shift, Alt, Win), D-Pad navigation, and text string transmission. |
| **Media Remote** | Circular D-Pad controller with volume, playback, track navigation, power, and home buttons. |
| **Presentation Remote** | Slide navigation with prev/next, fullscreen toggle, and laser pointer simulation. |
| **Custom Shortcuts** | Create and save custom keyboard shortcuts (e.g., Ctrl+C, Alt+Tab, Win+L) for quick access. |

### Additional Features

- **Dark/Light Theme** — Toggle between dark and light themes in Settings
- **Scroll Inertia** — Momentum-based scrolling like a real trackpad
- **Auto-Reconnect** — Automatically reconnect to last paired device
- **Haptic Feedback** — Vibration on tap/click interactions
- **Keep Screen Awake** — Prevent screen timeout during use

## How It Works

AirMouse uses the **Android Bluetooth HID Device Profile** (`BluetoothHidDevice`) to register your phone as a standard hardware input device. When paired with a host (PC, Mac, TV), the phone sends HID reports directly over Bluetooth — just like a physical mouse or keyboard.

```
┌─────────────┐     Bluetooth HID       ┌──────────────┐
│  Android    │ ──────────────────────▶ │  Host PC/TV  │
│  Phone      │   Mouse/Keyboard/       │  (Paired)    │
│  (AirMouse) │   Media Reports         │              │
└─────────────┘                         └──────────────┘
     No server. No app on the host. Just Bluetooth.
```



## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Architecture:** MVVM (ViewModel + StateFlow)
- **Database:** Room (settings + shortcuts persistence)
- **Bluetooth:** Android HID Device Profile (`BluetoothHidDevice`)
- **Sensors:** `TYPE_GYROSCOPE` for air mouse control
- **Build:** Gradle with Version Catalog
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 36

## Requirements

- Android device with Bluetooth support
- Android 9.0 (API 28) or higher for HID functionality
- A paired host device (PC, Mac, or TV) — pair via system Bluetooth settings first

## Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable)
- JDK 11+

### Build & Run

1. **Clone the repository**
   ```bash
   git clone https://github.com/MandipKumarKanu/serverless-bluetooth-mouse-control-android-app.git
   cd serverless-bluetooth-mouse-control-android-app
   ```


2. **Open in Android Studio**
   - Select **Open** and choose the project directory
   - Let Android Studio sync Gradle and resolve dependencies

3. **Run the app**
   - Connect an Android device or start an emulator
   - Click **Run** (or `Shift+F10`)

### Pairing with a Host

1. Open your phone's **Settings → Bluetooth**
2. Pair with your target PC/TV (this must be done through system Bluetooth settings)
3. Open AirMouse → the paired device will appear on the Dashboard
4. Tap the device to connect
5. Select a control mode and start controlling!

## Project Structure

```
airmouse/
├── app/src/main/java/com/example/
│   ├── MainActivity.kt              # Entry point, navigation setup
│   ├── bluetooth/
│   │   └── BluetoothHidManager.kt   # HID device profile, report transmission
│   ├── sensor/
│   │   └── MotionSensorManager.kt   # Gyroscope processing, calibration, smoothing
│   ├── viewmodel/
│   │   └── AirMouseViewModel.kt     # MVVM ViewModel, bridges UI ↔ BT/sensor
│   ├── data/
│   │   └── AirMouseData.kt          # Room DB entities, DAO, database
│   └── ui/
│       ├── screens/
│       │   └── AirMouseScreens.kt   # All Compose UI screens
│       └── theme/
│           ├── Theme.kt             # Material 3 dynamic theming
│           ├── Color.kt             # Color palette
│           └── Type.kt              # Typography
├── app/src/test/                     # Unit tests
├── app/src/androidTest/              # Instrumented tests
├── build.gradle.kts                  # Top-level Gradle config
├── settings.gradle.kts               # Project settings
└── gradle/libs.versions.toml         # Version catalog
```

## Air Mouse Settings

| Setting | Range | Default | Description |
|---------|-------|---------|-------------|
| Sensitivity | 0.2x – 3.0x | 1.0x | Cursor speed multiplier |
| Smoothing | 0 – 1 | 0.3 | Low-pass filter strength |
| Dead Zone | 0 – 1 | 0.05 | Minimum movement threshold |
| Acceleration | 0 – 3 | 1.2 | Non-linear speed boost |
| Invert X/Y | On/Off | Off | Reverse axis direction |

## Permissions

| Permission | Purpose |
|------------|---------|
| `BLUETOOTH_CONNECT` | Connect to paired host devices |
| `BLUETOOTH_SCAN` | Discover nearby devices |
| `BLUETOOTH_ADVERTISE` | Register as HID device |
| `VIBRATE` | Haptic feedback on actions |
| `ACCESS_FINE_LOCATION` | Required for Bluetooth on Android < 12 |

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Made with Kotlin + Jetpack Compose**

</div>
