# Architecture

YouGattMe is currently a single Android application module with explicit internal
boundaries. The code is structured so those boundaries can become Gradle modules
later without changing the public app behavior.

## Current Layout

```text
app/
  src/main/java/de/tobiga/yougattme/
    ApplicationState.kt        BLE runtime and GATT server lifecycle
    ApplicationViewModel.kt    Simulator state shared by UI and profiles
    MainActivity.kt            Main simulator UI
    gatt/                      GATT core, standard profiles, provider API
    tools/                     Measurement simulation helpers
    ui/                        Legacy fragment screens
```

## Logical Boundaries

`gatt/provider`

Defines the neutral extension boundary:

- `GattProfileProvider`
- `GattProfileDefinition`
- `ProfileDependencies`
- `ProfileSelectionMode`

`gatt`

Contains reusable GATT primitives:

- `AbstractGattProfile`
- `BaseGattCharacteristic`
- `SingleServiceGattProfile`
- `SafeGattServiceManager`
- common descriptors and characteristic base classes

`gatt/fitnessmachine`, `gatt/csc`, `gatt/cyclingpower`, `gatt/rsc`

Contain standard Bluetooth profile implementations.

`MainActivity`

Builds the selectable simulator UI from profile definitions and asks registered
providers to create selected profile instances.

`ApplicationState`

Owns the Android BLE server lifecycle. It accepts a list of `AbstractGattProfile`
instances, registers their services, starts advertising, and forwards GATT
callbacks to the active profiles.

## Intended Module Split

The current single-module structure can be split later as:

```text
profile-api/        provider interfaces and public dependency types
gatt-core/          BLE server and reusable GATT primitives
standard-profiles/  FTMS, CSC, Cycling Power, RSC, Battery, Device Info
app/                Android UI and packaging
```

The public app must remain buildable and useful with only the standard provider.
