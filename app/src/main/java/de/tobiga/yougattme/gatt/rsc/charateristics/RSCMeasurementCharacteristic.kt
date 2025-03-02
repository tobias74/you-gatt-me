package de.tobiga.yougattme.gatt.rsc.charateristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.rsc.RunningSpeedAndCadenceProfileData
import java.util.*
import kotlin.experimental.or

class RSCMeasurementCharacteristic(gattProfile: GattProfile, val profileData: RunningSpeedAndCadenceProfileData) : AbstractDataMeasurementCharacteristic(gattProfile) {

    val GATT_RSC_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A53-0000-1000-8000-00805f9b34fb")

    val BITMASK_INSTANT_STRIDE_LENGTH =      (0b00000001).toByte()
    val BITMASK_TOTAL_DISTANCE =             (0b00000010).toByte()
    val BITMASK_WALKING_OR_RUNNING_STATUS =  (0b00000100).toByte()

    override fun getCharacteristicUUID(): UUID {
        return GATT_RSC_MEASUREMENT_CHARACTERISTIC_UUID
    }

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_RSC_MEASUREMENT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ)

        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getData(): ByteArray {
        val speedBytes = encodeInstantSpeed(profileData.instantaneousSpeed)

        var flags = 0x00.toByte()
        flags = flags or BITMASK_TOTAL_DISTANCE

        val field = ByteArray(8)
        field[0] = flags.toByte()
        field[1] = speedBytes[0]
        field[2] = speedBytes[1]
        field[3] = (profileData.instantaneousCadence and 0xFF).toByte()


        val totalDistanceValue = (profileData.totalDistance * 10).toInt()
        field[4] = (totalDistanceValue and 0xFF).toByte()
        field[5] = ((totalDistanceValue shr 8) and 0xFF).toByte()
        field[6] = ((totalDistanceValue shr 16) and 0xFF).toByte()
        field[7] = ((totalDistanceValue shr 24) and 0xFF).toByte()

        return field
    }

    private fun encodeInstantSpeed(instantSpeed: Double): ByteArray {
        val field = ByteArray(2)
        val transformedSpeed = (256 * instantSpeed).toInt()
        field[0] = (transformedSpeed and 0xFF).toByte()
        field[1] = (transformedSpeed shr 8).toByte()
        return field
    }
}
