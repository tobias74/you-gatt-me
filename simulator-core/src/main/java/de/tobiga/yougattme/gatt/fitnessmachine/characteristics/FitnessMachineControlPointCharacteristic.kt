package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.TobigaGattCharacteristic
import de.tobiga.yougattme.gatt.common.ClientConfigDescriptor
import de.tobiga.yougattme.gatt.common.GattNotificationDispatcher
import de.tobiga.yougattme.gatt.common.UserDescriptor
import de.tobiga.yougattme.gatt.fitnessmachine.FTMS_SPIN_DOWN_STATUS_REQUESTED
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsHeartRateZoneTargets
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsSpinDownTarget
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsTargetState
import de.tobiga.yougattme.gatt.fitnessmachine.IndoorCyclingSimulationParameters
import de.tobiga.yougattme.tools.toHex
import de.tobiga.yougattme.bridge.RemoteControlDispatcher
import de.tobiga.yougattme.bridge.ControlCommand
import de.tobiga.yougattme.bridge.ControlStatus
import de.tobiga.yougattme.bridge.StandardService
import java.util.UUID

object REQUEST_CODE {
    const val REQUEST_CONTROL = 0x00
    const val RESET = 0x01
    const val SET_TARGET_SPEED = 0x02
    const val SET_TARGET_INCLINATION = 0x03
    const val SET_TARGET_RESISTANCE_LEVEL = 0x04
    const val SET_TARGET_POWER = 0x05
    const val SET_TARGET_HEART_RATE = 0x06
    const val START_OR_RESUME = 0x07
    const val STOP_OR_PAUSE = 0x08
    const val SET_TARGETED_EXPENDED_ENERGY = 0x09
    const val SET_TARGETED_NUMBER_OF_STEPS = 0x0A
    const val SET_TARGETED_NUMBER_OF_STRIDES = 0x0B
    const val SET_TARGETED_DISTANCE = 0x0C
    const val SET_TARGETED_TRAINING_TIME = 0x0D
    const val SET_TARGETED_TIME_IN_TWO_HEART_RATE_ZONES = 0x0E
    const val SET_TARGETED_TIME_IN_THREE_HEART_RATE_ZONES = 0x0F
    const val SET_TARGETED_TIME_IN_FIVE_HEART_RATE_ZONES = 0x10
    const val SET_INDOOR_BIKE_SIMULATION = 0x11
    const val SET_WHEEL_CIRCUMFERENCE = 0x12
    const val SPIN_DOWN_CONTROL = 0x13
    const val SET_TARGETED_CADENCE = 0x14
}

const val RESPONSE_CODE = 0x80

object RESULT_CODE {
    const val SUCCESS = 0x01
    const val OP_CODE_NOT_SUPPORTED = 0x02
    const val INVALID_PARAMETER = 0x03
    const val OPERATION_FAILED = 0x04
    const val CONTROL_NOT_PERMITTED = 0x05
}

private const val ATT_ERROR_PROCEDURE_ALREADY_IN_PROGRESS = 0x80
private const val ATT_ERROR_CCCD_IMPROPERLY_CONFIGURED = 0x81
private const val SPIN_DOWN_START = 0x01
private const val SPIN_DOWN_IGNORE = 0x02

private data class ControlPointProcedureResult(
    val resultCode: Int,
    val responseParameters: ByteArray = ByteArray(0)
)

