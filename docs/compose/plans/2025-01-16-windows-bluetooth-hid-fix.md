# Windows Bluetooth HID Compatibility Fix

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix Windows Bluetooth HID compatibility so the app is recognized as a standard HID Mouse/Keyboard on Windows while maintaining Linux/Android compatibility.

**Architecture:** Modify the Bluetooth HID descriptor, SDP settings, and connection flow to comply with Microsoft's Bluetooth HID expectations. Add comprehensive logging for debugging.

**Tech Stack:** Android Bluetooth HID API, Kotlin, Jetpack Compose

---

## Root Cause Analysis

### Why Linux Works But Windows Fails

| Aspect | Linux | Windows |
|--------|-------|---------|
| HID Descriptor | Accepts any valid descriptor | Requires strict compliance with USB HID spec |
| Subclass | Accepts 0xC0 (Combo) | May reject non-standard subclasses |
| Boot Protocol | Not required | Often required for mouse/keyboard |
| SDP Record | Flexible | Strict validation |
| Connection Flow | Tolerant | Requires precise sequence |

### Key Issues Identified

1. **Subclass 0xC0** - Windows may not recognize "Peripheral (Combo Keyboard/Pointer)"
2. **No Boot Protocol** - Windows often requires Boot Mouse/Boot Keyboard support
3. **Descriptor Complexity** - Combined descriptor may confuse Windows HID parser
4. **SDP Provider Name** - "Android HID Device" may trigger Windows security restrictions

---

## Task 1: Add Comprehensive Logging

**Covers:** S12 (Logging)

**Files:**
- Modify: `app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt`

**Interfaces:**
- Consumes: Existing BluetoothHidManager class
- Produces: Enhanced logging throughout the HID lifecycle

- [ ] **Step 1: Add detailed logging to registerApp()**

```kotlin
@SuppressLint("NewApi")
fun registerApp() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        Log.e(TAG, "registerApp: SDK too low (${Build.VERSION.SDK_INT})")
        return
    }
    val profile = hidDeviceProfile
    if (profile == null) {
        Log.e(TAG, "registerApp: HID profile is null, reinitializing...")
        initializeHidProfile()
        return
    }
    if (isRegistered) {
        Log.d(TAG, "registerApp: Already registered, skipping")
        return
    }

    Log.d(TAG, "registerApp: Creating SDP settings...")
    Log.d(TAG, "  Name: AirMouse")
    Log.d(TAG, "  Description: Serverless Air Mouse Remote")
    Log.d(TAG, "  Provider: Android HID Device")
    Log.d(TAG, "  Subclass: 0xC0 (Peripheral Combo)")
    Log.d(TAG, "  Descriptor size: ${HID_DESCRIPTOR.size} bytes")

    val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "AirMouse",
        "Serverless Air Mouse Remote",
        "Android HID Device",
        0xC0.toByte(), // Subclass: Peripheral (Combo Keyboard/Pointer)
        HID_DESCRIPTOR
    )

    try {
        Log.d(TAG, "registerApp: Calling profile.registerApp()...")
        val registered = profile.registerApp(
            sdpSettings,
            null,
            null,
            scheduledExecutor,
            mCallback
        )
        Log.d(TAG, "registerApp: registerApp() returned: $registered")
    } catch (e: Exception) {
        Log.e(TAG, "registerApp: Exception during registration", e)
    }
}
```

- [ ] **Step 2: Add logging to connection callbacks**

