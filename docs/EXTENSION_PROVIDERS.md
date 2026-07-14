# Extension Providers

YouGattMe exposes a neutral provider API so additional GATT profile sets can be
added without changing the core simulator UI and BLE lifecycle code.

## Provider API

The public API lives in:

```text
app/src/main/java/de/tobiga/yougattme/gatt/provider/
```

Core types:

```kotlin
interface GattProfileProvider {
    val providerId: String
    val profiles: List<GattProfileDefinition>

    fun createProfile(
        profileKey: String,
        dependencies: ProfileDependencies
    ): AbstractGattProfile?
}
```

`GattProfileDefinition` provides UI metadata and selection behavior. `createProfile`
returns the concrete `AbstractGattProfile` instance for the selected definition.

## Standard Provider

The built-in provider is `StandardGattProfileProvider`. It registers the standard
fitness profile definitions and creates the corresponding profile implementations.

Current standard provider entries:

- `ftms_bike`
- `ftms_rower`
- `ftms_treadmill`
- `ftms_cross`
- `ftms_step`
- `ftms_stair`
- `csc`
- `cycling_power`
- `rsc`

## Adding Providers

Additional providers should:

1. Implement `GattProfileProvider`.
2. Return stable, unique profile keys.
3. Use `ProfileDependencies` instead of reaching into Android singletons directly.
4. Return profiles that extend `AbstractGattProfile`.
5. Avoid changing `ApplicationState`; it should only receive profile instances.

The current app registers providers in `MainActivity.profileProviders`. A later
module split can replace that list with manifest metadata discovery or another
build-time registration mechanism.

## Compatibility Rules

- Provider keys must be unique.
- Provider definitions should describe standard service UUIDs or generic custom
  service behavior without requiring app-specific UI assumptions.
- The public app must continue to build and run when only the standard provider
  is present.
