package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.TobigaGattCharacteristic
import de.tobiga.yougattme.gatt.common.ClientConfigDescriptor
import de.tobiga.yougattme.gatt.common.ServerConfigDescriptor
import de.tobiga.yougattme.gatt.common.UserDescriptor
import de.tobiga.yougattme.tools.byteToInt
import de.tobiga.yougattme.tools.bytesToInt16
import de.tobiga.yougattme.tools.toHex
import java.util.*

object REQUEST_CODE {
    const val REQUEST_CONTROL = 0x00
    const val RESET = 0x01
    const val SET_TARGET_SPEED = 0x02
    const val SET_TARGET_INCLINATION = 0x03
    const val SET_TARGET_RESISTANCE_LEVEL = 0x04
    const val SET_TARGET_POWER = 0x05
    const val START_OR_RESUME = 0x07
    const val STOP_OR_PAUSE = 0x08
    const val SET_INDOOR_BIKE_SIMULATION = 0x11
}

const val RESPONSE_CODE = 0x80

object RESULT_CODE {
    const val SUCCESS = 0x01
    const val OP_CODE_NOT_SUPPORTED = 0x02
    const val INVALID_PARAMETER = 0x03
    const val OPERATION_FAILED = 0x04
    const val CONTROL_NOT_PERMITTED = 0x05
}

class FitnessMachineControlPointCharacteristic(gattProfile: GattProfile, val mainViewModel: ApplicationViewModel) : TobigaGattCharacteristic(gattProfile) {

    var clientConfigDescriptor: ClientConfigDescriptor
    val GATT_FITNESS_MACHINE_CONTROL_POINT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002AD9-0000-1000-8000-00805f9b34fb")


    override fun getCharacteristicUUID(): UUID {
        return GATT_FITNESS_MACHINE_CONTROL_POINT_CHARACTERISTIC_UUID
    }

    init {
        clientConfigDescriptor = ClientConfigDescriptor(gattProfile)
        activeDescriptors.add(clientConfigDescriptor)
        activeDescriptors.add(UserDescriptor(gattProfile))
        activeDescriptors.add(ServerConfigDescriptor(gattProfile))


        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_FITNESS_MACHINE_CONTROL_POINT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_WRITE)


        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)

        }

    }

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        gattProfile.bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,0, null)

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
            gattProfile.bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,0, null)
        }

        val code = value[0].toInt()

        val byteList = when (code) {
            REQUEST_CODE.REQUEST_CONTROL -> {
                mainViewModel.controlHasBeenTakenInControlPoint.postValue(true)
                arrayOf(RESPONSE_CODE, code, RESULT_CODE.SUCCESS).map { it.toByte() }
            }
            REQUEST_CODE.RESET -> {
                mainViewModel.controlHasBeenTakenInControlPoint.postValue(false)
                arrayOf(RESPONSE_CODE, code, RESULT_CODE.SUCCESS).map { it.toByte() }
            }
            REQUEST_CODE.SET_TARGET_POWER -> {
                mainViewModel.receivedTargetPowerInControlPoint.postValue(byteToInt(value.copyOfRange(1,3)))
                arrayOf(RESPONSE_CODE, code, RESULT_CODE.SUCCESS).map { it.toByte() }
            }
            REQUEST_CODE.SET_TARGET_INCLINATION -> {
                val gradeValue = bytesToInt16(value.copyOfRange(1,3))
                mainViewModel.ftmsReceivedTargetInclination.postValue(gradeValue.toDouble() * 0.1)
                arrayOf(RESPONSE_CODE, code, RESULT_CODE.SUCCESS).map { it.toByte() }
            }
            REQUEST_CODE.SET_INDOOR_BIKE_SIMULATION -> {
                val gradeValue = bytesToInt16(value.copyOfRange(3,5))
                mainViewModel.receivedIndoorBikeGrade.postValue(gradeValue.toDouble() * 0.01)
                arrayOf(RESPONSE_CODE, code, RESULT_CODE.SUCCESS).map { it.toByte() }
            }
            REQUEST_CODE.START_OR_RESUME -> {
                mainViewModel.fitnessMachineHasBeenStarted.postValue(true)
                arrayOf(RESPONSE_CODE, code, RESULT_CODE.SUCCESS).map { it.toByte() }
            }
            else -> arrayOf(RESPONSE_CODE, code, RESULT_CODE.OP_CODE_NOT_SUPPORTED).map { it.toByte() }
        }

        gattCharacteristic.value = byteList.toByteArray()
        gattProfile.bluetoothGattServer?.notifyCharacteristicChanged(device, gattCharacteristic, false)

    }

}