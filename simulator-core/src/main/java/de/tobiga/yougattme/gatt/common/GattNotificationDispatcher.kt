package de.tobiga.yougattme.gatt.common

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothStatusCodes
import android.util.Log
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Serializes every notification and indication on a GATT server. Android's
 * server API requires waiting for onNotificationSent before submitting the next
 * value, even when the values target different connections.
 */
object GattNotificationDispatcher {
    private data class PendingValue(
        val device: BluetoothDevice,
        val characteristic: BluetoothGattCharacteristic,
        val confirm: Boolean,
        val value: ByteArray,
        val coalesceKey: String?,
        val batchId: Long?,
        val onComplete: ((Int) -> Unit)?
    )

    private data class ServerQueue(
        val pending: ArrayDeque<PendingValue> = ArrayDeque(),
        var inFlight: PendingValue? = null
    )

    private val servers = IdentityHashMap<BluetoothGattServer, ServerQueue>()
    private val batchSequence = AtomicLong(0)

    @Synchronized
    fun enqueue(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        confirm: Boolean,
        value: ByteArray,
        coalesceKey: String? = null,
        onComplete: ((Int) -> Unit)? = null
    ) {
        val queue = servers.getOrPut(server) { ServerQueue() }
        if (coalesceKey != null) {
            queue.pending.removeIf { item ->
                item.device.address == device.address && item.coalesceKey == coalesceKey
            }
        }
        queue.pending.addLast(
            PendingValue(
                device,
                characteristic,
                confirm,
                value.copyOf(),
                coalesceKey,
                null,
                onComplete
            )
        )
        sendNext(server, queue)
    }

    @Synchronized
    fun enqueueBatch(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        confirm: Boolean,
        values: List<ByteArray>,
        coalesceKey: String
    ) {
        val queue = servers.getOrPut(server) { ServerQueue() }
        val keyPrefix = "$coalesceKey:"
        queue.pending.removeIf { item ->
            item.device.address == device.address &&
                item.coalesceKey?.startsWith(keyPrefix) == true
        }

        val batchId = batchSequence.incrementAndGet()
        values.forEachIndexed { index, value ->
            queue.pending.addLast(
                PendingValue(
                    device = device,
                    characteristic = characteristic,
                    confirm = confirm,
                    value = value.copyOf(),
                    coalesceKey = "$keyPrefix$index",
                    batchId = batchId,
                    onComplete = null
                )
            )
        }
        sendNext(server, queue)
    }

    @Synchronized
    fun onNotificationSent(server: BluetoothGattServer, device: BluetoothDevice, status: Int) {
        val queue = servers[server] ?: return
        val completed = queue.inFlight ?: return
        if (completed.device.address != device.address) {
            Log.w(
                "GattNotificationQueue",
                "Ignoring notification callback for ${device.address}; waiting for ${completed.device.address}"
            )
            return
        }
        queue.inFlight = null
        completed.onComplete?.invoke(status)
        sendNext(server, queue)
        removeIfIdle(server, queue)
    }

    @Synchronized
    fun clearDevice(server: BluetoothGattServer, device: BluetoothDevice) {
        val queue = servers[server] ?: return
        if (queue.inFlight?.device?.address == device.address) {
            queue.inFlight?.onComplete?.invoke(BluetoothGatt.GATT_FAILURE)
            queue.inFlight = null
        }
        val iterator = queue.pending.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.device.address == device.address) {
                item.onComplete?.invoke(BluetoothGatt.GATT_FAILURE)
                iterator.remove()
            }
        }
        sendNext(server, queue)
        removeIfIdle(server, queue)
    }

    @Synchronized
    fun clearServer(server: BluetoothGattServer) {
        val queue = servers.remove(server) ?: return
        queue.inFlight?.onComplete?.invoke(BluetoothGatt.GATT_FAILURE)
        queue.pending.forEach { item -> item.onComplete?.invoke(BluetoothGatt.GATT_FAILURE) }
    }

    private fun sendNext(server: BluetoothGattServer, queue: ServerQueue) {
        if (queue.inFlight != null) {
            return
        }
        while (queue.pending.isNotEmpty()) {
            val next = queue.pending.removeFirst()
            queue.inFlight = next
            val result = server.notifyCharacteristicChanged(
                next.device,
                next.characteristic,
                next.confirm,
                next.value
            )
            if (result == BluetoothStatusCodes.SUCCESS) {
                return
            }
            Log.w(
                "GattNotificationQueue",
                "notifyCharacteristicChanged failed immediately: status=$result, uuid=${next.characteristic.uuid}"
            )
            queue.inFlight = null
            next.onComplete?.invoke(BluetoothGatt.GATT_FAILURE)
        }
    }

    private fun removeIfIdle(server: BluetoothGattServer, queue: ServerQueue) {
        if (queue.inFlight == null && queue.pending.isEmpty()) {
            servers.remove(server)
        }
    }
}
