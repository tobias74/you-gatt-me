package de.tobiga.yougattme.gatt.common

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.BaseGattCharacteristic
import de.tobiga.yougattme.gatt.rsc.ScControlPointOpCodes
import java.util.UUID

enum class ScControlPointDialect {
    RSC,
    CSC
}

private data class ScProcedureResult(
    val resultCode: Int,
    val responseParameter: ByteArray = ByteArray(0)
)

class ScControlPointCharacteristic(
    gattProfile: AbstractGattProfile,
    private val dialect: ScControlPointDialect,
    private val supportedProcedures: () -> Set<Int>,
    private val setSensorLocation: (Int) -> Unit,
    private val supportedSensorLocations: () -> Set<Int>,
    private val setCumulativeValue: (Long) -> Unit,
    private val startCalibration: () -> Boolean = { false },
    private val recordEvent: (direction: String, title: String, payload: ByteArray) -> Unit = { _, _, _ -> }
) : BaseGattCharacteristic(gattProfile) {
    val clientConfigDescriptor = ClientConfigDescriptor(
        gattProfile,
        supportsNotifications = false,
        supportsIndications = true
    )
    private var procedureInProgressForAddress: String? = null

    init {
        activeDescriptors.add(clientConfigDescriptor)
        gattCharacteristic = BluetoothGattCharacteristic(
            UUID.fromString("00002A55-0000-1000-8000-00805f9b34fb"),
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        activeDescriptors.forEach { descriptor ->
            gattCharacteristic.addDescriptor(descriptor.gattDescriptor)
        }
    }

    override fun getCharacteristicUUID(): UUID = gattCharacteristic.uuid

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        gattProfile.bluetoothGattServer?.sendResponse(
            device,
            requestId,
            BluetoothGatt.GATT_READ_NOT_PERMITTED,
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
        recordEvent("RX", "${requestName(value.firstOrNull()?.toInt()?.and(0xFF))} · ${device.address}", value)
        if (preparedWrite || offset != 0 || value.isEmpty()) {
            sendWriteResponse(device, requestId, responseNeeded, BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH)
            return
        }
        if (!clientConfigDescriptor.isIndicationEnabled(device)) {
            sendWriteResponse(device, requestId, responseNeeded, ATT_ERROR_CCCD_IMPROPERLY_CONFIGURED)
            return
        }
        if (procedureInProgressForAddress != null) {
            sendWriteResponse(device, requestId, responseNeeded, ATT_ERROR_PROCEDURE_ALREADY_IN_PROGRESS)
            return
        }

        sendWriteResponse(device, requestId, responseNeeded, BluetoothGatt.GATT_SUCCESS)
        procedureInProgressForAddress = device.address
        val requestCode = value[0].toInt() and 0xFF
        val result = handleProcedure(requestCode, value)
        val response = byteArrayOf(
            ScControlPointOpCodes.RESPONSE_CODE.toByte(),
            requestCode.toByte(),
            result.resultCode.toByte()
        ) + result.responseParameter
        recordEvent("TX", "${requestName(requestCode)} response · ${resultName(result.resultCode)}", response)
        val server = gattProfile.bluetoothGattServer
        if (server == null) {
            procedureInProgressForAddress = null
            return
        }
        GattNotificationDispatcher.enqueue(
            server = server,
            device = device,
            characteristic = gattCharacteristic,
            confirm = true,
            value = response,
            onComplete = {
                if (procedureInProgressForAddress == device.address) {
                    procedureInProgressForAddress = null
                }
            }
        )
    }

    private fun handleProcedure(requestCode: Int, value: ByteArray): ScProcedureResult {
        if (requestCode !in supportedProcedures()) {
            return ScProcedureResult(RESULT_OP_CODE_NOT_SUPPORTED)
        }
        return when (requestCode) {
            0x01 -> {
                if (value.size != 5) {
                    ScProcedureResult(RESULT_INVALID_PARAMETER)
                } else {
                    setCumulativeValue(readUInt32(value, 1))
                    ScProcedureResult(RESULT_SUCCESS)
                }
            }

            0x02 -> {
                if (dialect != ScControlPointDialect.RSC) {
                    ScProcedureResult(RESULT_OP_CODE_NOT_SUPPORTED)
                } else if (value.size != 1) {
                    ScProcedureResult(RESULT_INVALID_PARAMETER)
                } else if (startCalibration()) {
                    ScProcedureResult(RESULT_SUCCESS)
                } else {
                    ScProcedureResult(RESULT_OPERATION_FAILED)
                }
            }

            updateLocationOpCode() -> {
                val location = value.getOrNull(1)?.toInt()?.and(0xFF)
                if (value.size != 2 || location == null || location !in supportedSensorLocations()) {
                    ScProcedureResult(RESULT_INVALID_PARAMETER)
                } else {
                    setSensorLocation(location)
                    ScProcedureResult(RESULT_SUCCESS)
                }
            }

            requestLocationsOpCode() -> {
                if (value.size != 1) {
                    ScProcedureResult(RESULT_INVALID_PARAMETER)
                } else {
                    ScProcedureResult(
                        RESULT_SUCCESS,
                        supportedSensorLocations().sorted().take(MAX_DEFAULT_MTU_LOCATIONS)
                            .map(Int::toByte)
                            .toByteArray()
                    )
                }
            }

            else -> ScProcedureResult(RESULT_OP_CODE_NOT_SUPPORTED)
        }
    }

    fun clearDevice(device: BluetoothDevice) {
        clientConfigDescriptor.removeDevice(device)
        if (procedureInProgressForAddress == device.address) procedureInProgressForAddress = null
    }

    fun clearDevices() {
        clientConfigDescriptor.clearDevices()
        procedureInProgressForAddress = null
    }

    private fun updateLocationOpCode(): Int = if (dialect == ScControlPointDialect.RSC) 0x03 else 0x03

    private fun requestLocationsOpCode(): Int = if (dialect == ScControlPointDialect.RSC) 0x04 else 0x04

    private fun sendWriteResponse(
        device: BluetoothDevice,
        requestId: Int,
        responseNeeded: Boolean,
        status: Int
    ) {
        if (responseNeeded) {
            gattProfile.bluetoothGattServer?.sendResponse(device, requestId, status, 0, null)
        }
    }

    private fun requestName(code: Int?): String = when (code) {
        0x01 -> "Set Cumulative Value"
        0x02 -> if (dialect == ScControlPointDialect.RSC) "Start Sensor Calibration" else "Reserved 0x02"
        0x03 -> "Update Sensor Location"
        0x04 -> "Request Supported Sensor Locations"
        null -> "Empty request"
        else -> "Op Code 0x${code.toString(16).uppercase().padStart(2, '0')}"
    }

    private fun resultName(code: Int): String = when (code) {
        RESULT_SUCCESS -> "Success"
        RESULT_OP_CODE_NOT_SUPPORTED -> "Op Code Not Supported"
        RESULT_INVALID_PARAMETER -> "Invalid Parameter"
        else -> "Operation Failed"
    }

    private companion object {
        const val ATT_ERROR_PROCEDURE_ALREADY_IN_PROGRESS = 0x80
        const val ATT_ERROR_CCCD_IMPROPERLY_CONFIGURED = 0x81
        const val RESULT_SUCCESS = 0x01
        const val RESULT_OP_CODE_NOT_SUPPORTED = 0x02
        const val RESULT_INVALID_PARAMETER = 0x03
        const val RESULT_OPERATION_FAILED = 0x04
        const val MAX_DEFAULT_MTU_LOCATIONS = 17
    }
}
