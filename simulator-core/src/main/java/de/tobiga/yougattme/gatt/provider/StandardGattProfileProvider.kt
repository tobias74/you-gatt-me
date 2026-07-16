package de.tobiga.yougattme.gatt.provider

import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.csc.CyclingSpeedAndCadenceProfile
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerProfile
import de.tobiga.yougattme.gatt.fitnessmachine.FitnessMachineProfile
import de.tobiga.yougattme.gatt.rsc.RunningSpeedAndCadenceProfile

class StandardGattProfileProvider : GattProfileProvider {
    override val providerId: String = "standard"

    override val profiles: List<GattProfileDefinition> = listOf(
        GattProfileDefinition(
            key = "ftms_bike",
            title = "FTMS Indoor Bike",
            subtitle = "Fitness Machine Service with Indoor Bike Data, Control Point, Status, and Training Status.",
            serviceUuid = "0x1826 / data 0x2AD2",
            selectionMode = ProfileSelectionMode.FTMS_MACHINE,
            ftmsMode = "indoor_bike"
        ),
        GattProfileDefinition(
            key = "ftms_rower",
            title = "FTMS Rower",
            subtitle = "Fitness Machine Service configured as a single Rower peripheral.",
            serviceUuid = "0x1826 / data 0x2AD1",
            selectionMode = ProfileSelectionMode.FTMS_MACHINE,
            ftmsMode = "rower"
        ),
        GattProfileDefinition(
            key = "ftms_treadmill",
            title = "FTMS Treadmill",
            subtitle = "Treadmill Data with spec-aligned Force on Belt and Power Output field pair.",
            serviceUuid = "0x1826 / data 0x2ACD",
            selectionMode = ProfileSelectionMode.FTMS_MACHINE,
            ftmsMode = "treadmill"
        ),
        GattProfileDefinition(
            key = "ftms_cross",
            title = "FTMS Cross Trainer",
            subtitle = "Cross Trainer Data with speed, step rate, distance, power, HR, and elapsed time.",
            serviceUuid = "0x1826 / data 0x2ACE",
            selectionMode = ProfileSelectionMode.FTMS_MACHINE,
            ftmsMode = "cross_trainer"
        ),
        GattProfileDefinition(
            key = "ftms_step",
            title = "FTMS Step Climber",
            subtitle = "Step Climber Data with floors, step count, step rate, energy, HR, and training time.",
            serviceUuid = "0x1826 / data 0x2ACF",
            selectionMode = ProfileSelectionMode.FTMS_MACHINE,
            ftmsMode = "step_climber"
        ),
        GattProfileDefinition(
            key = "ftms_stair",
            title = "FTMS Stair Climber",
            subtitle = "Stair Climber Data with floors, step rate, stride count, energy, HR, and training time.",
            serviceUuid = "0x1826 / data 0x2AD0",
            selectionMode = ProfileSelectionMode.FTMS_MACHINE,
            ftmsMode = "stair_climber"
        ),
        GattProfileDefinition(
            key = "csc",
            title = "Cycling Speed and Cadence",
            subtitle = "Standard CSC Measurement profile for wheel and crank revolution simulation.",
            serviceUuid = "0x1816 / measurement 0x2A5B",
            selectionMode = ProfileSelectionMode.OPTIONAL_SERVICE
        ),
        GattProfileDefinition(
            key = "cycling_power",
            title = "Cycling Power",
            subtitle = "Cycling Power Measurement with power and revolution data.",
            serviceUuid = "0x1818 / measurement 0x2A63",
            selectionMode = ProfileSelectionMode.OPTIONAL_SERVICE
        ),
        GattProfileDefinition(
            key = "rsc",
            title = "Running Speed and Cadence",
            subtitle = "Running Speed and Cadence Measurement for treadmill/running clients.",
            serviceUuid = "0x1814 / measurement 0x2A53",
            selectionMode = ProfileSelectionMode.OPTIONAL_SERVICE
        )
    )

    override fun createProfile(
        profileKey: String,
        dependencies: ProfileDependencies
    ): AbstractGattProfile? {
        val viewModel = dependencies.viewModel
        return when (profileKey) {
            "ftms_bike" -> FitnessMachineProfile("indoor_bike", viewModel)
            "ftms_rower" -> FitnessMachineProfile("rower", viewModel)
            "ftms_treadmill" -> FitnessMachineProfile("treadmill", viewModel)
            "ftms_cross" -> FitnessMachineProfile("cross_trainer", viewModel)
            "ftms_step" -> FitnessMachineProfile("step_climber", viewModel)
            "ftms_stair" -> FitnessMachineProfile("stair_climber", viewModel)
            "csc" -> CyclingSpeedAndCadenceProfile(viewModel)
            "cycling_power" -> CyclingPowerProfile(viewModel)
            "rsc" -> RunningSpeedAndCadenceProfile(viewModel)
            else -> null
        }
    }
}
