package de.tobiga.yougattme.gatt.trace

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattServer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

enum class BleTrafficRole {
    SCANNER,
    CENTRAL,
    ADVERTISER,
    PERIPHERAL
}

enum class BleTrafficDirection {
    IN,
    OUT,
    EVENT
}

/**
 * Protocol-neutral raw BLE event. Vendor packets remain opaque byte arrays.
 */
data class BleTrafficEvent(
    val sequence: Long,
    val wallTimeMillis: Long,
    val monotonicNanos: Long,
    val role: BleTrafficRole,
    val direction: BleTrafficDirection,
    val event: String,
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val serviceUuid: UUID? = null,
    val characteristicUuid: UUID? = null,
    val descriptorUuid: UUID? = null,
    val value: ByteArray? = null,
    val status: Int? = null,
    val details: Map<String, String> = emptyMap()
) {
    fun toJsonLine(): String = buildString {
        append('{')
        field("sequence", sequence.toString(), quoted = false)
        field("wall_time_ms", wallTimeMillis.toString(), quoted = false)
        field("monotonic_ns", monotonicNanos.toString(), quoted = false)
        field("role", role.name)
        field("direction", direction.name)
        field("event", event)
        deviceAddress?.let { field("device_address", it) }
        deviceName?.let { field("device_name", it) }
        serviceUuid?.let { field("service_uuid", it.toString()) }
        characteristicUuid?.let { field("characteristic_uuid", it.toString()) }
        descriptorUuid?.let { field("descriptor_uuid", it.toString()) }
        status?.let { field("status", it.toString(), quoted = false) }
        value?.let {
            field("value_hex", it.toHex(), quoted = true)
            field("value_length", it.size.toString(), quoted = false)
        }
        if (details.isNotEmpty()) {
            commaIfNeeded()
            append("\"details\":{")
            details.toSortedMap().entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(',')
                append('"').append(key.jsonEscape()).append("\":\"")
                    .append(value.jsonEscape()).append('"')
            }
            append('}')
        }
        append('}')
    }

    private fun StringBuilder.field(name: String, value: String, quoted: Boolean = true) {
        commaIfNeeded()
        append('"').append(name).append("\":")
        if (quoted) append('"').append(value.jsonEscape()).append('"') else append(value)
    }

    private fun StringBuilder.commaIfNeeded() {
        if (length > 1 && last() != '{') append(',')
    }
}

object BleTrafficTrace {
    private val sequence = AtomicLong(0)

    @Volatile
    private var sink: ((BleTrafficEvent) -> Unit)? = null

    fun installSink(value: ((BleTrafficEvent) -> Unit)?) {
        sink = value
    }

    fun record(
        role: BleTrafficRole,
        direction: BleTrafficDirection,
        event: String,
        deviceAddress: String? = null,
        deviceName: String? = null,
        serviceUuid: UUID? = null,
        characteristicUuid: UUID? = null,
        descriptorUuid: UUID? = null,
        value: ByteArray? = null,
        status: Int? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        val target = sink ?: return
        val trace = BleTrafficEvent(
            sequence = sequence.incrementAndGet(),
            wallTimeMillis = System.currentTimeMillis(),
            monotonicNanos = System.nanoTime(),
            role = role,
            direction = direction,
            event = event,
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            serviceUuid = serviceUuid,
            characteristicUuid = characteristicUuid,
            descriptorUuid = descriptorUuid,
            value = value?.copyOf(),
            status = status,
            details = details.mapValues { (_, item) -> item?.toString() ?: "null" }
        )
        runCatching { target(trace) }
    }
}

/**
 * Records an ATT server response before handing it to Android. The request ID
 * correlates it with the preceding request event in the capture.
 */
@SuppressLint("MissingPermission")
fun BluetoothGattServer.sendTracedResponse(
    device: BluetoothDevice,
    requestId: Int,
    status: Int,
    offset: Int,
    value: ByteArray?
): Boolean {
    BleTrafficTrace.record(
        role = BleTrafficRole.PERIPHERAL,
        direction = BleTrafficDirection.OUT,
        event = "ATT_RESPONSE",
        deviceAddress = device.address,
        value = value,
        status = status,
        details = mapOf("request_id" to requestId, "offset" to offset)
    )
    return sendResponse(device, requestId, status, offset, value)
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { String.format(Locale.US, "%02X", it.toInt() and 0xff) }

private fun String.jsonEscape(): String = buildString(length + 8) {
    this@jsonEscape.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u").append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
}