class FitnessMachineControlPointCharacteristic(
    gattProfile: AbstractGattProfile,
    private val machineType: String,
    private val mainViewModel: ApplicationViewModel,
    private val statusCharacteristic: FitnessMachineStatusCharacteristic,
    private val trainingStatusCharacteristic: TrainingStatusCharacteristic
) : TobigaGattCharacteristic(gattProfile) {

    var clientConfigDescriptor: ClientConfigDescriptor
    private var controlOwner: BluetoothDevice? = null
    private var procedureInProgress = false
    private var procedureDeviceAddress: String? = null

    private val GATT_FITNESS_MACHINE_CONTROL_POINT_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002AD9-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID = GATT_FITNESS_MACHINE_CONTROL_POINT_CHARACTERISTIC_UUID

    init {
        clientConfigDescriptor = ClientConfigDescriptor(
            gattProfile,
            supportsNotifications = false,
            supportsIndications = true
        )
        activeDescriptors.add(clientConfigDescriptor)
        activeDescriptors.add(UserDescriptor(gattProfile))

        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_FITNESS_MACHINE_CONTROL_POINT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        gattProfile.bluetoothGattServer!!.sendResponse(
            device,
            requestId,
            BluetoothGatt.GATT_READ_NOT_PERMITTED,
            0,
            null
        )
    }

    override fun onWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
        Log.d("FitnessMachineControlPoint", "Write from ${device.address}: ${value.toHex()}")
        if (value.isNotEmpty()) {
            val requestCode = value[0].toInt() and 0xFF
            mainViewModel.recordFtmsProtocolEvent(
                direction = "RX",
                channel = "CONTROL",
                title = "${requestName(requestCode)} · ${device.address}",
                payload = value
            )
        }

        if (offset != 0 || preparedWrite || value.isEmpty()) {
            sendWriteResponse(device, requestId, responseNeeded, BluetoothGatt.GATT_FAILURE)
            return
        }

        if (!clientConfigDescriptor.isIndicationEnabled(device)) {
            sendWriteResponse(device, requestId, responseNeeded, ATT_ERROR_CCCD_IMPROPERLY_CONFIGURED)
            return
        }

        if (procedureInProgress) {
            sendWriteResponse(device, requestId, responseNeeded, ATT_ERROR_PROCEDURE_ALREADY_IN_PROGRESS)
            return
        }

        sendWriteResponse(device, requestId, responseNeeded, BluetoothGatt.GATT_SUCCESS)

        procedureInProgress = true
        procedureDeviceAddress = device.address
        statusCharacteristic.beginDeferredNotifications(excludedDeviceAddress = device.address)
        trainingStatusCharacteristic.beginDeferredNotifications()
        val requestCode = value[0].toInt() and 0xFF
        mainViewModel.ftmsControlProcedureState.postValue(
            "Awaiting confirmation · ${requestName(requestCode)}"
        )
        if (requestCode == REQUEST_CODE.REQUEST_CONTROL) {
            if (value.size != 1) {
                sendControlPointResponse(device, requestCode, RESULT_CODE.INVALID_PARAMETER, ByteArray(0))
                return
            }
            if (controlOwner != null && !isControlOwner(device)) {
                sendControlPointResponse(device, requestCode, RESULT_CODE.CONTROL_NOT_PERMITTED, ByteArray(0))
                return
            }
        } else {
            if (!isSupportedControlProcedure(requestCode)) {
                sendControlPointResponse(device, requestCode, RESULT_CODE.OP_CODE_NOT_SUPPORTED, ByteArray(0))
                return
            }
            if (!isControlOwner(device)) {
                sendControlPointResponse(device, requestCode, RESULT_CODE.CONTROL_NOT_PERMITTED, ByteArray(0))
                return
            }
        }
        val semanticCommand = semanticCommand(requestCode, value)
        if (semanticCommand != null && RemoteControlDispatcher.forwardSemanticIfConfigured(
                outputKey = "ftms",
                command = semanticCommand,
                sameProtocolId = StandardService.FTMS.name
            ) { remote ->
                if (!procedureInProgress || procedureDeviceAddress != device.address) return@forwardSemanticIfConfigured
                var resultCode = when (remote.status) {
                    ControlStatus.SUCCESS -> RESULT_CODE.SUCCESS
                    ControlStatus.UNSUPPORTED -> RESULT_CODE.OP_CODE_NOT_SUPPORTED
                    ControlStatus.INVALID_PARAMETER -> RESULT_CODE.INVALID_PARAMETER
                    ControlStatus.REJECTED -> RESULT_CODE.CONTROL_NOT_PERMITTED
                    else -> RESULT_CODE.OPERATION_FAILED
                }
                if (requestCode == REQUEST_CODE.REQUEST_CONTROL && resultCode == RESULT_CODE.SUCCESS) {
                    controlOwner = device
                    mainViewModel.controlHasBeenTakenInControlPoint.postValue(true)
                    mainViewModel.ftmsControlOwnerAddress.postValue(device.address)
                } else if (resultCode == RESULT_CODE.SUCCESS) {
                    val local = handleProcedure(device, requestCode, value)
                    if (local.resultCode != RESULT_CODE.SUCCESS) resultCode = RESULT_CODE.OPERATION_FAILED
                }
                sendControlPointResponse(device, requestCode, resultCode, remote.responseData)
            }) return

        if (RemoteControlDispatcher.forwardIfConfigured(
                outputKey = "ftms",
                request = value,
                calibration = requestCode == REQUEST_CODE.SPIN_DOWN_CONTROL
            ) { remote ->
                if (!procedureInProgress || procedureDeviceAddress != device.address) return@forwardIfConfigured
                val response = remote.response
                val valid = remote.success && response != null && response.size >= 3 &&
                    (response[0].toInt() and 0xFF) == RESPONSE_CODE &&
                    (response[1].toInt() and 0xFF) == requestCode
                var resultCode = if (valid) response!![2].toInt() and 0xFF else RESULT_CODE.OPERATION_FAILED
                val parameters = if (valid) response!!.copyOfRange(3, response.size) else ByteArray(0)
                if (requestCode == REQUEST_CODE.REQUEST_CONTROL && resultCode == RESULT_CODE.SUCCESS) {
                    controlOwner = device
                    mainViewModel.controlHasBeenTakenInControlPoint.postValue(true)
                    mainViewModel.ftmsControlOwnerAddress.postValue(device.address)
                } else if (resultCode == RESULT_CODE.SUCCESS) {
                    val local = handleProcedure(device, requestCode, value)
                    if (local.resultCode != RESULT_CODE.SUCCESS) resultCode = RESULT_CODE.OPERATION_FAILED
                }
                sendControlPointResponse(device, requestCode, resultCode, parameters)
            }) {
            return
        }
        val result = handleProcedure(device, requestCode, value)
        sendControlPointResponse(device, requestCode, result.resultCode, result.responseParameters)
    }

    private fun semanticCommand(requestCode: Int, value: ByteArray): ControlCommand? {
        fun u16(offset: Int): Int = (value[offset].toInt() and 0xFF) or
            ((value[offset + 1].toInt() and 0xFF) shl 8)
        fun s16(offset: Int): Int = u16(offset).toShort().toInt()
        return when (requestCode) {
            REQUEST_CODE.REQUEST_CONTROL -> value.takeIf { it.size == 1 }?.let { ControlCommand.RequestControl }
            REQUEST_CODE.RESET -> value.takeIf { it.size == 1 }?.let { ControlCommand.Reset }
            REQUEST_CODE.START_OR_RESUME -> value.takeIf { it.size == 1 }?.let { ControlCommand.Start }
            REQUEST_CODE.STOP_OR_PAUSE -> value.takeIf { it.size == 2 }?.let {
                ControlCommand.Stop(pause = (it[1].toInt() and 0xFF) == 0x02)
            }
            REQUEST_CODE.SET_TARGET_POWER -> value.takeIf { it.size == 3 }?.let {
                val watts = s16(1).toDouble()
                watts.takeIf { requested ->
                    requested in mainViewModel.ftmsSupportedRanges.minPowerWatt.toDouble()..
                        mainViewModel.ftmsSupportedRanges.maxPowerWatt.toDouble()
                }?.let(ControlCommand::TargetPower)
            }
            REQUEST_CODE.SET_TARGET_RESISTANCE_LEVEL -> value.takeIf { it.size == 3 }?.let {
                val range = mainViewModel.ftmsSupportedRanges
                val minimum = range.minResistanceLevel.toDouble()
                val maximum = range.maxResistanceLevel.toDouble()
                if (maximum <= minimum) null else {
                    val level = s16(1) / 10.0
                    if (level !in minimum..maximum) null
                    else ControlCommand.BasicResistance((level - minimum) * 100.0 / (maximum - minimum))
                }
            }
            REQUEST_CODE.SET_INDOOR_BIKE_SIMULATION -> value.takeIf { it.size == 7 }?.let {
                ControlCommand.SimulationParameters(
                    windSpeedMetersPerSecond = s16(1) / 1000.0,
                    gradePercent = s16(3) / 100.0,
                    rollingResistanceCoefficient = (it[5].toInt() and 0xFF) / 10_000.0,
                    windResistanceCoefficient = (it[6].toInt() and 0xFF) / 100.0
                )
            }
            REQUEST_CODE.SET_WHEEL_CIRCUMFERENCE -> value.takeIf { it.size == 3 }
                ?.let { ControlCommand.WheelConfiguration(u16(1) / 10_000.0) }
            REQUEST_CODE.SPIN_DOWN_CONTROL -> value.takeIf { it.size == 2 && (it[1].toInt() and 0xFF) == SPIN_DOWN_START }
                ?.let { ControlCommand.Calibration(spinDown = true) }
            else -> null
        }
    }

    private fun handleProcedure(device: BluetoothDevice, requestCode: Int, value: ByteArray): ControlPointProcedureResult {
        if (requestCode == REQUEST_CODE.REQUEST_CONTROL) {
            return handleRequestControl(device, value)
        }

        if (!isSupportedControlProcedure(requestCode)) {
            return ControlPointProcedureResult(RESULT_CODE.OP_CODE_NOT_SUPPORTED)
        }

        if (!isControlOwner(device)) {
            return ControlPointProcedureResult(RESULT_CODE.CONTROL_NOT_PERMITTED)
        }

        return when (requestCode) {
            REQUEST_CODE.RESET -> handleReset(value)
            REQUEST_CODE.SET_TARGET_SPEED -> handleSetTargetSpeed(value)
            REQUEST_CODE.SET_TARGET_INCLINATION -> handleSetTargetInclination(value)
            REQUEST_CODE.SET_TARGET_RESISTANCE_LEVEL -> handleSetTargetResistanceLevel(value)
            REQUEST_CODE.SET_TARGET_POWER -> handleSetTargetPower(value)
            REQUEST_CODE.SET_TARGET_HEART_RATE -> handleSetTargetHeartRate(value)
            REQUEST_CODE.START_OR_RESUME -> handleStartOrResume(value)
            REQUEST_CODE.STOP_OR_PAUSE -> handleStopOrPause(value)
            REQUEST_CODE.SET_TARGETED_EXPENDED_ENERGY -> handleSetTargetedExpendedEnergy(value)
            REQUEST_CODE.SET_TARGETED_NUMBER_OF_STEPS -> handleSetTargetedNumberOfSteps(value)
            REQUEST_CODE.SET_TARGETED_NUMBER_OF_STRIDES -> handleSetTargetedNumberOfStrides(value)
            REQUEST_CODE.SET_TARGETED_DISTANCE -> handleSetTargetedDistance(value)
            REQUEST_CODE.SET_TARGETED_TRAINING_TIME -> handleSetTargetedTrainingTime(value)
            REQUEST_CODE.SET_TARGETED_TIME_IN_TWO_HEART_RATE_ZONES -> handleSetTargetedTimeInHeartRateZones(value, 2)
            REQUEST_CODE.SET_TARGETED_TIME_IN_THREE_HEART_RATE_ZONES -> handleSetTargetedTimeInHeartRateZones(value, 3)
            REQUEST_CODE.SET_TARGETED_TIME_IN_FIVE_HEART_RATE_ZONES -> handleSetTargetedTimeInHeartRateZones(value, 5)
            REQUEST_CODE.SET_INDOOR_BIKE_SIMULATION -> handleSetIndoorBikeSimulationParameters(value)
            REQUEST_CODE.SET_WHEEL_CIRCUMFERENCE -> handleSetWheelCircumference(value)
            REQUEST_CODE.SPIN_DOWN_CONTROL -> handleSpinDownControl(value)
            REQUEST_CODE.SET_TARGETED_CADENCE -> handleSetTargetedCadence(value)
            else -> ControlPointProcedureResult(RESULT_CODE.OP_CODE_NOT_SUPPORTED)
        }
    }

    private fun isSupportedControlProcedure(requestCode: Int): Boolean {
        val targetFeatures = mainViewModel.ftmsTargetFeatures
        return when (requestCode) {
            REQUEST_CODE.RESET,
            REQUEST_CODE.START_OR_RESUME,
            REQUEST_CODE.STOP_OR_PAUSE -> true
            REQUEST_CODE.SET_TARGET_SPEED -> targetFeatures.targetSpeed && supportsTargetSpeed()
            REQUEST_CODE.SET_TARGET_INCLINATION -> targetFeatures.targetInclination && supportsTargetInclination()
            REQUEST_CODE.SET_TARGET_RESISTANCE_LEVEL -> targetFeatures.targetResistanceLevel && supportsTargetResistance()
            REQUEST_CODE.SET_TARGET_POWER -> targetFeatures.targetPower && supportsTargetPower()
            REQUEST_CODE.SET_TARGET_HEART_RATE -> targetFeatures.targetHeartRate
            REQUEST_CODE.SET_TARGETED_EXPENDED_ENERGY -> targetFeatures.targetedExpendedEnergy
            REQUEST_CODE.SET_TARGETED_NUMBER_OF_STEPS -> targetFeatures.targetedSteps && supportsTargetSteps()
            REQUEST_CODE.SET_TARGETED_NUMBER_OF_STRIDES -> targetFeatures.targetedStrides && supportsTargetStrides()
            REQUEST_CODE.SET_TARGETED_DISTANCE -> targetFeatures.targetedDistance && supportsTargetDistance()
            REQUEST_CODE.SET_TARGETED_TRAINING_TIME -> targetFeatures.targetedTrainingTime
            REQUEST_CODE.SET_TARGETED_TIME_IN_TWO_HEART_RATE_ZONES -> targetFeatures.targetedTimeInTwoHeartRateZones
            REQUEST_CODE.SET_TARGETED_TIME_IN_THREE_HEART_RATE_ZONES -> targetFeatures.targetedTimeInThreeHeartRateZones
            REQUEST_CODE.SET_TARGETED_TIME_IN_FIVE_HEART_RATE_ZONES -> targetFeatures.targetedTimeInFiveHeartRateZones
            REQUEST_CODE.SET_INDOOR_BIKE_SIMULATION -> targetFeatures.indoorBikeSimulationParameters && supportsIndoorBikeSimulation()
            REQUEST_CODE.SET_WHEEL_CIRCUMFERENCE -> targetFeatures.wheelCircumference && supportsWheelCircumference()
            REQUEST_CODE.SPIN_DOWN_CONTROL -> targetFeatures.spinDownControl && supportsSpinDown()
            REQUEST_CODE.SET_TARGETED_CADENCE -> targetFeatures.targetedCadence && supportsTargetCadence()
            else -> false
        }
    }

    private fun supportsTargetSpeed(): Boolean = machineType in setOf("treadmill", "cross_trainer")
    private fun supportsTargetInclination(): Boolean = machineType in setOf("treadmill", "cross_trainer")
    private fun supportsTargetResistance(): Boolean = machineType in setOf("indoor_bike", "rower", "cross_trainer")
    private fun supportsTargetPower(): Boolean = machineType in setOf("indoor_bike", "treadmill", "rower", "cross_trainer")
    private fun supportsTargetSteps(): Boolean = machineType in setOf("cross_trainer", "step_climber")
    private fun supportsTargetStrides(): Boolean = machineType in setOf("cross_trainer", "stair_climber")
    private fun supportsTargetDistance(): Boolean = machineType in setOf("indoor_bike", "treadmill", "rower", "cross_trainer")
    private fun supportsTargetCadence(): Boolean = machineType in setOf("indoor_bike", "rower", "cross_trainer")
    private fun supportsIndoorBikeSimulation(): Boolean = machineType == "indoor_bike"
    private fun supportsWheelCircumference(): Boolean = machineType == "indoor_bike"
    private fun supportsSpinDown(): Boolean = machineType == "indoor_bike"

    private fun handleReset(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 1) {
            return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        }
        clearControlOwner()
        mainViewModel.fitnessMachineHasBeenStarted.postValue(false)
        mainViewModel.ftmsIndoorBikeData.resetSession()
        mainViewModel.ftmsTreadmillData.resetSession()
        mainViewModel.ftmsRowerData.resetSession()
        mainViewModel.ftmsCrossTrainerData.resetSession()
        mainViewModel.ftmsStepClimberData.resetSession()
        mainViewModel.ftmsStairClimberData.resetSession()
        mainViewModel.ftmsIndoorBikeData.targetDurationSeconds = 0
        mainViewModel.ftmsTreadmillData.targetDurationSeconds = 0
        mainViewModel.ftmsRowerData.targetDurationSeconds = 0
        mainViewModel.ftmsCrossTrainerData.targetDurationSeconds = 0
        mainViewModel.ftmsStepClimberData.targetDurationSeconds = 0
        mainViewModel.ftmsStairClimberData.targetDurationSeconds = 0
        mainViewModel.resetFtmsControlState()
        trainingStatusCharacteristic.setTrainingStatus(TRAINING_STATUS_CODES.IDLE)
        statusCharacteristic.sendResetStatus()
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleStartOrResume(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 1) {
            return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        }
        mainViewModel.fitnessMachineHasBeenStarted.postValue(true)
        trainingStatusCharacteristic.setTrainingStatus(TRAINING_STATUS_CODES.MANUAL_MODE)
        statusCharacteristic.sendStartedOrResumedStatus()
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleStopOrPause(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 2 || (value[1].toInt() and 0xFF) !in 1..2) {
            return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        }
        val isPause = (value[1].toInt() and 0xFF) == 2
        mainViewModel.fitnessMachineHasBeenStarted.postValue(false)
        trainingStatusCharacteristic.setTrainingStatus(TRAINING_STATUS_CODES.IDLE)
        statusCharacteristic.sendStoppedStatus(isPause)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetTargetSpeed(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 3) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val rawSpeed = readUInt16(value, 1)
        val speedKmh = rawSpeed / 100f
        val ranges = mainViewModel.ftmsSupportedRanges
        if (!ranges.supportsSpeedRaw(rawSpeed)) {
            return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        }
        updateTargets { it.copy(speedKmh = speedKmh) }
        statusCharacteristic.sendTargetSpeedChanged(speedKmh)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetTargetInclination(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 3) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val rawInclination = readSInt16(value, 1)
        val inclinationPercent = rawInclination / 10f
        val ranges = mainViewModel.ftmsSupportedRanges
        if (!ranges.supportsInclinationRaw(rawInclination)) {
            return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        }
        mainViewModel.ftmsReceivedTargetInclination.postValue(inclinationPercent.toDouble())
        updateTargets { it.copy(inclinationPercent = inclinationPercent) }
        statusCharacteristic.sendTargetInclinationChanged(inclinationPercent.toDouble())
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetTargetResistanceLevel(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 2) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val rawResistance = value[1].toInt() and 0xFF
        val resistanceLevel = rawResistance / 10f
        val ranges = mainViewModel.ftmsSupportedRanges
        if (!ranges.supportsResistanceRaw(rawResistance)) {
            return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        }
        updateTargets { it.copy(resistanceLevel = resistanceLevel) }
        statusCharacteristic.sendTargetResistanceLevelChanged(resistanceLevel)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetTargetPower(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 3) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val power = readSInt16(value, 1)
        val ranges = mainViewModel.ftmsSupportedRanges
        if (!ranges.supportsPower(power)) {
            return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        }
        mainViewModel.receivedTargetPowerInControlPoint.postValue(power)
        updateTargets { it.copy(powerWatt = power) }
        statusCharacteristic.sendTargetPowerChanged(power)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetTargetHeartRate(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 2) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val heartRate = value[1].toInt() and 0xFF
        val ranges = mainViewModel.ftmsSupportedRanges
        if (!ranges.supportsHeartRate(heartRate)) {
            return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        }
        updateTargets { it.copy(heartRateBpm = heartRate) }
        statusCharacteristic.sendTargetHeartRateChanged(heartRate)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetTargetedExpendedEnergy(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 3) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val kcal = readUInt16(value, 1)
        updateTargets { it.copy(expendedEnergyKcal = kcal) }
        statusCharacteristic.sendTargetedExpendedEnergyChanged(kcal)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetTargetedNumberOfSteps(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 3) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val steps = readUInt16(value, 1)
        updateTargets { it.copy(steps = steps) }
        statusCharacteristic.sendTargetedNumberOfStepsChanged(steps)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetTargetedNumberOfStrides(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 3) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val strides = readUInt16(value, 1)
        updateTargets { it.copy(strides = strides) }
        statusCharacteristic.sendTargetedNumberOfStridesChanged(strides)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetTargetedDistance(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 4) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val distanceMeters = readUInt24(value, 1)
        updateTargets { it.copy(distanceMeters = distanceMeters) }
        statusCharacteristic.sendTargetedDistanceChanged(distanceMeters)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetTargetedTrainingTime(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 3) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val seconds = readUInt16(value, 1)
        mainViewModel.ftmsIndoorBikeData.targetDurationSeconds = seconds
        mainViewModel.ftmsTreadmillData.targetDurationSeconds = seconds
        mainViewModel.ftmsRowerData.targetDurationSeconds = seconds
        mainViewModel.ftmsCrossTrainerData.targetDurationSeconds = seconds
        mainViewModel.ftmsStepClimberData.targetDurationSeconds = seconds
        mainViewModel.ftmsStairClimberData.targetDurationSeconds = seconds
        updateTargets { it.copy(trainingTimeSeconds = seconds) }
        statusCharacteristic.sendTargetedTrainingTimeChanged(seconds)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetTargetedTimeInHeartRateZones(
        value: ByteArray,
        zoneCount: Int
    ): ControlPointProcedureResult {
        if (value.size != 1 + zoneCount * 2) {
            return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        }

        val seconds = (0 until zoneCount).map { index ->
            readUInt16(value, 1 + index * 2)
        }
        val zoneTargets = FtmsHeartRateZoneTargets(zoneCount, seconds)
        updateTargets { it.copy(heartRateZoneTargets = zoneTargets) }
        when (zoneCount) {
            2 -> statusCharacteristic.sendTargetedTimeInTwoHeartRateZonesChanged(seconds)
            3 -> statusCharacteristic.sendTargetedTimeInThreeHeartRateZonesChanged(seconds)
            5 -> statusCharacteristic.sendTargetedTimeInFiveHeartRateZonesChanged(seconds)
        }
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetIndoorBikeSimulationParameters(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 7) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val parameters = IndoorCyclingSimulationParameters(
            windSpeedMps = readSInt16(value, 1) / 1000f,
            gradePercent = readSInt16(value, 3) / 100.0,
            crr = (value[5].toInt() and 0xFF) / 10000f,
            cw = (value[6].toInt() and 0xFF) / 100f
        )

        mainViewModel.updateSimulationParameters(
            parameters.windSpeedMps,
            parameters.gradePercent,
            parameters.crr,
            parameters.cw
        )
        mainViewModel.receivedIndoorBikeGrade.postValue(parameters.gradePercent)
        updateTargets { it.copy(indoorBikeSimulation = parameters) }
        statusCharacteristic.sendIndoorBikeSimulationParametersChanged(parameters)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSetWheelCircumference(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 3) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val rawTenthsMm = readUInt16(value, 1)
        if (rawTenthsMm == 0) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)

        val circumferenceMm = rawTenthsMm / 10f
        mainViewModel.postWheelCircumference(rawTenthsMm / 100f)
        updateTargets { it.copy(wheelCircumferenceMm = circumferenceMm) }
        statusCharacteristic.sendWheelCircumferenceChanged(rawTenthsMm)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleSpinDownControl(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 2) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val controlParameter = value[1].toInt() and 0xFF
        return when (controlParameter) {
            SPIN_DOWN_START -> handleSpinDownStart()
            SPIN_DOWN_IGNORE -> {
                updateTargets {
                    it.copy(spinDown = FtmsSpinDownTarget(FTMS_SPIN_DOWN_STATUS_REQUESTED, ignored = true))
                }
                ControlPointProcedureResult(RESULT_CODE.SUCCESS)
            }
            else -> ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        }
    }

    private fun handleSpinDownStart(): ControlPointProcedureResult {
        val ranges = mainViewModel.ftmsSupportedRanges
        val lowKmh = ranges.spinDownTargetSpeedLowKmh.coerceIn(ranges.minSpeedKmh, ranges.maxSpeedKmh)
        val highKmh = ranges.spinDownTargetSpeedHighKmh.coerceIn(lowKmh, ranges.maxSpeedKmh)
        val writer = FtmsPacketWriter()
        writer.u16(ranges.speedRaw(lowKmh))
        writer.u16(ranges.speedRaw(highKmh))

        updateTargets {
            it.copy(
                spinDown = FtmsSpinDownTarget(
                    status = FTMS_SPIN_DOWN_STATUS_REQUESTED,
                    targetSpeedLowKmh = lowKmh,
                    targetSpeedHighKmh = highKmh
                )
            )
        }
        statusCharacteristic.sendSpinDownStatus(FTMS_SPIN_DOWN_STATUS_REQUESTED)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS, writer.bytes())
    }

    private fun handleSetTargetedCadence(value: ByteArray): ControlPointProcedureResult {
        if (value.size != 3) return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        val cadenceRpm = readUInt16(value, 1) / 2f
        updateTargets { it.copy(cadenceRpm = cadenceRpm) }
        statusCharacteristic.sendTargetedCadenceChanged(cadenceRpm)
        return ControlPointProcedureResult(RESULT_CODE.SUCCESS)
    }

    private fun handleRequestControl(device: BluetoothDevice, value: ByteArray): ControlPointProcedureResult {
        if (value.size != 1) {
            return ControlPointProcedureResult(RESULT_CODE.INVALID_PARAMETER)
        }

        val owner = controlOwner
        return if (owner == null || owner.address == device.address) {
            controlOwner = device
            mainViewModel.controlHasBeenTakenInControlPoint.postValue(true)
            mainViewModel.ftmsControlOwnerAddress.postValue(device.address)
            ControlPointProcedureResult(RESULT_CODE.SUCCESS)
        } else {
            ControlPointProcedureResult(RESULT_CODE.CONTROL_NOT_PERMITTED)
        }
    }

    private fun updateTargets(update: (FtmsTargetState) -> FtmsTargetState) {
        mainViewModel.updateFtmsTargetState(update)
    }

    private fun readUInt16(value: ByteArray, offset: Int): Int {
        return (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readSInt16(value: ByteArray, offset: Int): Int {
        return readUInt16(value, offset).toShort().toInt()
    }

    private fun readUInt24(value: ByteArray, offset: Int): Int {
        return (value[offset].toInt() and 0xFF) or
            ((value[offset + 1].toInt() and 0xFF) shl 8) or
            ((value[offset + 2].toInt() and 0xFF) shl 16)
    }

    private fun isControlOwner(device: BluetoothDevice): Boolean {
        return controlOwner?.address == device.address
    }

    fun onDeviceDisconnected(device: BluetoothDevice) {
        clientConfigDescriptor.removeDevice(device)
        if (procedureDeviceAddress == device.address) {
            finishProcedure(notificationsCanBeSent = false)
        }
        if (isControlOwner(device)) {
            clearControlOwner()
        }
    }

    fun clearDevicesAndControl() {
        clientConfigDescriptor.clearDevices()
        finishProcedure(notificationsCanBeSent = false)
        clearControlOwner()
    }

    private fun clearControlOwner() {
        controlOwner = null
        mainViewModel.controlHasBeenTakenInControlPoint.postValue(false)
        mainViewModel.ftmsControlOwnerAddress.postValue(null)
    }

    private fun sendWriteResponse(
        device: BluetoothDevice,
        requestId: Int,
        responseNeeded: Boolean,
        status: Int
    ) {
        if (responseNeeded) {
            gattProfile.bluetoothGattServer!!.sendResponse(device, requestId, status, 0, null)
        }
    }

    private fun sendControlPointResponse(
        device: BluetoothDevice,
        requestCode: Int,
        resultCode: Int,
        responseParameters: ByteArray
    ) {
        val response = byteArrayOf(
            RESPONSE_CODE.toByte(),
            requestCode.toByte(),
            resultCode.toByte()
        ) + responseParameters
        mainViewModel.recordFtmsProtocolEvent(
            direction = "TX",
            channel = "CONTROL",
            title = "${requestName(requestCode)} response: ${resultName(resultCode)} · ${device.address}",
            payload = response
        )
        val server = gattProfile.bluetoothGattServer
        if (server == null) {
            finishProcedure(notificationsCanBeSent = false)
            return
        }
        GattNotificationDispatcher.enqueue(
            server = server,
            device = device,
            characteristic = gattCharacteristic,
            confirm = true,
            value = response,
            onComplete = { status ->
                finishProcedure(notificationsCanBeSent = status == BluetoothGatt.GATT_SUCCESS)
            }
        )
    }

    private fun finishProcedure(notificationsCanBeSent: Boolean) {
        if (!procedureInProgress) {
            return
        }
        procedureInProgress = false
        procedureDeviceAddress = null
        mainViewModel.ftmsControlProcedureState.postValue("Idle")
        if (notificationsCanBeSent) {
            statusCharacteristic.completeDeferredNotifications()
            trainingStatusCharacteristic.completeDeferredNotifications()
        } else {
            statusCharacteristic.discardDeferredNotifications()
            trainingStatusCharacteristic.discardDeferredNotifications()
        }
    }

    private fun requestName(requestCode: Int): String = when (requestCode) {
        REQUEST_CODE.REQUEST_CONTROL -> "Request control"
        REQUEST_CODE.RESET -> "Reset"
        REQUEST_CODE.SET_TARGET_SPEED -> "Set target speed"
        REQUEST_CODE.SET_TARGET_INCLINATION -> "Set target inclination"
        REQUEST_CODE.SET_TARGET_RESISTANCE_LEVEL -> "Set target resistance"
        REQUEST_CODE.SET_TARGET_POWER -> "Set target power"
        REQUEST_CODE.SET_TARGET_HEART_RATE -> "Set target heart rate"
        REQUEST_CODE.START_OR_RESUME -> "Start or resume"
        REQUEST_CODE.STOP_OR_PAUSE -> "Stop or pause"
        REQUEST_CODE.SET_TARGETED_EXPENDED_ENERGY -> "Set target energy"
        REQUEST_CODE.SET_TARGETED_NUMBER_OF_STEPS -> "Set target steps"
        REQUEST_CODE.SET_TARGETED_NUMBER_OF_STRIDES -> "Set target strides"
        REQUEST_CODE.SET_TARGETED_DISTANCE -> "Set target distance"
        REQUEST_CODE.SET_TARGETED_TRAINING_TIME -> "Set target training time"
        REQUEST_CODE.SET_TARGETED_TIME_IN_TWO_HEART_RATE_ZONES -> "Set two HR zones"
        REQUEST_CODE.SET_TARGETED_TIME_IN_THREE_HEART_RATE_ZONES -> "Set three HR zones"
        REQUEST_CODE.SET_TARGETED_TIME_IN_FIVE_HEART_RATE_ZONES -> "Set five HR zones"
        REQUEST_CODE.SET_INDOOR_BIKE_SIMULATION -> "Set bike simulation"
        REQUEST_CODE.SET_WHEEL_CIRCUMFERENCE -> "Set wheel circumference"
        REQUEST_CODE.SPIN_DOWN_CONTROL -> "Spin-down control"
        REQUEST_CODE.SET_TARGETED_CADENCE -> "Set target cadence"
        else -> "Unknown procedure 0x${requestCode.toString(16).uppercase()}"
    }

    private fun resultName(resultCode: Int): String = when (resultCode) {
        RESULT_CODE.SUCCESS -> "success"
        RESULT_CODE.OP_CODE_NOT_SUPPORTED -> "not supported"
        RESULT_CODE.INVALID_PARAMETER -> "invalid parameter"
        RESULT_CODE.OPERATION_FAILED -> "operation failed"
        RESULT_CODE.CONTROL_NOT_PERMITTED -> "control not permitted"
        else -> "0x${resultCode.toString(16).uppercase()}"
    }
}
