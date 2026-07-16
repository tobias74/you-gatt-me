package de.tobiga.yougattme.gatt.cyclingpower

import android.bluetooth.BluetoothDevice
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.SingleServiceGattProfile
import de.tobiga.yougattme.gatt.common.ReadOnlyValueCharacteristic
import de.tobiga.yougattme.gatt.cyclingpower.characteristics.CyclingPowerControlPointCharacteristic
import de.tobiga.yougattme.gatt.cyclingpower.characteristics.CyclingPowerMeasurementCharacteristic
import de.tobiga.yougattme.gatt.cyclingpower.characteristics.CyclingPowerVectorCharacteristic
import java.util.UUID

class CyclingPowerProfile(
    private val viewModel: ApplicationViewModel
) : SingleServiceGattProfile(), CyclingPowerBroadcastSource {
    private var broadcastListener: ((Boolean, ByteArray) -> Unit)? = null
    private val broadcastEnabledAddresses = mutableSetOf<String>()
    private val measurementCharacteristic = CyclingPowerMeasurementCharacteristic(
        this,
        viewModel,
        onBroadcastChanged = { device, enabled ->
            if (enabled) broadcastEnabledAddresses.add(device.address)
            else broadcastEnabledAddresses.remove(device.address)
            publishBroadcastState()
        }
    )
    private val featureCharacteristic = ReadOnlyValueCharacteristic(
        this,
        UUID.fromString("00002A65-0000-1000-8000-00805f9b34fb"),
        valueProvider = {
            CyclingPowerProtocol.encodeFeature(viewModel.cyclingPowerConfiguration.featureBitsForGatt())
        },
        indications = viewModel.cyclingPowerConfiguration.featureIndicationsEnabled
    )
    private val sensorLocationCharacteristic =
        if (viewModel.cyclingPowerConfiguration.sensorLocationCharacteristicEnabled) {
            ReadOnlyValueCharacteristic(
                this,
                UUID.fromString("00002A5D-0000-1000-8000-00805f9b34fb"),
                valueProvider = { byteArrayOf(viewModel.cyclingPowerConfiguration.sensorLocation.toByte()) }
            )
        } else {
            null
        }
    private val controlPointCharacteristic =
        if (viewModel.cyclingPowerConfiguration.controlPointCharacteristicEnabled) {
            CyclingPowerControlPointCharacteristic(
                this,
                viewModel.cyclingPowerConfiguration,
                viewModel::setCyclingPowerCumulativeWheelRevolutions,
                recordEvent = { direction, title, payload ->
                    viewModel.recordSensorProtocolEvent("cycling_power", direction, title, payload)
                }
            )
        } else {
            null
        }
    private val vectorCharacteristic =
        if (viewModel.cyclingPowerConfiguration.vectorCharacteristicEnabled) {
            CyclingPowerVectorCharacteristic(this, viewModel)
        } else {
            null
        }

    init {
        activeCharacteristics.add(measurementCharacteristic)
        activeCharacteristics.add(featureCharacteristic)
        sensorLocationCharacteristic?.let(activeCharacteristics::add)
        controlPointCharacteristic?.let(activeCharacteristics::add)
        vectorCharacteristic?.let(activeCharacteristics::add)
    }

    override fun getServiceUUIDs(): List<UUID> = listOf(
        UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
    )

    override fun clearAllDevices() {
        measurementCharacteristic.clearDevices()
        featureCharacteristic.clearDevices()
        controlPointCharacteristic?.clearDevices()
        vectorCharacteristic?.clearDevices()
        viewModel.cyclingPowerConfiguration.clearDevices()
        broadcastEnabledAddresses.clear()
        publishBroadcastState()
        clearDeviceMtus()
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        super.onDeviceConnected(device)
        viewModel.resetCyclingPowerConnectionAccumulators()
        viewModel.cyclingPowerConfiguration.clearDevice(device.address)
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        measurementCharacteristic.clearDevice(device)
        featureCharacteristic.clearDevice(device)
        controlPointCharacteristic?.clearDevice(device)
        vectorCharacteristic?.clearDevice(device)
        viewModel.cyclingPowerConfiguration.clearDevice(device.address)
        broadcastEnabledAddresses.remove(device.address)
        publishBroadcastState()
        forgetDeviceMtu(device)
    }

    override fun triggerPeriodicTimer() {
        val measurementLogical = measurementCharacteristic.getData()
        val measurementPackets = measurementCharacteristic.previewPackets(20)
        viewModel.recordSensorMeasurement(
            "cycling_power",
            measurementCharacteristic.getCharacteristicUUID().toString(),
            measurementLogical,
            measurementPackets,
            20
        )
        measurementCharacteristic.publishMeasurementData()

        vectorCharacteristic?.let { vector ->
            val packets = vector.getNotificationPackets(20)
            viewModel.recordSensorMeasurement(
                "cycling_power_vector",
                vector.getCharacteristicUUID().toString(),
                packets.firstOrNull() ?: ByteArray(0),
                packets,
                20
            )
            vector.publishMeasurementData()
        }
        if (broadcastEnabledAddresses.isNotEmpty()) publishBroadcastState()
    }

    override fun setCyclingPowerBroadcastListener(
        listener: ((enabled: Boolean, payload: ByteArray) -> Unit)?
    ) {
        broadcastListener = listener
        publishBroadcastState()
    }

    override fun destroy() {
        broadcastEnabledAddresses.clear()
        publishBroadcastState()
        broadcastListener = null
    }

    private fun publishBroadcastState() {
        broadcastListener?.invoke(
            broadcastEnabledAddresses.isNotEmpty(),
            measurementCharacteristic.getBroadcastData()
        )
    }
}
