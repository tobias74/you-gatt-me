package de.tobiga.yougattme.gatt.common

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.BaseGattCharacteristic
import java.util.UUID

object SensorLocationValues {
    const val OTHER = 0
    const val TOP_OF_SHOE = 1
    const val IN_SHOE = 2
    const val HIP = 3
    const val FRONT_WHEEL = 4
    const val LEFT_CRANK = 5
    const val RIGHT_CRANK = 6
    const val LEFT_PEDAL = 7
    const val RIGHT_PEDAL = 8
    const val FRONT_HUB = 9
    const val REAR_DROPOUT = 10
    const val CHAINSTAY = 11
    const val REAR_WHEEL = 12
    const val REAR_HUB = 13
    const val CHEST = 14
    const val SPIDER = 15
    const val CHAIN_RING = 16

    val names = listOf(
        "Other", "Top of shoe", "In shoe", "Hip", "Front wheel", "Left crank",
        "Right crank", "Left pedal", "Right pedal", "Front hub", "Rear dropout",
        "Chainstay", "Rear wheel", "Rear hub", "Chest", "Spider", "Chain ring"
    )

    fun isAssigned(value: Int): Boolean = value in names.indices
}

class ReadOnlyValueCharacteristic(
    gattProfile: AbstractGattProfile,
    private val uuid: UUID,
    private val valueProvider: () -> ByteArray,
    indications: Boolean = false
) : BaseGattCharacteristic(gattProfile) {
    val clientConfigDescriptor: ClientConfigDescriptor? = if (indications) {
        ClientConfigDescriptor(
            gattProfile,
            supportsNotifications = false,
            supportsIndications = true
        ).also(activeDescriptors::add)
    } else {
        null
    }

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            uuid,
            BluetoothGattCharacteristic.PROPERTY_READ or
                if (indications) BluetoothGattCharacteristic.PROPERTY_INDICATE else 0,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        activeDescriptors.forEach { descriptor ->
            gattCharacteristic.addDescriptor(descriptor.gattDescriptor)
        }
    }

    override fun getCharacteristicUUID(): UUID = uuid

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        val value = valueProvider()
        if (offset !in 0..value.size) {
            gattProfile.bluetoothGattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_INVALID_OFFSET,
                offset,
                null
            )
            return
        }
        gattProfile.bluetoothGattServer?.sendResponse(
            device,
            requestId,
            BluetoothGatt.GATT_SUCCESS,
            offset,
            value.copyOfRange(offset, value.size)
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
            gattProfile.bluetoothGattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED,
                offset,
                null
            )
        }
    }

    fun clearDevice(device: BluetoothDevice) {
        clientConfigDescriptor?.removeDevice(device)
    }

    fun clearDevices() {
        clientConfigDescriptor?.clearDevices()
    }
}

fun littleEndianUInt16(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte()
)

fun littleEndianUInt32(value: Long): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte(),
    ((value ushr 16) and 0xFF).toByte(),
    ((value ushr 24) and 0xFF).toByte()
)

fun readUInt16(value: ByteArray, offset: Int): Int =
    (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)

fun readUInt32(value: ByteArray, offset: Int): Long =
    (value[offset].toLong() and 0xFF) or
        ((value[offset + 1].toLong() and 0xFF) shl 8) or
        ((value[offset + 2].toLong() and 0xFF) shl 16) or
        ((value[offset + 3].toLong() and 0xFF) shl 24)
