package de.tobiga.yougattme.gatt.common

import de.tobiga.yougattme.gatt.trace.sendTracedResponse

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.BaseGattCharacteristic
import de.tobiga.yougattme.gatt.common.ClientConfigDescriptor
import java.util.UUID

/**
 * A generic, write-only GATT characteristic.
 *
 * @param gattProfile     the profile this characteristic belongs to
 * @param uuid            the UUID of this characteristic
 * @param withNotifications  true if you want to support notify/indicate back to client
 * @param onWriteCallback the callback to invoke when the client writes data here
 */
class WriteOnlyCharacteristic(
    gattProfile: AbstractGattProfile,
    uuid: UUID,
    withNotifications: Boolean = false,
    private val onWriteCallback: (device: BluetoothDevice, value: ByteArray) -> Unit
) : BaseGattCharacteristic(gattProfile) {

    // If you need notify support, add a CCCD
    private val cccd: ClientConfigDescriptor? = if (withNotifications) {
        ClientConfigDescriptor(gattProfile).also { activeDescriptors.add(it) }
    } else null

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            uuid,
            // write-only + optional notify
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    if (withNotifications) BluetoothGattCharacteristic.PROPERTY_NOTIFY else 0,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // add the descriptor if notify is enabled
        cccd?.let { gattCharacteristic.addDescriptor(it.gattDescriptor) }
    }

    override fun getCharacteristicUUID(): UUID =
        gattCharacteristic.uuid

    override fun onReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int
    ) {
        // No reads allowed
        gattProfile.bluetoothGattServer?.sendTracedResponse(
            device,
            requestId,
            BluetoothGattCharacteristic.PERMISSION_READ, // or GATT_READ_NOT_PERMITTED
            offset,
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
        // First handle the write callback
        onWriteCallback(device, value)

        // Always ack if requested
        if (responseNeeded) {
            gattProfile.bluetoothGattServer?.sendTracedResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                null
            )
        }
    }

    /**
     * If notifications are enabled, you can call this to push data back.
     */
    fun notify(device: BluetoothDevice, data: ByteArray) {
        cccd?.takeIf { it.registeredDevices.contains(device) }?.also {
            gattProfile.bluetoothGattServer?.let { server ->
                GattNotificationDispatcher.enqueue(
                    server = server,
                    device = device,
                    characteristic = gattCharacteristic,
                    confirm = false,
                    value = data
                )
            }
        }
    }
}
