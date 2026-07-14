package de.tobiga.yougattme.gatt.common

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.TobigaGattCharacteristic
import de.tobiga.yougattme.tools.toHex
import java.nio.charset.Charset
import java.util.*

private const val TAG = "ReadOnlyCharacteristic"

class ReadOnlyCharacteristic(
    // primary constructor takes a ByteArray payload
    gattProfile: AbstractGattProfile,
    private val uuid: UUID,
    private val dataBytes: ByteArray
) : TobigaGattCharacteristic(gattProfile) {

    // secondary, convenience constructor for String-based payloads
    constructor(
        gattProfile: AbstractGattProfile,
        uuid: UUID,
        textValue: String
    ) : this(
        gattProfile,
        uuid,
        textValue.toByteArray(Charset.forName("UTF-8"))
    )

    override fun getCharacteristicUUID(): UUID = uuid

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            uuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            // Initialize the characteristic’s “stored” value
            value = dataBytes
        }
    }

    override fun onReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int
    ) {
        // Fail fast if the client asked for an impossible offset
        require(offset >= 0) { "Negative offset ($offset) on read for $uuid" }
        require(offset <= dataBytes.size) {
            "Offset ($offset) exceeds length (${dataBytes.size}) for $uuid"
        }

        // Slice out exactly what they asked for
        val chunk = dataBytes.copyOfRange(offset, dataBytes.size)

        Log.d(TAG, "Responding to read of $uuid: offset=$offset, bytes=${chunk.size}")

        gattProfile.bluetoothGattServer!!.sendResponse(
            device,
            requestId,
            BluetoothGatt.GATT_SUCCESS,
            offset,
            chunk
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
        Log.w(TAG, "Unexpected write to read‑only $uuid: ${value.toHex()}")
        if(responseNeeded) {
            gattProfile.bluetoothGattServer!!.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED,
                offset,
                null
            )
        }
    }
}
