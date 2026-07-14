package de.tobiga.yougattme.gatt

import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattService

/**
 * Base for profiles that host exactly one primary service.
 * Subclasses must:
 * - override getServiceUUIDs() to return a single UUID
 * - populate activeCharacteristics in their init block
 */abstract class SingleServiceGattProfile : AbstractGattProfile() {

    final override fun createServices(gattServer: BluetoothGattServer): List<BluetoothGattService> {
        // keep for notify callbacks
        bluetoothGattServer = gattServer

        // pull the one-and-only service UUID
        val uuids = getServiceUUIDs()
        require(uuids.size == 1) {
            "SingleServiceGattProfile requires exactly one service UUID, but got: $uuids"
        }
        val svcUuid = uuids[0]

        // build the service and wire up every characteristic
        val service = BluetoothGattService(
            svcUuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        activeCharacteristics.forEach { char ->
            service.addCharacteristic(char.gattCharacteristic)
        }

        return listOf(service)
    }
}
