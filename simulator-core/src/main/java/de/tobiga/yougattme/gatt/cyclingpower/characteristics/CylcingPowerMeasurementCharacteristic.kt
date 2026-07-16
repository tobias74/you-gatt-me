package de.tobiga.yougattme.gatt.cyclingpower.characteristics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.common.ServerConfigDescriptor
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerMeasurementValues
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerProtocol
import java.util.UUID

class CyclingPowerMeasurementCharacteristic(
    gattProfile: AbstractGattProfile,
    private val viewModel: ApplicationViewModel,
    onBroadcastChanged: (BluetoothDevice, Boolean) -> Unit = { _, _ -> }
) : AbstractDataMeasurementCharacteristic(gattProfile, includeUserDescriptor = false) {
    private val uuid = UUID.fromString(CyclingPowerProtocol.MEASUREMENT_UUID)
    val serverConfigDescriptor: ServerConfigDescriptor? =
        if (viewModel.cyclingPowerConfiguration.measurementBroadcastPropertyEnabled) {
            ServerConfigDescriptor(gattProfile, onBroadcastChanged).also(activeDescriptors::add)
        } else {
            null
        }

    init {
        val broadcastProperty = if (serverConfigDescriptor != null) {
            BluetoothGattCharacteristic.PROPERTY_BROADCAST
        } else {
            0
        }
        gattCharacteristic = BluetoothGattCharacteristic(
            uuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or broadcastProperty,
            0
        )
        activeDescriptors.forEach { descriptor ->
            gattCharacteristic.addDescriptor(descriptor.gattDescriptor)
        }
    }

    override fun getCharacteristicUUID(): UUID = uuid

    override fun getData(): ByteArray = CyclingPowerProtocol.encodeMeasurement(
        measurementValues(),
        viewModel.cyclingPowerConfiguration
    )

    override fun getNotificationPacketsForDevice(
        device: BluetoothDevice,
        maxPayloadBytes: Int
    ): List<ByteArray> = CyclingPowerProtocol.encodeMeasurementPackets(
        values = measurementValues(),
        configuration = viewModel.cyclingPowerConfiguration,
        contentMask = viewModel.cyclingPowerConfiguration.measurementMaskFor(device.address),
        maxPayloadBytes = maxPayloadBytes
    )

    fun previewPackets(maxPayloadBytes: Int): List<ByteArray> =
        CyclingPowerProtocol.encodeMeasurementPackets(
            values = measurementValues(),
            configuration = viewModel.cyclingPowerConfiguration,
            contentMask = 0,
            maxPayloadBytes = maxPayloadBytes
        )

    fun getBroadcastData(): ByteArray = CyclingPowerProtocol.encodeMeasurement(
        values = measurementValues(),
        configuration = viewModel.cyclingPowerConfiguration,
        contentMask = 0x01F7
    )

    fun clearDevice(device: BluetoothDevice) {
        clientConfigDescriptor.removeDevice(device)
        serverConfigDescriptor?.removeDevice(device)
    }

    fun clearDevices() {
        clientConfigDescriptor.clearDevices()
        serverConfigDescriptor?.clearDevices()
    }

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        gattProfile.bluetoothGattServer?.sendResponse(
            device,
            requestId,
            BluetoothGatt.GATT_READ_NOT_PERMITTED,
            offset,
            null
        )
    }

    private fun measurementValues() = CyclingPowerMeasurementValues(
        instantaneousPowerWatts = viewModel.cyclingPowerWatts,
        cumulativeWheelRevolutions = viewModel.cumulativeWheelRevolutions and 0xFFFF_FFFFL,
        lastWheelEventTimeMillis = viewModel.lastWheelEventTime,
        cumulativeCrankRevolutions = viewModel.cumulativeCrankRevolutions,
        lastCrankEventTimeMillis = viewModel.lastCrankEventTime
    )
}
