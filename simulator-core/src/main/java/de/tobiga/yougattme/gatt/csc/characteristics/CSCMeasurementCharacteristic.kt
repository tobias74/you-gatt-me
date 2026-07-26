package de.tobiga.yougattme.gatt.csc.characteristics

import de.tobiga.yougattme.gatt.trace.sendTracedResponse

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.csc.CscMeasurementValues
import de.tobiga.yougattme.gatt.csc.CscProtocol
import java.util.UUID

class CSCMeasurementCharacteristic(
    gattProfile: AbstractGattProfile,
    private val viewModel: ApplicationViewModel
) : AbstractDataMeasurementCharacteristic(gattProfile, includeUserDescriptor = false) {
    private val uuid = UUID.fromString("00002A5B-0000-1000-8000-00805f9b34fb")

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

    override fun getData(): ByteArray = CscProtocol.encodeMeasurement(
        CscMeasurementValues(
            cumulativeWheelRevolutions = viewModel.cscCumulativeWheelRevolutions and 0xFFFF_FFFFL,
            lastWheelEventTimeMillis = viewModel.cscLastWheelEventTime,
            cumulativeCrankRevolutions = viewModel.cscCumulativeCrankRevolutions,
            lastCrankEventTimeMillis = viewModel.cscLastCrankEventTime
        ),
        viewModel.cscConfiguration
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
