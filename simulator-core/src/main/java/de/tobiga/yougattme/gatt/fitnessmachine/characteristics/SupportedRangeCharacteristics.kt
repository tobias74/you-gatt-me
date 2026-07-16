package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.TobigaGattCharacteristic
import de.tobiga.yougattme.gatt.common.UserDescriptor
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsSupportedRanges
import java.util.UUID

abstract class AbstractSupportedRangeCharacteristic(
    gattProfile: AbstractGattProfile,
    private val uuid: UUID
) : TobigaGattCharacteristic(gattProfile) {

    override fun getCharacteristicUUID(): UUID = uuid

    init {
        activeDescriptors.add(UserDescriptor(gattProfile))
        gattCharacteristic = BluetoothGattCharacteristic(
            uuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    abstract fun getPayload(): ByteArray

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        val data = getPayload()
        if (offset > data.size) {
            gattProfile.bluetoothGattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            return
        }
        gattProfile.bluetoothGattServer!!.sendResponse(
            device,
            requestId,
            BluetoothGatt.GATT_SUCCESS,
            offset,
            data.copyOfRange(offset, data.size)
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
                offset,
                null
            )
        }
    }
}

class SupportedSpeedRangeCharacteristic(
    gattProfile: AbstractGattProfile,
    private val ranges: FtmsSupportedRanges
) : AbstractSupportedRangeCharacteristic(
    gattProfile,
    UUID.fromString("00002AD4-0000-1000-8000-00805f9b34fb")
) {
    override fun getPayload(): ByteArray {
        val writer = FtmsPacketWriter()
        writer.u16(ranges.speedRaw(ranges.minSpeedKmh))
        writer.u16(ranges.speedRaw(ranges.maxSpeedKmh))
        writer.u16(ranges.speedRaw(ranges.speedIncrementKmh))
        return writer.bytes()
    }
}

class SupportedInclinationRangeCharacteristic(
    gattProfile: AbstractGattProfile,
    private val ranges: FtmsSupportedRanges
) : AbstractSupportedRangeCharacteristic(
    gattProfile,
    UUID.fromString("00002AD5-0000-1000-8000-00805f9b34fb")
) {
    override fun getPayload(): ByteArray {
        val writer = FtmsPacketWriter()
        writer.s16(ranges.inclinationRaw(ranges.minInclinationPercent))
        writer.s16(ranges.inclinationRaw(ranges.maxInclinationPercent))
        writer.u16(ranges.inclinationRaw(ranges.inclinationIncrementPercent))
        return writer.bytes()
    }
}

class SupportedResistanceLevelRangeCharacteristic(
    gattProfile: AbstractGattProfile,
    private val ranges: FtmsSupportedRanges
) : AbstractSupportedRangeCharacteristic(
    gattProfile,
    UUID.fromString("00002AD6-0000-1000-8000-00805f9b34fb")
) {
    override fun getPayload(): ByteArray {
        val writer = FtmsPacketWriter()
        writer.u8(ranges.resistanceRaw(ranges.minResistanceLevel))
        writer.u8(ranges.resistanceRaw(ranges.maxResistanceLevel))
        writer.u8(ranges.resistanceRaw(ranges.resistanceIncrement))
        return writer.bytes()
    }
}

class SupportedHeartRateRangeCharacteristic(
    gattProfile: AbstractGattProfile,
    private val ranges: FtmsSupportedRanges
) : AbstractSupportedRangeCharacteristic(
    gattProfile,
    UUID.fromString("00002AD7-0000-1000-8000-00805f9b34fb")
) {
    override fun getPayload(): ByteArray {
        val writer = FtmsPacketWriter()
        writer.u8(ranges.minHeartRateBpm)
        writer.u8(ranges.maxHeartRateBpm)
        writer.u8(ranges.heartRateIncrementBpm)
        return writer.bytes()
    }
}

class SupportedPowerRangeCharacteristic(
    gattProfile: AbstractGattProfile,
    private val ranges: FtmsSupportedRanges
) : AbstractSupportedRangeCharacteristic(
    gattProfile,
    UUID.fromString("00002AD8-0000-1000-8000-00805f9b34fb")
) {
    override fun getPayload(): ByteArray {
        val writer = FtmsPacketWriter()
        writer.s16(ranges.minPowerWatt)
        writer.s16(ranges.maxPowerWatt)
        writer.u16(ranges.powerIncrementWatt)
        return writer.bytes()
    }
}