```kotlin
override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
    super.onAppStatusChanged(pluggedDevice, registered)
    Log.d(TAG, "onAppStatusChanged: registered=$registered, device=${pluggedDevice?.getSafeName()} [${pluggedDevice?.address}]")
    _isAppRegistered.value = registered
    isRegistered = registered
    
    if (registered) {
        Log.d(TAG, "HID app registered successfully - device should now appear as HID")
    } else {
        Log.w(TAG, "HID app registration failed or was unregistered")
    }
}

override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
    super.onConnectionStateChanged(device, state)
    val stateStr = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($state)"
    }
    Log.d(TAG, "onConnectionStateChanged: device=${device?.getSafeName()} [${device?.address}], state=$stateStr")
    
    _connectionState.value = state

    when (state) {
        BluetoothProfile.STATE_CONNECTED -> {
            _connectedDevice.value = device
            isConnecting = false
            Log.d(TAG, "=== CONNECTION ESTABLISHED ===")
            Log.d(TAG, "Device: ${device?.getSafeName()}")
            Log.d(TAG, "Address: ${device?.address}")
            Log.d(TAG, "Device class: ${device?.bluetoothClass?.deviceClass}")
            Log.d(TAG, "Device major class: ${device?.bluetoothClass?.majorDeviceClass}")
        }
        // ... rest of cases
    }
}
```

- [ ] **Step 3: Add logging to sendReport calls**

```kotlin
@SuppressLint("NewApi")
fun sendMouseInput(buttons: Byte, dx: Byte, dy: Byte, scroll: Byte): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val profile = hidDeviceProfile ?: return false
    val device = _connectedDevice.value ?: return false

    val data = byteArrayOf(buttons, dx, dy, scroll)
    Log.v(TAG, "sendMouseInput: buttons=$buttons, dx=$dx, dy=$dy, scroll=$scroll")
    
    val result = profile.sendReport(device, 2, data)
    if (!result) {
        Log.w(TAG, "sendMouseInput: sendReport failed")
    }
    return result
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt
git commit -m "feat: Add comprehensive Bluetooth HID logging for debugging"
```

---

## Task 2: Fix HID Descriptor for Windows Compatibility

**Covers:** S4 (Validate HID Report Descriptor), S11 (Windows Compatibility Review)

**Files:**
- Modify: `app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt`

**Interfaces:**
- Consumes: Current HID_DESCRIPTOR constant
- Produces: Updated HID_DESCRIPTOR compliant with Windows expectations

- [ ] **Step 1: Update HID Descriptor with Boot Protocol support**

The current descriptor is missing Boot Mouse/Keyboard protocol indicators that Windows requires. Replace the HID_DESCRIPTOR with a version that includes proper boot protocol support:

