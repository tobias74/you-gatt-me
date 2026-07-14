package de.tobiga.yougattme.gatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattService
import android.util.Log

private const val TAG = "SafeGattServiceManager"

class SafeGattServiceManager(
    private val gattServer: BluetoothGattServer
) {
    // Queue of services waiting to be added
    private val pendingServices = mutableListOf<BluetoothGattService>()
    // List of successfully added services
    private val addedServices = mutableListOf<BluetoothGattService>()
    // Hook for when we've drained the queue
    var onAllServicesAdded: (() -> Unit)? = null
    // Once destroy() is called, we stop all further adds
    private var destroyed = false

    /** Enqueue all your services up‑front.  */
    fun enqueueServices(services: List<BluetoothGattService>) {
        if (destroyed) return
        pendingServices.clear()
        pendingServices += services
    }

    /** Start the one‑by‑one addService() process. */
    fun startAdding() {
        if (destroyed) return

        if (pendingServices.isEmpty()) {
            // nothing left → notify
            onAllServicesAdded?.invoke()
        } else {
            // take the head of the queue and fire off the addService()
            val next = pendingServices.first()
            Log.d(TAG, "Adding GATT service: ${next.uuid}")
            gattServer.addService(next)
        }
    }

    /**
     * Must be called from your BluetoothGattServerCallback.onServiceAdded(...)
     * to advance the queue (or abort on error).
     */
    fun onServiceAdded(status: Int, service: BluetoothGattService) {
        if (destroyed) return

        if (status == BluetoothGatt.GATT_SUCCESS) {
            // mark it added
            addedServices += service
        } else {
            Log.e(TAG, "Failed to add service ${service.uuid}: status=$status")
        }

        // Remove it from pending (whether success or not) and keep going
        pendingServices.remove(service)
        startAdding()
    }

    /**
     * Tear everything down. No more adds will happen, and any already‑added
     * services get removed too.
     */
    fun destroy() {
        destroyed = true

        // Remove any services we did add
        for (svc in addedServices) {
            try {
                gattServer.removeService(svc)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing service ${svc.uuid}", e)
            }
        }

        pendingServices.clear()
        addedServices.clear()
    }
}
