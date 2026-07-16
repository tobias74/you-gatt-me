package de.tobiga.yougattme.gatt.rsc

import android.bluetooth.BluetoothDevice
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.SingleServiceGattProfile
import de.tobiga.yougattme.gatt.common.ReadOnlyValueCharacteristic
import de.tobiga.yougattme.gatt.common.ScControlPointCharacteristic
import de.tobiga.yougattme.gatt.common.ScControlPointDialect
import de.tobiga.yougattme.gatt.rsc.charateristics.RSCMeasurementCharacteristic
import java.util.UUID

class RunningSpeedAndCadenceProfile(
    private val viewModel: ApplicationViewModel
) : SingleServiceGattProfile() {
    private val measurementCharacteristic = RSCMeasurementCharacteristic(this, viewModel)
    private val featureCharacteristic = ReadOnlyValueCharacteristic(
        this,
        UUID.fromString("00002A54-0000-1000-8000-00805f9b34fb"),
        valueProvider = { RscProtocol.encodeFeature(viewModel.rscConfiguration.featureBitsForGatt()) },
        indications = viewModel.rscConfiguration.featureIndicationsEnabled
    )
    private val sensorLocationCharacteristic =
        if (viewModel.rscConfiguration.sensorLocationCharacteristicEnabled) {
            ReadOnlyValueCharacteristic(
                this,
                UUID.fromString("00002A5D-0000-1000-8000-00805f9b34fb"),
                valueProvider = { byteArrayOf(viewModel.rscConfiguration.sensorLocation.toByte()) }
            )
        } else {
            null
        }
    private val controlPointCharacteristic =
        if (viewModel.rscConfiguration.controlPointCharacteristicEnabled) {
            ScControlPointCharacteristic(
                gattProfile = this,
                dialect = ScControlPointDialect.RSC,
                supportedProcedures = { viewModel.rscConfiguration.supportedProcedures },
                setSensorLocation = { location -> viewModel.rscConfiguration.sensorLocation = location },
                supportedSensorLocations = { viewModel.rscConfiguration.supportedSensorLocations },
                setCumulativeValue = { raw ->
                    viewModel.runningSpeedAndCadenceData.setTotalDistance(raw / 10.0)
                },
                startCalibration = { true },
                recordEvent = { direction, title, payload ->
                    viewModel.recordSensorProtocolEvent("rsc", direction, title, payload)
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
        UUID.fromString("00001814-0000-1000-8000-00805f9b34fb")
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
            "rsc",
            measurementCharacteristic.getCharacteristicUUID().toString(),
            logical,
            packets,
            20
        )
        measurementCharacteristic.publishMeasurementData()
    }
}