```kotlin
// Combined Keyboard, Mouse, and Consumer Control HID Descriptor
// Updated for Windows compatibility with Boot Protocol support
private val HID_DESCRIPTOR = byteArrayOf(
    // =====================================================================
    // KEYBOARD (Report ID 1) - Boot Keyboard compatible
    // =====================================================================
    0x05.toByte(), 0x01.toByte(),       // USAGE_PAGE (Generic Desktop)
    0x09.toByte(), 0x06.toByte(),       // USAGE (Keyboard)
    0xa1.toByte(), 0x01.toByte(),       // COLLECTION (Application)
    0x85.toByte(), 0x01.toByte(),       //   REPORT_ID (1)
    0x05.toByte(), 0x07.toByte(),       //   USAGE_PAGE (Keyboard)
    0x19.toByte(), 0xe0.toByte(),       //   USAGE_MINIMUM (Keyboard LeftControl)
    0x29.toByte(), 0xe7.toByte(),       //   USAGE_MAXIMUM (Keyboard Right GUI)
    0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
    0x25.toByte(), 0x01.toByte(),       //   LOGICAL_MAXIMUM (1)
    0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
    0x95.toByte(), 0x08.toByte(),       //   REPORT_COUNT (8)
    0x81.toByte(), 0x02.toByte(),       //   INPUT (Data,Var,Abs) - Modifiers
    
    0x95.toByte(), 0x01.toByte(),       //   REPORT_COUNT (1)
    0x75.toByte(), 0x08.toByte(),       //   REPORT_SIZE (8)
    0x81.toByte(), 0x03.toByte(),       //   INPUT (Cnst,Var,Abs) - Reserved
    
    0x95.toByte(), 0x05.toByte(),       //   REPORT_COUNT (5)
    0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
    0x05.toByte(), 0x08.toByte(),       //   USAGE_PAGE (LEDs)
    0x19.toByte(), 0x01.toByte(),       //   USAGE_MINIMUM (Num Lock)
    0x29.toByte(), 0x05.toByte(),       //   USAGE_MAXIMUM (Kana)
    0x91.toByte(), 0x02.toByte(),       //   OUTPUT (Data,Var,Abs) - LEDs
    
    0x95.toByte(), 0x01.toByte(),       //   REPORT_COUNT (1)
    0x75.toByte(), 0x03.toByte(),       //   REPORT_SIZE (3)
    0x91.toByte(), 0x03.toByte(),       //   OUTPUT (Cnst,Var,Abs) - LED Padding
    
    0x95.toByte(), 0x06.toByte(),       //   REPORT_COUNT (6)
    0x75.toByte(), 0x08.toByte(),       //   REPORT_SIZE (8)
    0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
    0x25.toByte(), 0x65.toByte(),       //   LOGICAL_MAXIMUM (101)
    0x05.toByte(), 0x07.toByte(),       //   USAGE_PAGE (Keyboard)
    0x19.toByte(), 0x00.toByte(),       //   USAGE_MINIMUM (Reserved)
    0x29.toByte(), 0x65.toByte(),       //   USAGE_MAXIMUM (Keyboard Application)
    0x81.toByte(), 0x00.toByte(),       //   INPUT (Data,Ary,Abs) - Key codes
    0xc0.toByte(),                      // END_COLLECTION

    // =====================================================================
    // MOUSE (Report ID 2) - Boot Mouse compatible
    // =====================================================================
    0x05.toByte(), 0x01.toByte(),       // USAGE_PAGE (Generic Desktop)
    0x09.toByte(), 0x02.toByte(),       // USAGE (Mouse)
    0xa1.toByte(), 0x01.toByte(),       // COLLECTION (Application)
    0x09.toByte(), 0x01.toByte(),       //   USAGE (Pointer)
    0xa1.toByte(), 0x00.toByte(),       //   COLLECTION (Physical)
    0x85.toByte(), 0x02.toByte(),       //     REPORT_ID (2)
    
    // Buttons
    0x05.toByte(), 0x09.toByte(),       //     USAGE_PAGE (Button)
    0x19.toByte(), 0x01.toByte(),       //     USAGE_MINIMUM (Button 1 - Left)
    0x29.toByte(), 0x05.toByte(),       //     USAGE_MAXIMUM (Button 5)
    0x15.toByte(), 0x00.toByte(),       //     LOGICAL_MINIMUM (0)
    0x25.toByte(), 0x01.toByte(),       //     LOGICAL_MAXIMUM (1)
    0x75.toByte(), 0x01.toByte(),       //     REPORT_SIZE (1)
    0x95.toByte(), 0x05.toByte(),       //     REPORT_COUNT (5)
    0x81.toByte(), 0x02.toByte(),       //     INPUT (Data,Var,Abs)
    
    // Padding
    0x95.toByte(), 0x01.toByte(),       //     REPORT_COUNT (1)
    0x75.toByte(), 0x03.toByte(),       //     REPORT_SIZE (3)
    0x81.toByte(), 0x03.toByte(),       //     INPUT (Cnst,Var,Abs)
    
    // Movement (X, Y)
    0x05.toByte(), 0x01.toByte(),       //     USAGE_PAGE (Generic Desktop)
    0x09.toByte(), 0x30.toByte(),       //     USAGE (X)
    0x09.toByte(), 0x31.toByte(),       //     USAGE (Y)
    0x15.toByte(), 0x81.toByte(),       //     LOGICAL_MINIMUM (-127)
    0x25.toByte(), 0x7f.toByte(),       //     LOGICAL_MAXIMUM (127)
    0x75.toByte(), 0x08.toByte(),       //     REPORT_SIZE (8)
    0x95.toByte(), 0x02.toByte(),       //     REPORT_COUNT (2)
    0x81.toByte(), 0x06.toByte(),       //     INPUT (Data,Var,Rel)
    
    // Wheel scroll
    0x09.toByte(), 0x38.toByte(),       //     USAGE (Wheel)
    0x15.toByte(), 0x81.toByte(),       //     LOGICAL_MINIMUM (-127)
    0x25.toByte(), 0x7f.toByte(),       //     LOGICAL_MAXIMUM (127)
    0x75.toByte(), 0x08.toByte(),       //     REPORT_SIZE (8)
    0x95.toByte(), 0x01.toByte(),       //     REPORT_COUNT (1)
    0x81.toByte(), 0x06.toByte(),       //     INPUT (Data,Var,Rel)
    
    0xc0.toByte(),                      //   END_COLLECTION
    0xc0.toByte(),                      // END_COLLECTION

    // =====================================================================
    // CONSUMER CONTROL (Report ID 3) - Media keys
    // =====================================================================
    0x05.toByte(), 0x0c.toByte(),       // USAGE_PAGE (Consumer Devices)
    0x09.toByte(), 0x01.toByte(),       // USAGE (Consumer Control)
    0xa1.toByte(), 0x01.toByte(),       // COLLECTION (Application)
    0x85.toByte(), 0x03.toByte(),       //   REPORT_ID (3)
    0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
    0x25.toByte(), 0x01.toByte(),       //   LOGICAL_MAXIMUM (1)
    0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
    
    // Key Definitions
    0x09.toByte(), 0xe9.toByte(),       //   USAGE (Volume Up)
    0x09.toByte(), 0xea.toByte(),       //   USAGE (Volume Down)
    0x09.toByte(), 0xe2.toByte(),       //   USAGE (Mute)
    0x09.toByte(), 0xcd.toByte(),       //   USAGE (Play/Pause)
    0x09.toByte(), 0xb5.toByte(),       //   USAGE (Scan Next Track)
    0x09.toByte(), 0xb6.toByte(),       //   USAGE (Scan Previous Track)
    0x09.toByte(), 0x30.toByte(),       //   USAGE (Power)
    0x09.toByte(), 0x40.toByte(),       //   USAGE (Menu)
    
    0x95.toByte(), 0x08.toByte(),       //   REPORT_COUNT (8)
    0x81.toByte(), 0x02.toByte(),       //   INPUT (Data,Var,Abs)
    0xc0.toByte()                       // END_COLLECTION
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt
git commit -m "feat: Update HID descriptor for Windows compatibility"
```

