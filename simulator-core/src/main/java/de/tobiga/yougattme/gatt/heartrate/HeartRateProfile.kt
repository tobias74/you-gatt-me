package de.tobiga.yougattme.gatt.heartrate

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.BaseGattCharacteristic
import de.tobiga.yougattme.gatt.SingleServiceGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.common.ReadOnlyValueCharacteristic
import de.tobiga.yougattme.gatt.common.littleEndianUInt16
import java.util.UUID
import kotlin.math.roundToInt
import de.tobiga.yougattme.bridge.RemoteControlDispatcher

class HeartRateProfile(private val viewModel: ApplicationViewModel) : SingleServiceGattProfile() {
    override val bridgeOutputKey: String = "heart_rate"
    private val measurement = HeartRateMeasurementCharacteristic(this, viewModel)
    private val bodyLocation = if (viewModel.heartRateConfiguration.bodySensorLocationCharacteristicEnabled) {
        ReadOnlyValueCharacteristic(
            this, UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb"),
            { byteArrayOf(viewModel.heartRateConfiguration.bodySensorLocation.coerceIn(0, 6).toByte()) }
        )
    } else null
    private val controlPoint = if (viewModel.heartRateConfiguration.controlPointCharacteristicEnabled &&
        viewModel.heartRateConfiguration.includeEnergyExpended) {
        HeartRateControlPointCharacteristic(this) {
            viewModel.heartRateConfiguration.energyExpendedKj = 0
            viewModel.recordSensorProtocolEvent("heart_rate", "RX", "Reset energy expended", byteArrayOf(0x01))
        }
    } else null

    init {
        activeCharacteristics += measurement
        bodyLocation?.let(activeCharacteristics::add)
        controlPoint?.let(activeCharacteristics::add)
    }

    override fun getServiceUUIDs(): List<UUID> =
        listOf(UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb"))

    override fun clearAllDevices() {
        measurement.clientConfigDescriptor.clearDevices()
        clearDeviceMtus()
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        measurement.clientConfigDescriptor.removeDevice(device)
        forgetDeviceMtu(device)
    }

    override fun triggerPeriodicTimer() {
        val data = measurement.getData()
        viewModel.recordSensorMeasurement("heart_rate", measurement.getCharacteristicUUID().toString(), data, listOf(data), 20)
        measurement.publishMeasurementData()
    }
}

private class HeartRateMeasurementCharacteristic(
    profile: AbstractGattProfile,
    private val viewModel: ApplicationViewModel
) : AbstractDataMeasurementCharacteristic(profile) {
    private val uuid = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")

    init {
        gattCharacteristic = BluetoothGattCharacteristic(uuid, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        activeDescriptors.forEach { gattCharacteristic.addDescriptor(it.gattDescriptor) }
    }

    override fun getCharacteristicUUID(): UUID = uuid

    override fun getData(): ByteArray {
        val config = viewModel.heartRateConfiguration
        val use16 = config.useUInt16Format || config.heartRateBpm > 255
        var flags = if (use16) 0x01 else 0
        if (config.sensorContactSupported) {
            flags = flags or 0x04
            if (config.sensorContactDetected) flags = flags or 0x02
        }
        if (config.includeEnergyExpended) flags = flags or 0x08
        if (config.includeRrIntervals && config.rrIntervalsSeconds.isNotEmpty()) flags = flags or 0x10
        return buildList<Byte> {
            add(flags.toByte())
            if (use16) addAll(littleEndianUInt16(config.heartRateBpm.coerceIn(0, 0xFFFF)).toList())
            else add(config.heartRateBpm.coerceIn(0, 0xFF).toByte())
            if (config.includeEnergyExpended) {
                addAll(littleEndianUInt16(config.energyExpendedKj.coerceIn(0, 0xFFFF)).toList())
            }
            if (config.includeRrIntervals) config.rrIntervalsSeconds.forEach { seconds ->
                addAll(littleEndianUInt16((seconds * 1024.0).roundToInt().coerceIn(0, 0xFFFF)).toList())
            }
        }.toByteArray()
    }
}

private class HeartRateControlPointCharacteristic(
    profile: AbstractGattProfile,
    private val resetEnergy: () -> Unit
) : BaseGattCharacteristic(profile) {
    private val uuid = UUID.fromString("00002A39-0000-1000-8000-00805f9b34fb")

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            uuid, BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
    }

    override fun getCharacteristicUUID(): UUID = uuid
    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        gattProfile.bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
    }

    override fun onWriteRequest(device: BluetoothDevice, requestId: Int, preparedWrite: Boolean,
        responseNeeded: Boolean, offset: Int, value: ByteArray) {
        if (preparedWrite || offset != 0 || !value.contentEquals(byteArrayOf(0x01))) {
            if (responseNeeded) gattProfile.bluetoothGattServer?.sendResponse(
                device, requestId, BluetoothGatt.GATT_FAILURE, offset, null
            )
            return
        }
        if (RemoteControlDispatcher.forwardIfConfigured("heart_rate", value) { remote ->
                if (remote.success) resetEnergy()
                if (responseNeeded) gattProfile.bluetoothGattServer?.sendResponse(
                    device, requestId,
                    if (remote.success) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                    offset, null
                )
            }) return
        resetEnergy()
        if (responseNeeded) gattProfile.bluetoothGattServer?.sendResponse(
            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null
        )
    }
}
