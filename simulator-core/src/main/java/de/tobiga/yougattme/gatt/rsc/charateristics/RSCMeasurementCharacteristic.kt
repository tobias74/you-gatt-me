package de.tobiga.yougattme.gatt.rsc.charateristics

import de.tobiga.yougattme.gatt.trace.sendTracedResponse

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.rsc.RscMeasurementValues
import de.tobiga.yougattme.gatt.rsc.RscProtocol
import java.util.UUID

class RSCMeasurementCharacteristic(
    gattProfile: AbstractGattProfile,
    private val viewModel: ApplicationViewModel
) : AbstractDataMeasurementCharacteristic(gattProfile, includeUserDescriptor = false) {
    private val uuid = UUID.fromString("00002A53-0000-1000-8000-00805f9b34fb")

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

    override fun getData(): ByteArray {
        val data = viewModel.runningSpeedAndCadenceData
        return RscProtocol.encodeMeasurement(
            RscMeasurementValues(
                instantaneousSpeedMetersPerSecond = data.instantaneousSpeed,
                instantaneousCadencePerMinute = data.instantaneousCadence,
                instantaneousStrideLengthMeters = data.instantaneousStrideLength,
                totalDistanceMeters = data.totalDistance
            ),
            viewModel.rscConfiguration
        )
    }

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
