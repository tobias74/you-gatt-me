# Extension Providers

YouGattMe exposes a neutral provider API so additional GATT profile sets can be
added without changing the core simulator UI and BLE lifecycle code.

## Provider API

The public API lives in:

```text
simulator-core/src/main/java/de/tobiga/yougattme/gatt/provider/
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

A definition may expose an independent bridge route with
`BridgeOutputDefinition`. The route has a neutral `protocolId`, required and
routable metrics, compatible semantic controls, optional capability mirroring,
and an optional provider configuration activity. The app renders these controls
without knowing the provider's protocol.

Input-side extensions implement `InputAdapterProvider` and register it through
manifest metadata with the `de.tobiga.yougattme.INPUT_ADAPTER_PROVIDER.` prefix.
`InputAdapter` supports provider-defined protocol identities, advertisement
matching, post-subscription discovery transactions, preferred GATT write type,
first-measurement readiness, semantic controls, and responses delivered on a
different notification characteristic.

Cross-protocol controls use `ControlCommand` and `ControlResult`. Providers must
reject commands without an exact or defensible mapping; raw same-protocol
forwarding remains available to the built-in standard profiles.

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
- `heart_rate`

## Adding Providers

Additional providers should:

1. Implement `GattProfileProvider`.
2. Return stable, unique profile keys.
3. Use `ProfileDependencies` instead of reaching into Android singletons directly.
4. Return profiles that extend `AbstractGattProfile`.
5. Avoid changing `ApplicationState`; it should only receive profile instances.
6. Keep optional discovery transactions non-fatal when missing data only degrades
   capability information.

The app loads the standard provider and can load extension providers through
generic manifest metadata. Extension builds may also use ignored local Gradle
hooks to add provider modules without changing tracked public files.

Generic local hooks:

```text
local.settings.gradle
local.extensions.gradle
```

Both files are ignored by Git.

## Compatibility Rules

- Provider keys must be unique.
- Provider definitions should describe standard service UUIDs or generic custom
  service behavior without requiring app-specific UI assumptions.
- The public app must continue to build and run when only the standard provider
  is present.
