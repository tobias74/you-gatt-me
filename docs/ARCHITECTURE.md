# Architecture

YouGattMe is currently a single Android application module with explicit internal
boundaries. The code is structured so those boundaries can become Gradle modules
later without changing the public app behavior.

## Current Layout

```text
app/
  src/main/java/de/tobiga/yougattme/
    ApplicationState.kt        BLE runtime and GATT server lifecycle
    MainActivity.kt            Main simulator UI
    ui/                        Legacy fragment screens
simulator-core/
  src/main/java/de/tobiga/yougattme/
    ApplicationViewModel.kt    Simulator state shared by UI and profiles
    gatt/                      GATT core, standard profiles, provider API
    tools/                     Measurement simulation helpers
    ui/                        Legacy fragment screens
```

## Logical Boundaries

`simulator-core:gatt/provider`

Defines the neutral extension boundary:

- `GattProfileProvider`
- `GattProfileDefinition`
- `ProfileDependencies`
- `ProfileSelectionMode`

`simulator-core:gatt`

Contains reusable GATT primitives:

- `AbstractGattProfile`
- `BaseGattCharacteristic`
- `SingleServiceGattProfile`
- `SafeGattServiceManager`
- common descriptors and characteristic base classes

`simulator-core:gatt/fitnessmachine`, `gatt/csc`, `gatt/cyclingpower`, `gatt/rsc`

Contain standard Bluetooth profile implementations.

`MainActivity`

Builds the selectable simulator UI from profile definitions and asks registered
providers to create selected profile instances.

`ApplicationState`

Owns the Android BLE server lifecycle. It accepts a list of `AbstractGattProfile`
instances, registers their services, starts advertising, and forwards GATT
callbacks to the active profiles.

## Module Split

The current split is:

```text
simulator-core/  provider interfaces, simulator state, reusable GATT primitives, and standard profiles
app/             Android UI, BLE runtime lifecycle, and packaging
```

The public app must remain buildable and useful with only the standard provider.
