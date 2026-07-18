package de.tobiga.yougattme.gatt.provider

import android.content.Context
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.SimulationStateStore
import de.tobiga.yougattme.bridge.ControlCommand
import de.tobiga.yougattme.bridge.Metric
import de.tobiga.yougattme.bridge.StandardService
import de.tobiga.yougattme.gatt.AbstractGattProfile

enum class ProfileSelectionMode {
    FTMS_MACHINE,
    OPTIONAL_SERVICE
}

data class GattProfileDefinition(
    val key: String,
    val title: String,
    val subtitle: String,
    val serviceUuid: String,
    val selectionMode: ProfileSelectionMode,
    val ftmsMode: String? = null,
    val exclusiveGroup: String? = null,
    val bridgeOutput: BridgeOutputDefinition? = null,
    val configurationActivityClassName: String? = null
)

/** Optional, protocol-neutral route exposed by a profile provider. */
data class BridgeOutputDefinition(
    val outputKey: String,
    val title: String,
    val protocolId: String,
    val standardService: StandardService? = null,
    val requiredMetrics: Set<Metric>,
    val routableMetrics: Set<Metric> = requiredMetrics,
    val compatibleControls: Set<ControlCommand.Kind> = emptySet(),
    val supportsCapabilityMirroring: Boolean = false
)

data class ProfileDependencies(
    val viewModel: ApplicationViewModel,
    val stateStore: SimulationStateStore = viewModel.stateStore,
    val context: Context? = null
)

interface GattProfileProvider {
    val providerId: String
    val profiles: List<GattProfileDefinition>

    fun createProfile(profileKey: String, dependencies: ProfileDependencies): AbstractGattProfile?
}
