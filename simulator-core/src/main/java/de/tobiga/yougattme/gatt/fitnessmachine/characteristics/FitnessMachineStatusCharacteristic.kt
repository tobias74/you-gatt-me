package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.fitnessmachine.IndoorCyclingSimulationParameters
import java.util.*

object FTMS_STATUS_CODES {
    const val RESET = 0x01
    const val FITNESS_MACHINE_STOPPED_OR_PAUSED_BY_USER = 0x02
    const val FITNESS_MACHINE_STOPPED_BY_SAFETY_KEY = 0x03
    const val FITNESS_MACHINE_STARTED_OR_RESUMED_BY_USER = 0x04
    const val TARGET_SPEED_CHANGED = 0x05
    const val TARGET_INCLINE_CHANGED = 0x06
    const val TARGET_RESISTANCE_LEVEL_CHANGED = 0x07
    const val TARGET_POWER_CHANGED = 0x08
    const val TARGET_HEART_RATE_CHANGED = 0x09
    const val TARGETED_EXPENDED_ENERGY_CHANGED = 0x0A
    const val TARGETED_NUMBER_OF_STEPS_CHANGED = 0x0B
    const val TARGETED_NUMBER_OF_STRIDES_CHANGED = 0x0C
    const val TARGETED_DISTANCE_CHANGED = 0x0D
    const val TARGETED_TRAINING_TIME_CHANGED = 0x0E
    const val TARGETED_TIME_IN_TWO_HR_ZONES_CHANGED = 0x0F
    const val TARGETED_TIME_IN_THREE_HR_ZONES_CHANGED = 0x10
    const val TARGETED_TIME_IN_FIVE_HR_ZONES_CHANGED = 0x11
    const val INDOOR_BIKE_SIMULATION_PARAMETERS_CHANGED = 0x12
    const val WHEEL_CIRCUMFERENCE_CHANGED = 0x13
    const val SPIN_DOWN_STATUS = 0x14
    const val TARGETED_CADENCE_CHANGED = 0x15
    const val CONTROL_PERMISSION_LOST = 0xFF
}

/**
 * Implements the Fitness Machine Status characteristic.
 * This characteristic is event-driven and notifies clients about state changes.
 * It inherits from AbstractDataMeasurementCharacteristic to reuse the notification logic.
 */