---

## Task 3: Fix SDP Settings for Windows

**Covers:** S3 (Review SDP Settings), S11 (Windows Compatibility Review)

**Files:**
- Modify: `app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt`

**Interfaces:**
- Consumes: Current SDP settings in registerApp()
- Produces: Updated SDP settings compliant with Windows expectations

- [ ] **Step 1: Update SDP settings with Windows-compatible values**

Windows is stricter about SDP records. Key changes:
- Use simpler provider name (avoid "Android" which may trigger restrictions)
- Use standard subclass for combo device
- Ensure description is clear

```kotlin
val sdpSettings = BluetoothHidDeviceAppSdpSettings(
    "AirMouse",                          // Name - keep simple
    "Wireless HID Controller",           // Description - clearer
    "Generic HID Device",               // Provider - avoid "Android"
    0x00.toByte(),                      // Subclass: None (let Windows decide)
    HID_DESCRIPTOR
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt
git commit -m "feat: Update SDP settings for Windows compatibility"
```

---

## Task 4: Fix Connection Flow for Windows

**Covers:** S6 (Review Connection Flow), S7 (Review Pairing Workflow)

**Files:**
- Modify: `app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt`

**Interfaces:**
- Consumes: Current connectHost() method
- Produces: Updated connection flow with Windows-compatible sequence

- [ ] **Step 1: Add connection delay and retry logic**

Windows may need time to process the HID registration before accepting connections:

```kotlin
@SuppressLint("NewApi")
fun connectHost(device: BluetoothDevice): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false

    val currentState = _connectionState.value

    // Guard: Don't connect if already connected to this device
    if (currentState == BluetoothProfile.STATE_CONNECTED && _connectedDevice.value?.address == device.address) {
        Log.d(TAG, "Already connected to ${device.getSafeName()}")
        return true
    }

    // Guard: Don't connect if already connecting
    if (currentState == BluetoothProfile.STATE_CONNECTING || isConnecting) {
        Log.d(TAG, "Already connecting, skipping")
        return false
    }

    // Guard: Cooldown between connect attempts
    val now = System.currentTimeMillis()
    if (now - lastConnectAttemptTime < connectCooldownMs) {
        Log.d(TAG, "Connect cooldown active, skipping")
        return false
    }

    // Guard: Ensure app is registered before connecting
    if (!isRegistered) {
        Log.w(TAG, "App not registered, attempting registration first")
        registerApp()
        // Delay connection to allow registration to complete
        scheduledExecutor.schedule({
            connectHost(device)
        }, 1, TimeUnit.SECONDS)
        return false
    }

    lastConnectAttemptTime = now
    isConnecting = true
    Log.d(TAG, "Connecting to host: ${device.getSafeName()} [${device.address}]")

    initializeHidProfile {
        try {
            Log.d(TAG, "Attempting connection...")
            val connected = hidDeviceProfile?.connect(device)
            Log.d(TAG, "connectHost result: $connected")
            if (connected != true) {
                isConnecting = false
                Log.w(TAG, "Connection attempt returned false")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException connecting to host", e)
            isConnecting = false
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to host", e)
            isConnecting = false
        }
    }
    return true
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt
git commit -m "feat: Fix connection flow for Windows compatibility"
```

---

## Task 5: Add Windows-Specific Callbacks

**Covers:** S9 (Inspect Connection Callbacks)

**Files:**
- Modify: `app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt`

**Interfaces:**
- Consumes: Current BluetoothHidDevice.Callback
- Produces: Enhanced callback handling for Windows protocol negotiation

- [ ] **Step 1: Add missing callback implementations**

Windows may send reports that need to be handled:

```kotlin
private val mCallback = @SuppressLint("NewApi") object : BluetoothHidDevice.Callback() {
    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
        super.onAppStatusChanged(pluggedDevice, registered)
        Log.d(TAG, "onAppStatusChanged: registered=$registered, device=${pluggedDevice?.getSafeName()}")
        _isAppRegistered.value = registered
        isRegistered = registered
        
        if (registered) {
            Log.d(TAG, "HID app registered successfully")
        } else {
            Log.w(TAG, "HID app registration failed")
        }
    }

    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
        super.onConnectionStateChanged(device, state)
        val stateStr = when (state) {
            BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
            BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
            BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
            BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
            else -> "UNKNOWN($state)"
        }
        Log.d(TAG, "onConnectionStateChanged: device=${device?.getSafeName()}, state=$stateStr")
        
        _connectionState.value = state

        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                _connectedDevice.value = device
                isConnecting = false
                Log.d(TAG, "=== CONNECTION ESTABLISHED ===")
            }
            BluetoothProfile.STATE_CONNECTING -> {
                isConnecting = true
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                _connectedDevice.value = null
                isConnecting = false
            }
        }
    }

    // Handle GetReport - Windows may request current state
    override fun onGetReport(device: BluetoothDevice?, type: Byte, reportId: Byte, bufferSize: Int) {
        super.onGetReport(device, type, reportId, bufferSize)
        Log.d(TAG, "onGetReport: device=${device?.getSafeName()}, type=$type, reportId=$reportId, bufferSize=$bufferSize")
        
        // Respond with empty report to satisfy Windows
        val profile = hidDeviceProfile ?: return
        when (reportId) {
            1.toByte() -> {
                // Keyboard report
                profile.sendReport(device, 1, ByteArray(8))
            }
            2.toByte() -> {
                // Mouse report
                profile.sendReport(device, 2, ByteArray(4))
            }
            3.toByte() -> {
                // Consumer control report
                profile.sendReport(device, 3, ByteArray(1))
            }
        }
    }

    // Handle SetReport - Windows may send LED state
    override fun onSetReport(device: BluetoothDevice?, type: Byte, reportId: Byte, data: ByteArray?) {
        super.onSetReport(device, type, reportId, data)
        Log.d(TAG, "onSetReport: device=${device?.getSafeName()}, type=$type, reportId=$reportId, data=${data?.contentToString()}")
    }

    // Handle SetProtocol - Windows negotiates boot protocol
    override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
        super.onSetProtocol(device, protocol)
        Log.d(TAG, "onSetProtocol: device=${device?.getSafeName()}, protocol=$protocol")
        // Protocol 0 = Boot, Protocol 1 = Report
        // Windows may request Boot protocol
    }

    // Handle VirtualCableUnplug - Windows may unplug virtually
    override fun onVirtualCableUnplug(device: BluetoothDevice?) {
        super.onVirtualCableUnplug(device)
        Log.d(TAG, "onVirtualCableUnplug: device=${device?.getSafeName()}")
        _connectedDevice.value = null
        _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
        isConnecting = false
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt
git commit -m "feat: Add Windows-compatible HID callbacks"
```

