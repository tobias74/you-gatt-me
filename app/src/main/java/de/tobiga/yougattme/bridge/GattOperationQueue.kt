package de.tobiga.yougattme.bridge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Handler
import de.tobiga.yougattme.bridge.StandardBleUuids
import java.util.UUID

internal sealed class GattOperation(
    val timeoutMillis: Long = 10_000L,
    val completion: (Boolean, ByteArray?) -> Unit = { _, _ -> }
) {
    class RequestMtu(val mtu: Int, completion: (Boolean, ByteArray?) -> Unit = { _, _ -> }) :
        GattOperation(completion = completion)
    class Discover(completion: (Boolean, ByteArray?) -> Unit) : GattOperation(completion = completion)
    class Read(val characteristic: BluetoothGattCharacteristic, completion: (Boolean, ByteArray?) -> Unit) :
        GattOperation(completion = completion)
    class Subscribe(val characteristic: BluetoothGattCharacteristic, val indications: Boolean,
        completion: (Boolean, ByteArray?) -> Unit = { _, _ -> }) : GattOperation(completion = completion)
    class Write(
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray,
        val awaitIndication: Boolean,
        timeoutMillis: Long,
        completion: (Boolean, ByteArray?) -> Unit
    ) : GattOperation(timeoutMillis, completion)
    class Barrier(completion: (Boolean, ByteArray?) -> Unit) : GattOperation(completion = completion)
}

/** Android GATT permits only one outstanding ATT operation; this queue enforces that per device. */
@SuppressLint("MissingPermission")
internal class GattOperationQueue(
    private val gattProvider: () -> BluetoothGatt?,
    private val handler: Handler,
    private val onFatalTimeout: () -> Unit
) {
    private var waitingForIndication = false
    private val controller = SerializedOperationController(
        starter = ::startOperation,
        timeoutMillis = GattOperation::timeoutMillis,
        scheduler = OperationTimeoutScheduler { delayMillis, action ->
            val runnable = Runnable { action() }
            handler.postDelayed(runnable, delayMillis)
            ScheduledCancellation { handler.removeCallbacks(runnable) }
        },
        completion = { operation, success, value ->
            waitingForIndication = false
            operation.completion(success, value)
        },
        onTimeout = { onFatalTimeout() }
    )

    fun enqueue(operation: GattOperation) {
        controller.enqueue(operation)
    }

    fun clear() {
        waitingForIndication = false
        controller.clear()
    }

    fun onMtuChanged(status: Int) {
        if (controller.active is GattOperation.RequestMtu) finish(status == BluetoothGatt.GATT_SUCCESS, null)
    }

    fun onServicesDiscovered(status: Int) {
        if (controller.active is GattOperation.Discover) finish(status == BluetoothGatt.GATT_SUCCESS, null)
    }

    fun onCharacteristicRead(characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        val op = controller.active as? GattOperation.Read ?: return
        if (op.characteristic.uuid == characteristic.uuid) finish(status == BluetoothGatt.GATT_SUCCESS, value)
    }

    fun onDescriptorWrite(descriptor: BluetoothGattDescriptor, status: Int) {
        val op = controller.active as? GattOperation.Subscribe ?: return
        if (descriptor.characteristic.uuid == op.characteristic.uuid) finish(status == BluetoothGatt.GATT_SUCCESS, null)
    }

    fun onCharacteristicWrite(characteristic: BluetoothGattCharacteristic, status: Int) {
        val op = controller.active as? GattOperation.Write ?: return
        if (characteristic.uuid != op.characteristic.uuid) return
        if (status != BluetoothGatt.GATT_SUCCESS || !op.awaitIndication) finish(status == BluetoothGatt.GATT_SUCCESS, null)
        else waitingForIndication = true
    }

    fun onCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        val op = controller.active as? GattOperation.Write ?: return false
        if (!waitingForIndication || characteristic.uuid != op.characteristic.uuid) return false
        finish(true, value)
        return true
    }

    private fun startOperation(operation: GattOperation): Boolean {
        waitingForIndication = false
        if (operation is GattOperation.Barrier) {
            controller.finishActive(true, null)
            return true
        }
        val gatt = gattProvider()
        return if (gatt == null) false else when (operation) {
            is GattOperation.RequestMtu -> gatt.requestMtu(operation.mtu)
            is GattOperation.Discover -> gatt.discoverServices()
            is GattOperation.Read -> gatt.readCharacteristic(operation.characteristic)
            is GattOperation.Subscribe -> subscribe(gatt, operation)
            is GattOperation.Write -> gatt.writeCharacteristic(
                operation.characteristic, operation.value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
            is GattOperation.Barrier -> true
        }
    }

    private fun subscribe(gatt: BluetoothGatt, operation: GattOperation.Subscribe): Boolean {
        if (!gatt.setCharacteristicNotification(operation.characteristic, true)) return false
        val descriptor = operation.characteristic.getDescriptor(StandardBleUuids.CCCD) ?: return false
        val value = if (operation.indications) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    }

    private fun finish(success: Boolean, value: ByteArray?) {
        waitingForIndication = false
        controller.finishActive(success, value)
    }
}