class FitnessMachineStatusCharacteristic(
    gattProfile: AbstractGattProfile,
    private val mainViewModel: ApplicationViewModel
) : AbstractDataMeasurementCharacteristic(gattProfile) {
    private data class DeferredStatus(
        val packet: ByteArray,
        val coalesceKey: String?,
        val excludedDeviceAddress: String?
    )

    private val deferredNotifications = mutableListOf<DeferredStatus>()
    private var notificationsDeferred = false
    private var deferredExcludedDeviceAddress: String? = null
    val GATT_FITNESS_MACHINE_STATUS_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002ADA-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID {
        return GATT_FITNESS_MACHINE_STATUS_CHARACTERISTIC_UUID
    }

    init {
        // Per FTMS spec, this characteristic's property is NOTIFY.
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_FITNESS_MACHINE_STATUS_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )

        // The base class (AbstractDataMeasurementCharacteristic) already adds
        // the ClientConfigDescriptor, so we just need to add the characteristic's
        // own descriptors to the gattCharacteristic object.
        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    /**
     * This method is for periodic updates, which this characteristic doesn't do.
     * We return an empty array as a safeguard, but it should not be called.
     */
    override fun getData(): ByteArray {
        return ByteArray(0)
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
        if (responseNeeded) {
            gattProfile.bluetoothGattServer!!.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED,
                0,
                null
            )
        }
    }

    // --- Public methods to be called by the Profile to send event-driven status updates ---

    fun sendResetStatus() {
        val packet = byteArrayOf(FTMS_STATUS_CODES.RESET.toByte())
        sendData(packet)
    }

    fun sendStartedOrResumedStatus() {
        val packet = byteArrayOf(FTMS_STATUS_CODES.FITNESS_MACHINE_STARTED_OR_RESUMED_BY_USER.toByte())
        sendData(packet)
    }

    fun sendStoppedStatus(isPause: Boolean) {
        val controlInfo = if (isPause) 0x02 else 0x01
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.FITNESS_MACHINE_STOPPED_OR_PAUSED_BY_USER.toByte(),
            controlInfo.toByte()
        )
        sendData(packet)
    }

    fun sendTargetSpeedChanged(speedKmh: Float) {
        val speedValue = (speedKmh * 100f).toInt()
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.TARGET_SPEED_CHANGED.toByte(),
            (speedValue and 0xFF).toByte(),
            ((speedValue shr 8) and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendTargetPowerChanged(newPower: Int) {
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.TARGET_POWER_CHANGED.toByte(),
            (newPower and 0xFF).toByte(),
            ((newPower shr 8) and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendTargetResistanceLevelChanged(resistanceLevel: Float) {
        val resistanceValue = (resistanceLevel * 10f).toInt()
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.TARGET_RESISTANCE_LEVEL_CHANGED.toByte(),
            (resistanceValue and 0xFF).toByte()
        )
        sendData(packet)
    }

    override fun sendData(dataBlock: ByteArray, coalesceKey: String?) {
        if (notificationsDeferred) {
            deferredNotifications += DeferredStatus(
                packet = dataBlock.copyOf(),
                coalesceKey = coalesceKey,
                excludedDeviceAddress = deferredExcludedDeviceAddress
            )
        } else {
            recordStatus(dataBlock)
            super.sendData(dataBlock, coalesceKey)
        }
    }

    fun beginDeferredNotifications(excludedDeviceAddress: String? = null) {
        deferredNotifications.clear()
        deferredExcludedDeviceAddress = excludedDeviceAddress
        notificationsDeferred = true
    }

    fun completeDeferredNotifications() {
        notificationsDeferred = false
        val pending = deferredNotifications.toList()
        deferredNotifications.clear()
        deferredExcludedDeviceAddress = null
        pending.forEach { status ->
            recordStatus(status.packet)
            sendDataToRegisteredDevices(
                dataBlock = status.packet,
                coalesceKey = status.coalesceKey,
                excludedDeviceAddress = status.excludedDeviceAddress
            )
        }
    }

    fun discardDeferredNotifications() {
        notificationsDeferred = false
        deferredExcludedDeviceAddress = null
        deferredNotifications.clear()
    }

    private fun recordStatus(packet: ByteArray) {
        val title = when (packet.firstOrNull()?.toInt()?.and(0xFF)) {
            FTMS_STATUS_CODES.RESET -> "Reset"
            FTMS_STATUS_CODES.FITNESS_MACHINE_STOPPED_OR_PAUSED_BY_USER -> "Stopped or paused"
            FTMS_STATUS_CODES.FITNESS_MACHINE_STARTED_OR_RESUMED_BY_USER -> "Started or resumed"
            FTMS_STATUS_CODES.TARGET_SPEED_CHANGED -> "Target speed changed"
            FTMS_STATUS_CODES.TARGET_INCLINE_CHANGED -> "Target inclination changed"
            FTMS_STATUS_CODES.TARGET_RESISTANCE_LEVEL_CHANGED -> "Target resistance changed"
            FTMS_STATUS_CODES.TARGET_POWER_CHANGED -> "Target power changed"
            FTMS_STATUS_CODES.TARGET_HEART_RATE_CHANGED -> "Target heart rate changed"
            FTMS_STATUS_CODES.TARGETED_EXPENDED_ENERGY_CHANGED -> "Target energy changed"
            FTMS_STATUS_CODES.TARGETED_NUMBER_OF_STEPS_CHANGED -> "Target steps changed"
            FTMS_STATUS_CODES.TARGETED_NUMBER_OF_STRIDES_CHANGED -> "Target strides changed"
            FTMS_STATUS_CODES.TARGETED_DISTANCE_CHANGED -> "Target distance changed"
            FTMS_STATUS_CODES.TARGETED_TRAINING_TIME_CHANGED -> "Target training time changed"
            FTMS_STATUS_CODES.TARGETED_TIME_IN_TWO_HR_ZONES_CHANGED -> "Two-zone HR times changed"
            FTMS_STATUS_CODES.TARGETED_TIME_IN_THREE_HR_ZONES_CHANGED -> "Three-zone HR times changed"
            FTMS_STATUS_CODES.TARGETED_TIME_IN_FIVE_HR_ZONES_CHANGED -> "Five-zone HR times changed"
            FTMS_STATUS_CODES.INDOOR_BIKE_SIMULATION_PARAMETERS_CHANGED -> "Bike simulation changed"
            FTMS_STATUS_CODES.WHEEL_CIRCUMFERENCE_CHANGED -> "Wheel circumference changed"
            FTMS_STATUS_CODES.SPIN_DOWN_STATUS -> "Spin-down status"
            FTMS_STATUS_CODES.TARGETED_CADENCE_CHANGED -> "Target cadence changed"
            FTMS_STATUS_CODES.CONTROL_PERMISSION_LOST -> "Control permission lost"
            else -> "Fitness Machine Status"
        }
        mainViewModel.recordFtmsProtocolEvent("TX", "STATUS", title, packet)
    }

    fun sendTargetHeartRateChanged(heartRate: Int) {
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.TARGET_HEART_RATE_CHANGED.toByte(),
            (heartRate and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendTargetInclinationChanged(newInclination: Double) {
        val inclineValue = (newInclination * 10).toInt()
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.TARGET_INCLINE_CHANGED.toByte(),
            (inclineValue and 0xFF).toByte(),
            ((inclineValue shr 8) and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendTargetedExpendedEnergyChanged(kcal: Int) {
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.TARGETED_EXPENDED_ENERGY_CHANGED.toByte(),
            (kcal and 0xFF).toByte(),
            ((kcal shr 8) and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendTargetedNumberOfStepsChanged(steps: Int) {
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.TARGETED_NUMBER_OF_STEPS_CHANGED.toByte(),
            (steps and 0xFF).toByte(),
            ((steps shr 8) and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendTargetedNumberOfStridesChanged(strides: Int) {
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.TARGETED_NUMBER_OF_STRIDES_CHANGED.toByte(),
            (strides and 0xFF).toByte(),
            ((strides shr 8) and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendTargetedDistanceChanged(distanceMeters: Int) {
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.TARGETED_DISTANCE_CHANGED.toByte(),
            (distanceMeters and 0xFF).toByte(),
            ((distanceMeters shr 8) and 0xFF).toByte(),
            ((distanceMeters shr 16) and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendTargetedTrainingTimeChanged(seconds: Int) {
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.TARGETED_TRAINING_TIME_CHANGED.toByte(),
            (seconds and 0xFF).toByte(),
            ((seconds shr 8) and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendTargetedTimeInTwoHeartRateZonesChanged(seconds: List<Int>) {
        sendTargetedHeartRateZoneTimes(FTMS_STATUS_CODES.TARGETED_TIME_IN_TWO_HR_ZONES_CHANGED, seconds)
    }

    fun sendTargetedTimeInThreeHeartRateZonesChanged(seconds: List<Int>) {
        sendTargetedHeartRateZoneTimes(FTMS_STATUS_CODES.TARGETED_TIME_IN_THREE_HR_ZONES_CHANGED, seconds)
    }

    fun sendTargetedTimeInFiveHeartRateZonesChanged(seconds: List<Int>) {
        sendTargetedHeartRateZoneTimes(FTMS_STATUS_CODES.TARGETED_TIME_IN_FIVE_HR_ZONES_CHANGED, seconds)
    }

    private fun sendTargetedHeartRateZoneTimes(statusCode: Int, seconds: List<Int>) {
        val writer = FtmsPacketWriter()
        writer.u8(statusCode)
        seconds.forEach { writer.u16(it) }
        sendData(writer.bytes())
    }

    fun sendIndoorBikeSimulationParametersChanged(parameters: IndoorCyclingSimulationParameters) {
        val writer = FtmsPacketWriter()
        writer.u8(FTMS_STATUS_CODES.INDOOR_BIKE_SIMULATION_PARAMETERS_CHANGED)
        writer.s16((parameters.windSpeedMps * 1000f).toInt())
        writer.s16((parameters.gradePercent * 100.0).toInt())
        writer.u8((parameters.crr * 10000f).toInt())
        writer.u8((parameters.cw * 100f).toInt())
        sendData(writer.bytes())
    }

    fun sendWheelCircumferenceChanged(rawCircumferenceTenthsMm: Int) {
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.WHEEL_CIRCUMFERENCE_CHANGED.toByte(),
            (rawCircumferenceTenthsMm and 0xFF).toByte(),
            ((rawCircumferenceTenthsMm shr 8) and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendSpinDownStatus(status: Int) {
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.SPIN_DOWN_STATUS.toByte(),
            (status and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendTargetedCadenceChanged(cadenceRpm: Float) {
        val cadenceValue = (cadenceRpm * 2f).toInt()
        val packet = byteArrayOf(
            FTMS_STATUS_CODES.TARGETED_CADENCE_CHANGED.toByte(),
            (cadenceValue and 0xFF).toByte(),
            ((cadenceValue shr 8) and 0xFF).toByte()
        )
        sendData(packet)
    }

    fun sendControlPermissionLost() {
        val packet = byteArrayOf(FTMS_STATUS_CODES.CONTROL_PERMISSION_LOST.toByte())
        sendData(packet)
    }
}
