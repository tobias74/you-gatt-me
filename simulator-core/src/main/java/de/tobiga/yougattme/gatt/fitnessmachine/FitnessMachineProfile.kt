package de.tobiga.yougattme.gatt.fitnessmachine

import android.bluetooth.*
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.SingleServiceGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.fitnessmachine.characteristics.*
import java.util.*

/**
 * Implementation of the Bluetooth GATT Fitness Machine Profile.
 * This has been updated to be reactive to ViewModel changes.
 */
class FitnessMachineProfile(
    val machineType: String,
    val mainViewModel: ApplicationViewModel
) : SingleServiceGattProfile() {
    override val bridgeOutputKey: String = "ftms"

    private var dataCharacteristic: AbstractDataMeasurementCharacteristic
    private var controlPointCharacteristic: FitnessMachineControlPointCharacteristic? = null
    private var statusCharacteristic: FitnessMachineStatusCharacteristic
    private var trainingStatusCharacteristic: TrainingStatusCharacteristic

    val GATT_FITNESS_MACHINE_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")

    override fun getServiceUUIDs(): List<UUID> {
        return listOf(GATT_FITNESS_MACHINE_SERVICE_UUID)
    }

    init {
        // This when block remains the same.
        when (machineType) {
            "indoor_bike" -> {
                dataCharacteristic = IndoorBikeDataCharacteristic(this, mainViewModel.ftmsIndoorBikeData)
                activeCharacteristics.add(dataCharacteristic);
            }
            "rower" -> {
                dataCharacteristic = RowerDataCharacteristic(this, mainViewModel.ftmsRowerData)
                activeCharacteristics.add(dataCharacteristic)
            }
            "treadmill" -> {
                dataCharacteristic = TreadmillDataCharacteristic(this, mainViewModel.ftmsTreadmillData)
                activeCharacteristics.add(dataCharacteristic)
            }
            "cross_trainer" -> {
                dataCharacteristic = CrossTrainerDataCharacteristic(this, mainViewModel.ftmsCrossTrainerData)
                activeCharacteristics.add(dataCharacteristic)
            }
            "stair_climber" -> {
                dataCharacteristic = StairClimberDataCharacteristic(this, mainViewModel.ftmsStairClimberData)
                activeCharacteristics.add(dataCharacteristic)
            }
            "step_climber" -> {
                dataCharacteristic = StepClimberDataCharacteristic(this, mainViewModel.ftmsStepClimberData)
                activeCharacteristics.add(dataCharacteristic)
            }
            else -> {
                dataCharacteristic = IndoorBikeDataCharacteristic(this, mainViewModel.ftmsIndoorBikeData)
                activeCharacteristics.add(dataCharacteristic);
            }
        }

        statusCharacteristic = FitnessMachineStatusCharacteristic(this, mainViewModel)
        trainingStatusCharacteristic = TrainingStatusCharacteristic(this, mainViewModel)
        if (mainViewModel.ftmsControlPointEnabled) {
            controlPointCharacteristic = FitnessMachineControlPointCharacteristic(
                this,
                machineType,
                mainViewModel,
                statusCharacteristic,
                trainingStatusCharacteristic
            )
            activeCharacteristics.add(controlPointCharacteristic!!)
        }

        activeCharacteristics.add(statusCharacteristic)
        activeCharacteristics.add(trainingStatusCharacteristic)
        addSupportedRangeCharacteristics()
        activeCharacteristics.add(FitnessMachineFeatureCharacteristic(this, machineType, mainViewModel))
    }

    private fun addSupportedRangeCharacteristics() {
        if (!mainViewModel.ftmsControlPointEnabled) {
            return
        }
        val ranges = mainViewModel.ftmsSupportedRanges
        val declarations = mainViewModel.ftmsTargetFeatureDeclarationsForGatt()
        if (declarations.targetSpeed && supportsTargetSpeed()) {
            activeCharacteristics.add(SupportedSpeedRangeCharacteristic(this, ranges))
        }
        if (declarations.targetInclination && supportsTargetInclination()) {
            activeCharacteristics.add(SupportedInclinationRangeCharacteristic(this, ranges))
        }
        if (declarations.targetResistanceLevel && supportsTargetResistance()) {
            activeCharacteristics.add(SupportedResistanceLevelRangeCharacteristic(this, ranges))
        }
        if (declarations.targetHeartRate) {
            activeCharacteristics.add(SupportedHeartRateRangeCharacteristic(this, ranges))
        }
        if (declarations.targetPower && supportsTargetPower()) {
            activeCharacteristics.add(SupportedPowerRangeCharacteristic(this, ranges))
        }
    }

    private fun supportsTargetSpeed(): Boolean = machineType in setOf("treadmill", "cross_trainer")
    private fun supportsTargetInclination(): Boolean = machineType in setOf("treadmill", "cross_trainer")
    private fun supportsTargetResistance(): Boolean = machineType in setOf("indoor_bike", "rower", "cross_trainer")
    private fun supportsTargetPower(): Boolean = machineType in setOf("indoor_bike", "treadmill", "rower", "cross_trainer")

    /**
     * This method MUST be called when the profile is deactivated to prevent memory leaks
     * from the `observeForever` calls.
     */
    override fun destroy() {
        super.destroy()
    }


    override fun clearAllDevices() {
        clearDeviceMtus()
        dataCharacteristic.clientConfigDescriptor.clearDevices()
        statusCharacteristic.clientConfigDescriptor.clearDevices()
        trainingStatusCharacteristic.clientConfigDescriptor.clearDevices()
        controlPointCharacteristic?.clearDevicesAndControl()
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        forgetDeviceMtu(device)
        dataCharacteristic.clientConfigDescriptor.removeDevice(device)
        statusCharacteristic.clientConfigDescriptor.removeDevice(device)
        trainingStatusCharacteristic.clientConfigDescriptor.removeDevice(device)
        controlPointCharacteristic?.onDeviceDisconnected(device)
    }

    override fun triggerPeriodicTimer() {
        val logicalRecord = dataCharacteristic.getData()
        val previewPayloadBytes = dataCharacteristic.clientConfigDescriptor.registeredDevices
            .minOfOrNull(::getNotificationPayloadSize)
            ?: 20
        mainViewModel.recordFtmsMeasurement(
            machineType = machineType,
            characteristicUuid = dataCharacteristic.getCharacteristicUUID().toString(),
            logicalRecord = logicalRecord,
            packets = dataCharacteristic.getNotificationPackets(previewPayloadBytes),
            previewPayloadBytes = previewPayloadBytes
        )
        dataCharacteristic.publishMeasurementData()
    }
}
