package de.tobiga.yougattme.gatt.cyclingpower.characteristics

import de.tobiga.yougattme.gatt.trace.sendTracedResponse

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerMeasurementValues
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerProtocol
import java.util.UUID

class CyclingPowerVectorCharacteristic(
    gattProfile: AbstractGattProfile,
    private val viewModel: ApplicationViewModel
) : AbstractDataMeasurementCharacteristic(gattProfile, includeUserDescriptor = false) {
    private val uuid = UUID.fromString(CyclingPowerProtocol.VECTOR_UUID)
    private val lastCrankSequenceByDevice = mutableMapOf<String, Long>()

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            uuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        activeDescriptors.forEach { descriptor ->
            gattCharacteristic.addDescriptor(descriptor.gattDescriptor)
        }
    }

    override fun getCharacteristicUUID(): UUID = uuid

    override fun getData(): ByteArray = getNotificationPackets(20).first()

    override fun getNotificationPackets(maxPayloadBytes: Int): List<ByteArray> =
        encodePackets(maxPayloadBytes, viewModel.cyclingPowerConfiguration.vectorIncludeFirstCrankAngle)

    override fun getNotificationPacketsForDevice(
        device: BluetoothDevice,
        maxPayloadBytes: Int
    ): List<ByteArray> {
        val currentSequence = viewModel.cyclingPowerCrankRevolutionSequence
        val previousSequence = lastCrankSequenceByDevice.put(device.address, currentSequence)
        val startsNewRevolution = previousSequence == null || previousSequence != currentSequence
        return encodePackets(
            maxPayloadBytes,
            viewModel.cyclingPowerConfiguration.vectorIncludeFirstCrankAngle && startsNewRevolution
        )
    }

    fun clearDevice(device: BluetoothDevice) {
        clientConfigDescriptor.removeDevice(device)
        lastCrankSequenceByDevice.remove(device.address)
    }

    fun clearDevices() {
        clientConfigDescriptor.clearDevices()
        lastCrankSequenceByDevice.clear()
    }

    private fun encodePackets(
        maxPayloadBytes: Int,
        includeFirstCrankAngle: Boolean
    ): List<ByteArray> =
        CyclingPowerProtocol.encodeVectorPackets(
            values = CyclingPowerMeasurementValues(
                instantaneousPowerWatts = viewModel.cyclingPowerWatts,
                cumulativeWheelRevolutions = viewModel.cumulativeWheelRevolutions and 0xFFFF_FFFFL,
                lastWheelEventTimeMillis = viewModel.lastWheelEventTime,
                cumulativeCrankRevolutions = viewModel.cumulativeCrankRevolutions,
                lastCrankEventTimeMillis = viewModel.lastCrankEventTime
            ),
            configuration = viewModel.cyclingPowerConfiguration,
            maxPayloadBytes = maxPayloadBytes,
            includeFirstCrankAngle = includeFirstCrankAngle
        )

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        gattProfile.bluetoothGattServer?.sendTracedResponse(
            device,
            requestId,
            BluetoothGatt.GATT_READ_NOT_PERMITTED,
            offset,
            null
        )
    }
}
