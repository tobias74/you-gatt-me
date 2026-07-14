# YouGattMe

YouGattMe is an Android Bluetooth LE GATT simulator for fitness clients and
developer testing. It runs a local Android GATT server and exposes standard
fitness-related Bluetooth services without requiring real fitness equipment.

## Features

- Standards-based BLE GATT simulation on Android.
- FTMS machine modes: indoor bike, treadmill, rower, cross trainer, step climber, and stair climber.
- Optional standard services: Cycling Speed and Cadence, Cycling Power, and Running Speed and Cadence.
- Runtime controls for measurement fields, declared capabilities, FTMS Control Point behavior, and simulated workout values.
- Extensible provider API for adding additional profile providers in separate modules or custom builds.

The public app is complete on its own and ships only standard Bluetooth GATT profiles.

## Requirements

- Android Studio
- Android device with Bluetooth LE peripheral/GATT server support
- JDK 17

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

## Run

1. Open the project in Android Studio.
2. Connect an Android device with BLE support.
3. Install and run the `app` configuration.
4. Grant Bluetooth permissions when prompted.
5. Enable the GATT server and select the simulated services in the app UI.

## Standard Profiles

Included profiles:

- Fitness Machine Service (FTMS)
- Cycling Speed and Cadence (CSC)
- Cycling Power
- Running Speed and Cadence (RSC)
- Battery Service
- Device Information Service

See [docs/STANDARD_PROFILES.md](docs/STANDARD_PROFILES.md) for implementation notes.

## Extension Providers

YouGattMe has a neutral provider API for registering additional GATT profile providers.
The standard profiles are implemented through the same provider boundary used by extensions.

See [docs/EXTENSION_PROVIDERS.md](docs/EXTENSION_PROVIDERS.md) for the API shape and integration approach.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the current code layout and intended module boundaries.

## License

MIT License. See [LICENSE](LICENSE).
