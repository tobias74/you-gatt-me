package de.tobiga.yougattme.gatt.provider

import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.SimulationStateStore
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
    val exclusiveGroup: String? = null
)

data class ProfileDependencies(
    val viewModel: ApplicationViewModel,
    val stateStore: SimulationStateStore = viewModel.stateStore
)

interface GattProfileProvider {
    val providerId: String
    val profiles: List<GattProfileDefinition>

    fun createProfile(profileKey: String, dependencies: ProfileDependencies): AbstractGattProfile?
}
