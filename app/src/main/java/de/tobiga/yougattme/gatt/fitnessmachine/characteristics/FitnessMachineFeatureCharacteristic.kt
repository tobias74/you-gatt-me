package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.TobigaGattCharacteristic
import de.tobiga.yougattme.gatt.common.UserDescriptor
import java.util.*
import kotlin.experimental.or

class FitnessMachineFeatureCharacteristic(gattProfile: GattProfile) : TobigaGattCharacteristic(gattProfile) {
    val GATT_FITNESS_MACHINE_FEATURE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002ACC-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID {
        return GATT_FITNESS_MACHINE_FEATURE_CHARACTERISTIC_UUID
    }

    val BITMASK_AVERAGE_SPEED_SUPPORTED =                 (0b00000001).toByte()
    val BITMASK_CADENCE_SUPPORTED =                       (0b00000010).toByte()
    val BITMASK_TOTAL_DISTANCE_SUPPORTED =                (0b00000100).toByte()
    val BITMASK_INCLINATION_SUPPORTED =                   (0b00001000).toByte()
    val BITMASK_ELEVATION_GAIN_SUPPORTED =                (0b00010000).toByte()
    val BITMASK_PACE_SUPPORTED =                          (0b00100000).toByte()
    val BITMASK_STEP_COUNT_SUPPORTED =                    (0b01000000).toByte()
    val BITMASK_RESISTANCE_LEVEL_SUPPORTED =              (0b10000000).toByte()

    val BITMASK2_STRIDE_COUNT_SUPPORTED =                  (0b00000001).toByte()
    val BITMASK2_EXPENDED_ENERGY_SUPPORTED =               (0b00000010).toByte()
    val BITMASK2_HEART_RATE_MEASUREMENT_SUPPORTED =        (0b00000100).toByte()
    val BITMASK2_METABOLIC_EQUIVALENT_SUPPORTED =          (0b00001000).toByte()
    val BITMASK2_ELAPSED_TIME_SUPPORTED =                  (0b00010000).toByte()
    val BITMASK2_REMAINING_TIME_SUPPORTED =                (0b00100000).toByte()
    val BITMASK2_POWER_MEASUREMENT_SUPPORTED =             (0b01000000).toByte()
    val BITMASK2_FORCE_ON_BELT_AND_POWER_OUTPUT_SUPPORTED =(0b10000000).toByte()

    val BITMASK3_USER_DATA_RETENTION_SUPPORTED =           (0b00000001).toByte()


    val BITMASK5_SPEED_TARGET_SETTING_SUPPORTED =                  (0b00000001).toByte()
    val BITMASK5_INCLINATION_TARGET_SETTING_SUPPORTED =            (0b00000010).toByte()
    val BITMASK5_RESTISTANCE_TARGET_SETTING_SUPPORTED =            (0b00000100).toByte()
    val BITMASK5_POWER_TARGET_SETTING_SUPPORTED =                  (0b00001000).toByte()
    val BITMASK5_HEART_RATE_TARGET_SETTING_SUPPORTED =             (0b00010000).toByte()
    val BITMASK5_TARGET_EXPENDED_ENERGY_CONFIGURATION_SUPPORTED =  (0b00100000).toByte()
    val BITMASK5_TARGET_STEP_NUMBER_CONFIGURATION_SUPPORTED =      (0b01000000).toByte()
    val BITMASK5_TARGET_STRIDE_NUMBER_CONFIGURATION_SUPPORTED =    (0b10000000).toByte()


    val BITMASK6_TARGET_DISTANCE_CONFIGURATION_SUPPORTED =         (0b00000001).toByte()
    val BITMASK6_TARGET_TRAINING_TIME_CONFIGURATION_SUPPORTED =    (0b00000010).toByte()
    val BITMASK6_TARGET_TIME_2_HEART_RATE_ZONES_SUPPORTED =        (0b00000100).toByte()
    val BITMASK6_TARGET_TIME_3_HEART_RATE_ZONES_SUPPORTED =        (0b00001000).toByte()
    val BITMASK6_TARGET_TIME_5_HEART_RATE_ZONES_SUPPORTED =        (0b00010000).toByte()
    val BITMASK6_INDOOR_BIKE_SIMULATION_PARAMETERS_SUPPORTED =     (0b00100000).toByte()
    val BITMASK6_WHEEL_CIRCUMFERENCE_CONFIGURATION_SUPPORTED =     (0b01000000).toByte()
    val BITMASK6_SPIN_DOWN_CONTROL_SUPPORTED =                     (0b10000000).toByte()

    val BITMASK7_TARGET_CADENCE_CONFIGURATION_SUPPORTED =          (0b00000001).toByte()


    init {
        activeDescriptors.add(UserDescriptor(gattProfile))

        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_FITNESS_MACHINE_FEATURE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)


        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }

    }

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        gattProfile.bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,0, getFeatureCharacteristicData())

    }

    override fun onWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
    }


    private fun getFeatureCharacteristicData(): ByteArray? {

        val field = ByteArray(8)

        val firstByte = BITMASK_AVERAGE_SPEED_SUPPORTED or BITMASK_CADENCE_SUPPORTED or BITMASK_TOTAL_DISTANCE_SUPPORTED or BITMASK_INCLINATION_SUPPORTED or BITMASK_PACE_SUPPORTED
        val secondByte = BITMASK2_EXPENDED_ENERGY_SUPPORTED or BITMASK2_HEART_RATE_MEASUREMENT_SUPPORTED or BITMASK2_ELAPSED_TIME_SUPPORTED or BITMASK2_POWER_MEASUREMENT_SUPPORTED
        val fifthByte = BITMASK5_SPEED_TARGET_SETTING_SUPPORTED or BITMASK5_INCLINATION_TARGET_SETTING_SUPPORTED or BITMASK5_RESTISTANCE_TARGET_SETTING_SUPPORTED or BITMASK5_POWER_TARGET_SETTING_SUPPORTED
        val sixthByte = BITMASK6_INDOOR_BIKE_SIMULATION_PARAMETERS_SUPPORTED or BITMASK6_WHEEL_CIRCUMFERENCE_CONFIGURATION_SUPPORTED

        field[0] = (firstByte).toByte()
        field[1] = (secondByte).toByte()
        field[2] = (0x00).toByte()
        field[3] = (0x00).toByte()
        field[4] = (fifthByte).toByte()
        field[5] = (sixthByte).toByte()
        field[6] = (0x00).toByte()
        field[7] = (0x00).toByte()


        return field
    }


}