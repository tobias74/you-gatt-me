package de.tobiga.yougattme.bridge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Handler
import de.tobiga.yougattme.bridge.StandardBleUuids
import de.tobiga.yougattme.bridge.GattWritePreference
import de.tobiga.yougattme.bridge.TransactionCompletion
import java.util.UUID

internal sealed class GattOperation(
    val timeoutMillis: Long = 10_000L,
    val fatalOnTimeout: Boolean = true,
    val completion: (Boolean, ByteArray?) -> Unit = { _, _ -> }
) {
    class RequestMtu(val mtu: Int, completion: (Boolean, ByteArray?) -> Unit = { _, _ -> }) :
        GattOperation(completion = completion)
    class Discover(completion: (Boolean, ByteArray?) -> Unit) : GattOperation(completion = completion)
    class Read(
        val characteristic: BluetoothGattCharacteristic,
        timeoutMillis: Long = 10_000L,
        fatalOnTimeout: Boolean = true,
        completion: (Boolean, ByteArray?) -> Unit
    ) : GattOperation(timeoutMillis, fatalOnTimeout, completion)
    class Subscribe(val characteristic: BluetoothGattCharacteristic, val indications: Boolean,
        completion: (Boolean, ByteArray?) -> Unit = { _, _ -> }) : GattOperation(completion = completion)
    class Write(
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray,
        val completionMode: TransactionCompletion,
        val writePreference: GattWritePreference = GattWritePreference.WITH_RESPONSE,
        val responseCharacteristicUuid: UUID? = null,
        val responseMatcher: (ByteArray) -> Boolean = { true },
        timeoutMillis: Long,
        fatalOnTimeout: Boolean = true,
        completion: (Boolean, ByteArray?) -> Unit
    ) : GattOperation(timeoutMillis, fatalOnTimeout, completion)
    class Barrier(completion: (Boolean, ByteArray?) -> Unit) : GattOperation(completion = completion)
    class Delay(val delayMillis: Long) : GattOperation(timeoutMillis = delayMillis + 1_000L)
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
        continueAfterTimeout = { operation -> !operation.fatalOnTimeout },
        onTimeout = { operation -> if (operation.fatalOnTimeout) onFatalTimeout() }
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
        if (status != BluetoothGatt.GATT_SUCCESS) finish(false, null)
        else if (op.completionMode == TransactionCompletion.MATCHING_NOTIFICATION) waitingForIndication = true
        else finish(true, null)
    }

    fun onCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        val op = controller.active as? GattOperation.Write ?: return false
        val expected = op.responseCharacteristicUuid ?: op.characteristic.uuid
        if (!waitingForIndication || characteristic.uuid != expected || !op.responseMatcher(value)) return false
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
            is GattOperation.Write -> write(gatt, operation)
            is GattOperation.Barrier -> true
            is GattOperation.Delay -> {
                handler.postDelayed({ controller.finishActive(true, null) }, operation.delayMillis)
                true
            }
        }
    }

    private fun write(gatt: BluetoothGatt, operation: GattOperation.Write): Boolean {
        val characteristic = operation.characteristic
        val supportsNoResponse = characteristic.properties and
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val supportsResponse = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val writeType = when (operation.writePreference) {
            GattWritePreference.WITH_RESPONSE -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            GattWritePreference.WITHOUT_RESPONSE -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            GattWritePreference.WITHOUT_RESPONSE_FALLBACK_WITH_RESPONSE -> if (supportsNoResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE && !supportsNoResponse) return false
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT && !supportsResponse) return false
        val started = gatt.writeCharacteristic(characteristic, operation.value, writeType) == BluetoothStatusCodes.SUCCESS
        if (started && operation.completionMode == TransactionCompletion.MATCHING_NOTIFICATION &&
            writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            waitingForIndication = true
        }
        if (started && operation.completionMode == TransactionCompletion.FIRE_AND_FORGET) {
            controller.finishActive(true, null)
        }
        return started
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
