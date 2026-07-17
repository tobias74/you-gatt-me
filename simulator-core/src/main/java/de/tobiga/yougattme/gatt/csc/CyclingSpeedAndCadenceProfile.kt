package de.tobiga.yougattme.gatt.csc

import android.bluetooth.BluetoothDevice
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.SingleServiceGattProfile
import de.tobiga.yougattme.gatt.common.ReadOnlyValueCharacteristic
import de.tobiga.yougattme.gatt.common.ScControlPointCharacteristic
import de.tobiga.yougattme.gatt.common.ScControlPointDialect
import de.tobiga.yougattme.gatt.csc.characteristics.CSCMeasurementCharacteristic
import java.util.UUID

class CyclingSpeedAndCadenceProfile(
    private val viewModel: ApplicationViewModel
) : SingleServiceGattProfile() {
    override val bridgeOutputKey: String = "csc"
    private val measurementCharacteristic = CSCMeasurementCharacteristic(this, viewModel)
    private val featureCharacteristic = ReadOnlyValueCharacteristic(
        this,
        UUID.fromString("00002A5C-0000-1000-8000-00805f9b34fb"),
        valueProvider = { CscProtocol.encodeFeature(viewModel.cscConfiguration.featureBitsForGatt()) },
        indications = viewModel.cscConfiguration.featureIndicationsEnabled
    )
    private val sensorLocationCharacteristic =
        if (viewModel.cscConfiguration.sensorLocationCharacteristicEnabled) {
            ReadOnlyValueCharacteristic(
                this,
                UUID.fromString("00002A5D-0000-1000-8000-00805f9b34fb"),
                valueProvider = { byteArrayOf(viewModel.cscConfiguration.sensorLocation.toByte()) }
            )
        } else {
            null
        }
    private val controlPointCharacteristic =
        if (viewModel.cscConfiguration.controlPointCharacteristicEnabled) {
            ScControlPointCharacteristic(
                gattProfile = this,
                dialect = ScControlPointDialect.CSC,
                supportedProcedures = { viewModel.cscConfiguration.supportedProcedures },
                setSensorLocation = { location -> viewModel.cscConfiguration.sensorLocation = location },
                supportedSensorLocations = { viewModel.cscConfiguration.supportedSensorLocations },
                setCumulativeValue = viewModel::applyCscCumulativeWheelRevolutions,
                recordEvent = { direction, title, payload ->
                    viewModel.recordSensorProtocolEvent("csc", direction, title, payload)
                }
            )
        } else {
            null
        }

    init {
        activeCharacteristics.add(measurementCharacteristic)
        activeCharacteristics.add(featureCharacteristic)
        sensorLocationCharacteristic?.let(activeCharacteristics::add)
        controlPointCharacteristic?.let(activeCharacteristics::add)
    }

    override fun getServiceUUIDs(): List<UUID> = listOf(
        UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
    )

    override fun clearAllDevices() {
        measurementCharacteristic.clientConfigDescriptor.clearDevices()
        featureCharacteristic.clearDevices()
        controlPointCharacteristic?.clearDevices()
        clearDeviceMtus()
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        measurementCharacteristic.clientConfigDescriptor.removeDevice(device)
        featureCharacteristic.clearDevice(device)
        controlPointCharacteristic?.clearDevice(device)
        forgetDeviceMtu(device)
    }

    override fun triggerPeriodicTimer() {
        val logical = measurementCharacteristic.getData()
        val packets = measurementCharacteristic.getNotificationPackets(20)
        viewModel.recordSensorMeasurement(
            "csc",
            measurementCharacteristic.getCharacteristicUUID().toString(),
            logical,
            packets,
            20
        )
        measurementCharacteristic.publishMeasurementData()
    }
}
