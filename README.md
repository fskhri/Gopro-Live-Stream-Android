# GoPro Rebuild

Open-source GoPro camera control app for Android. Rebuilt from reverse engineering GoPro Quik - no cloud, pure local control.

## Features

### ✅ Implemented
- **BLE Connection** - Scan, pair, connect to GoPro via Bluetooth LE
- **WiFi Connection** - Auto-retrieve credentials, connect to camera hotspot
- **Camera Control** - Shutter, power off, mode switching (Video/Photo/Timelapse)
- **Live Preview** - UDP stream via ExoPlayer
- **Media Browser** - Thumbnails, full-res view, delete
- **Media Download** - With progress tracking
- **Settings** - Resolution, FPS, FOV, HyperSmooth, Hindsight, Protune
- **Presets** - View and switch camera presets
- **Camera Info** - Model, firmware, serial, storage stats, battery

### ❌ Not Included
- GoPro Cloud
- Account/login
- OTA updates
- Video editing

## Architecture

```
gopro_rebuild/
├── ble/                        # Bluetooth LE layer
│   ├── GoProBleConstants.kt    # Service/Characteristic UUIDs
│   ├── GoProCommands.kt        # Command byte arrays
│   ├── GoProScanner.kt         # BLE scanning
│   └── GoProBleConnection.kt   # GATT client
├── wifi/                       # WiFi/HTTP layer
│   ├── GoProWifiApi.kt         # HTTP API client
│   └── WifiConnectionManager.kt# WiFi connection
├── camera/                     # Camera abstraction
│   ├── GoProCamera.kt          # Main controller
│   ├── CameraRepository.kt     # State management
│   └── CameraSettings.kt       # All setting enums
├── media/                      # Media handling
│   ├── PreviewPlayer.kt        # ExoPlayer wrapper
│   └── MediaDownloader.kt      # Download manager
├── ui/
│   ├── screens/                # Compose screens (7)
│   ├── viewmodel/              # ViewModels (7)
│   └── theme/                  # Material3 theme
└── di/                         # Hilt DI
```

## Protocol Reference

### BLE Services

| Service | UUID | Purpose |
|---------|------|---------|
| Control Point | `0000FEA6-0000-1000-8000-00805F9B34FB` | Commands, settings, queries |
| WiFi AP | `B5F90001-AA8D-11E3-9046-0002A5D5C51B` | Get WiFi SSID/password |
| Camera Mgmt | `B5F90090-AA8D-11E3-9046-0002A5D5C51B` | Camera management |

### BLE Characteristics

| Characteristic | UUID | Direction |
|----------------|------|-----------|
| Command | `B5F90072-...` | Write |
| Command Response | `B5F90073-...` | Notify |
| Setting | `B5F90074-...` | Write |
| Setting Response | `B5F90075-...` | Notify |
| Query | `B5F90076-...` | Write |
| Query Response | `B5F90077-...` | Notify |
| WiFi SSID | `B5F90002-...` | Read |
| WiFi Password | `B5F90003-...` | Read |

### Key Commands

```kotlin
// Shutter
SHUTTER_ON  = [0x01, 0x01, 0x01]
SHUTTER_OFF = [0x01, 0x01, 0x00]

// Power
POWER_OFF   = [0x01, 0x05]
KEEP_ALIVE  = [0x01, 0x5B]

// WiFi AP
WIFI_ON     = [0x03, 0x17, 0x01, 0x01]
WIFI_OFF    = [0x03, 0x17, 0x01, 0x00]
```

### HTTP API (Camera IP: 10.5.5.9)

```
GET /gopro/camera/shutter/start     # Start recording
GET /gopro/camera/shutter/stop      # Stop recording
GET /gopro/camera/control/sleep     # Power off
GET /gopro/camera/state             # Get status
GET /gopro/camera/presets/get       # Get presets
GET /gopro/camera/presets/load?id=X # Load preset
GET /gopro/camera/setting?setting=X&option=Y
GET /gopro/media/list               # List media
GET /gopro/media/thumbnail?path=X   # Get thumbnail
GET /gopro/media/delete/file?path=X # Delete file
GET /gopro/camera/stream/start      # Start preview
GET /gopro/camera/stream/stop       # Stop preview
```

### Setting IDs

| Setting | ID |
|---------|-----|
| Resolution | 2 |
| FPS | 3 |
| FOV/Lens | 121 |
| HyperSmooth | 135 |
| Hindsight | 167 |
| Timer | 173 |
| White Balance | 115 |
| ISO Max | 102 |
| ISO Min | 75 |
| Sharpness | 117 |
| EV Comp | 118 |

## Building

```bash
cd gopro_rebuild
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Requirements

- Android SDK 34
- Java 17
- Kotlin 1.9+
- Android device with BLE support

## Dependencies

- **Kable** - Kotlin BLE (same as official app)
- **Hilt** - Dependency injection
- **Jetpack Compose** - UI
- **Material3** - Design system
- **OkHttp** - HTTP client
- **ExoPlayer (Media3)** - Live preview
- **Coil** - Image loading
- **Kotlinx Serialization** - JSON parsing

## Tested Cameras

- GoPro Hero 12
- GoPro Hero 11
- GoPro Hero 10
- GoPro Hero 9
- (Any camera supporting Open GoPro BLE)

## Screens

1. **ScanScreen** - Find and connect to GoPro
2. **ControlScreen** - Main remote control
3. **LivePreviewScreen** - Full-screen preview
4. **MediaScreen** - Browse camera media
5. **SettingsScreen** - Video/photo settings
6. **PresetScreen** - Manage presets
7. **CameraInfoScreen** - Device info

## File Count

- 29 Kotlin files
- 7 XML resource files
- 3 Gradle files

## Legal

Clean-room reverse engineering for educational purposes.
Not affiliated with GoPro, Inc.

## License

MIT