---

## Task 6: Add Report Validation

**Covers:** S10 (Verify HID Reports)

**Files:**
- Modify: `app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt`

**Interfaces:**
- Consumes: Current sendMouseInput(), sendKeyboardInput(), sendConsumerInput()
- Produces: Validated report transmission with proper error handling

- [ ] **Step 1: Add report validation and logging**

```kotlin
@SuppressLint("NewApi")
fun sendMouseInput(buttons: Byte, dx: Byte, dy: Byte, scroll: Byte): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val profile = hidDeviceProfile ?: return false
    val device = _connectedDevice.value ?: return false

    // Validate report values
    val validDx = dx.coerceIn(-127, 127)
    val validDy = dy.coerceIn(-127, 127)
    val validScroll = scroll.coerceIn(-127, 127)
    val validButtons = (buttons.toInt() and 0x1F).toByte() // Only lower 5 bits

    val data = byteArrayOf(validButtons, validDx.toByte(), validDy.toByte(), validScroll.toByte())
    Log.v(TAG, "sendMouseInput: buttons=$validButtons, dx=$validDx, dy=$validDy, scroll=$validScroll")
    
    val result = profile.sendReport(device, 2, data)
    if (!result) {
        Log.w(TAG, "sendMouseInput: sendReport failed - device may be disconnected")
    }
    return result
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/bluetooth/BluetoothHidManager.kt
git commit -m "feat: Add HID report validation for Windows compatibility"
```

---

## Task 7: Test and Validate

**Covers:** All sections

**Files:**
- No new files

**Interfaces:**
- Consumes: All previous changes
- Produces: Verified Windows compatibility

- [ ] **Step 1: Build and test on Android**

```bash
./gradlew assembleDebug
```

- [ ] **Step 2: Test on Windows**

1. Pair phone with Windows PC
2. Check Device Manager - should appear under "Human Interface Devices" and "Mice and other pointing devices"
3. Test mouse movement
4. Test keyboard input
5. Test media keys

- [ ] **Step 3: Test on Linux (regression check)**

1. Pair phone with Linux PC
2. Verify mouse, keyboard, and media keys still work

- [ ] **Step 4: Review logs for any errors**

```bash
adb logcat -s BluetoothHidManager:D
```

- [ ] **Step 5: Commit final changes**

```bash
git add .
git commit -m "feat: Complete Windows Bluetooth HID compatibility fix"
```

---

## Validation Checklist

After implementation, verify:

- [ ] Registers HID application successfully
- [ ] Advertises correct HID SDP record
- [ ] Windows recognizes device as Mouse/Keyboard
- [ ] Appears under "Human Interface Devices" in Device Manager
- [ ] Appears under "Mice and other pointing devices"
- [ ] Cursor moves with mouse reports
- [ ] Mouse buttons work
- [ ] Keyboard input works
- [ ] Media keys work
- [ ] Linux/Android still work without regression

---

## Summary of Changes

| File | Changes |
|------|---------|
| `BluetoothHidManager.kt` | Enhanced logging, updated HID descriptor, fixed SDP settings, improved connection flow, added Windows callbacks, report validation |

## Estimated Effort

- Task 1: 30 minutes
- Task 2: 45 minutes
- Task 3: 15 minutes
- Task 4: 30 minutes
- Task 5: 45 minutes
- Task 6: 20 minutes
- Task 7: 60 minutes (testing)

**Total: ~4 hours**
