package de.tobiga.yougattme.gatt.cyclingpower.characteristics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.BaseGattCharacteristic
import de.tobiga.yougattme.gatt.common.ClientConfigDescriptor
import de.tobiga.yougattme.gatt.common.GattNotificationDispatcher
import de.tobiga.yougattme.gatt.common.SensorLocationValues
import de.tobiga.yougattme.gatt.common.littleEndianUInt16
import de.tobiga.yougattme.gatt.common.readUInt16
import de.tobiga.yougattme.gatt.common.readUInt32
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerControlPointOpCodes
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerSimulatorConfiguration
import de.tobiga.yougattme.bridge.RemoteControlDispatcher
import java.util.UUID

private data class CyclingPowerProcedureResult(
    val resultCode: Int,
    val responseParameter: ByteArray = ByteArray(0)
)

class CyclingPowerControlPointCharacteristic(
    gattProfile: AbstractGattProfile,
    private val configuration: CyclingPowerSimulatorConfiguration,
    private val setCumulativeWheelRevolutions: (Long) -> Unit,
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
            UUID.fromString("00002A66-0000-1000-8000-00805f9b34fb"),
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
        val requestCode = value.firstOrNull()?.toInt()?.and(0xFF)
        recordEvent("RX", "${requestName(requestCode)} · ${device.address}", value)
        if (preparedWrite || offset != 0 || requestCode == null) {
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
        // Measurement Content Masking is deliberately per downstream client and stays bridge-local.
        if (requestCode != CyclingPowerControlPointOpCodes.MASK_MEASUREMENT_CONTENT &&
            RemoteControlDispatcher.forwardIfConfigured(
                outputKey = "cycling_power",
                request = value,
                calibration = requestCode == CyclingPowerControlPointOpCodes.START_OFFSET_COMPENSATION ||
                    requestCode == CyclingPowerControlPointOpCodes.START_ENHANCED_OFFSET_COMPENSATION
            ) { remote ->
                if (procedureInProgressForAddress != device.address) return@forwardIfConfigured
                val upstream = remote.response
                val valid = remote.success && upstream != null && upstream.size >= 3 &&
                    (upstream[0].toInt() and 0xFF) == CyclingPowerControlPointOpCodes.RESPONSE_CODE &&
                    (upstream[1].toInt() and 0xFF) == requestCode
                val upstreamSuccess = valid && (upstream!![2].toInt() and 0xFF) == RESULT_SUCCESS
                val localSuccess = !upstreamSuccess || handleProcedure(device, requestCode, value).resultCode == RESULT_SUCCESS
                val response = if (valid && localSuccess) upstream!! else byteArrayOf(
                    CyclingPowerControlPointOpCodes.RESPONSE_CODE.toByte(), requestCode.toByte(),
                    RESULT_OPERATION_FAILED.toByte()
                )
                recordEvent("TX", "${requestName(requestCode)} proxied response", response)
                val server = gattProfile.bluetoothGattServer
                if (server == null) {
                    procedureInProgressForAddress = null
                    return@forwardIfConfigured
                }
                GattNotificationDispatcher.enqueue(server, device, gattCharacteristic, true, response) {
                    if (procedureInProgressForAddress == device.address) procedureInProgressForAddress = null
                }
            }) return
        val result = handleProcedure(device, requestCode, value)
        val response = byteArrayOf(
            CyclingPowerControlPointOpCodes.RESPONSE_CODE.toByte(),
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

    private fun handleProcedure(
        device: BluetoothDevice,
        requestCode: Int,
        value: ByteArray
    ): CyclingPowerProcedureResult {
        if (requestCode !in configuration.supportedProcedures) {
            return CyclingPowerProcedureResult(RESULT_OP_CODE_NOT_SUPPORTED)
        }
        return when (requestCode) {
            CyclingPowerControlPointOpCodes.SET_CUMULATIVE_VALUE -> requireLength(value, 5) {
                setCumulativeWheelRevolutions(readUInt32(value, 1))
                success()
            }

            CyclingPowerControlPointOpCodes.UPDATE_SENSOR_LOCATION -> requireLength(value, 2) {
                val location = value[1].toInt() and 0xFF
                if (!SensorLocationValues.isAssigned(location) || location !in configuration.supportedSensorLocations) {
                    invalid()
                } else {
                    configuration.sensorLocation = location
                    success()
                }
            }

            CyclingPowerControlPointOpCodes.REQUEST_SUPPORTED_SENSOR_LOCATIONS -> requireLength(value, 1) {
                val maxLocations = (gattProfile.getNotificationPayloadSize(device) - 3).coerceAtLeast(0)
                success(
                    configuration.supportedSensorLocations.sorted().take(maxLocations)
                        .map(Int::toByte)
                        .toByteArray()
                )
            }

            CyclingPowerControlPointOpCodes.SET_CRANK_LENGTH -> requireLength(value, 3) {
                configuration.crankLengthHalfMillimeters = readUInt16(value, 1)
                success()
            }

            CyclingPowerControlPointOpCodes.REQUEST_CRANK_LENGTH -> requireLength(value, 1) {
                success(littleEndianUInt16(configuration.crankLengthHalfMillimeters))
            }

            CyclingPowerControlPointOpCodes.SET_CHAIN_LENGTH -> requireLength(value, 3) {
                configuration.chainLengthMillimeters = readUInt16(value, 1)
                success()
            }

            CyclingPowerControlPointOpCodes.REQUEST_CHAIN_LENGTH -> requireLength(value, 1) {
                success(littleEndianUInt16(configuration.chainLengthMillimeters))
            }

            CyclingPowerControlPointOpCodes.SET_CHAIN_WEIGHT -> requireLength(value, 3) {
                configuration.chainWeightGrams = readUInt16(value, 1)
                success()
            }

            CyclingPowerControlPointOpCodes.REQUEST_CHAIN_WEIGHT -> requireLength(value, 1) {
                success(littleEndianUInt16(configuration.chainWeightGrams))
            }

            CyclingPowerControlPointOpCodes.SET_SPAN_LENGTH -> requireLength(value, 3) {
                configuration.spanLengthMillimeters = readUInt16(value, 1)
                success()
            }

            CyclingPowerControlPointOpCodes.REQUEST_SPAN_LENGTH -> requireLength(value, 1) {
                success(littleEndianUInt16(configuration.spanLengthMillimeters))
            }

            CyclingPowerControlPointOpCodes.START_OFFSET_COMPENSATION -> requireLength(value, 1) {
                success(littleEndianUInt16(configuration.offsetCompensationRaw))
            }

            CyclingPowerControlPointOpCodes.MASK_MEASUREMENT_CONTENT -> requireLength(value, 3) {
                val mask = readUInt16(value, 1)
                if (mask and 0xFE00 != 0) {
                    invalid()
                } else {
                    configuration.setMeasurementMask(device.address, mask)
                    success()
                }
            }

            CyclingPowerControlPointOpCodes.REQUEST_SAMPLING_RATE -> requireLength(value, 1) {
                success(byteArrayOf(configuration.samplingRateHz.coerceIn(0, 0xFF).toByte()))
            }

            CyclingPowerControlPointOpCodes.REQUEST_FACTORY_CALIBRATION_DATE -> requireLength(value, 1) {
                val date = configuration.factoryCalibrationDate
                success(
                    littleEndianUInt16(date.year) + byteArrayOf(
                        date.monthValue.toByte(),
                        date.dayOfMonth.toByte(),
                        date.hour.toByte(),
                        date.minute.toByte(),
                        date.second.toByte()
                    )
                )
            }

            CyclingPowerControlPointOpCodes.START_ENHANCED_OFFSET_COMPENSATION -> requireLength(value, 1) {
                enhancedOffsetResponse(device)
            }

            else -> CyclingPowerProcedureResult(RESULT_OP_CODE_NOT_SUPPORTED)
        }
    }

    private fun enhancedOffsetResponse(device: BluetoothDevice): CyclingPowerProcedureResult {
        val failureCode = configuration.enhancedOffsetFailureCode
        if (failureCode != null) {
            if (failureCode != 0xFF) {
                return CyclingPowerProcedureResult(RESULT_OPERATION_FAILED, byteArrayOf(failureCode.toByte()))
            }
            val maxData = (gattProfile.getNotificationPayloadSize(device) - 7).coerceAtLeast(0)
            val data = configuration.enhancedOffsetManufacturerData.take(maxData).toByteArray()
            return CyclingPowerProcedureResult(
                RESULT_OPERATION_FAILED,
                byteArrayOf(0xFF.toByte()) +
                    littleEndianUInt16(configuration.enhancedOffsetCompanyId) +
                    byteArrayOf(data.size.toByte()) + data
            )
        }
        val maxData = (gattProfile.getNotificationPayloadSize(device) - 8).coerceAtLeast(0)
        val data = configuration.enhancedOffsetManufacturerData.take(maxData).toByteArray()
        return success(
            littleEndianUInt16(configuration.offsetCompensationRaw) +
                littleEndianUInt16(configuration.enhancedOffsetCompanyId) +
                byteArrayOf(data.size.toByte()) + data
        )
    }

    fun clearDevice(device: BluetoothDevice) {
        clientConfigDescriptor.removeDevice(device)
        configuration.clearDevice(device.address)
        if (procedureInProgressForAddress == device.address) procedureInProgressForAddress = null
    }

    fun clearDevices() {
        clientConfigDescriptor.clearDevices()
        configuration.clearDevices()
        procedureInProgressForAddress = null
    }

    private inline fun requireLength(
        value: ByteArray,
        expected: Int,
        action: () -> CyclingPowerProcedureResult
    ): CyclingPowerProcedureResult = if (value.size == expected) action() else invalid()

    private fun success(parameter: ByteArray = ByteArray(0)) =
        CyclingPowerProcedureResult(RESULT_SUCCESS, parameter)

    private fun invalid() = CyclingPowerProcedureResult(RESULT_INVALID_PARAMETER)

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
        0x02 -> "Update Sensor Location"
        0x03 -> "Request Supported Sensor Locations"
        0x04 -> "Set Crank Length"
        0x05 -> "Request Crank Length"
        0x06 -> "Set Chain Length"
        0x07 -> "Request Chain Length"
        0x08 -> "Set Chain Weight"
        0x09 -> "Request Chain Weight"
        0x0A -> "Set Span Length"
        0x0B -> "Request Span Length"
        0x0C -> "Start Offset Compensation"
        0x0D -> "Mask Measurement Content"
        0x0E -> "Request Sampling Rate"
        0x0F -> "Request Factory Calibration Date"
        0x10 -> "Start Enhanced Offset Compensation"
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
    }
}
