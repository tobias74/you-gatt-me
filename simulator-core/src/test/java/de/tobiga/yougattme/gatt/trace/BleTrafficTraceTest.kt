package de.tobiga.yougattme.gatt.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BleTrafficTraceTest {
    @Test
    fun `json line contains stable raw fields and escaped metadata`() {
        val line = BleTrafficEvent(
            sequence = 7,
            wallTimeMillis = 100,
            monotonicNanos = 200,
            role = BleTrafficRole.CENTRAL,
            direction = BleTrafficDirection.IN,
            event = "NOTIFICATION",
            deviceAddress = "AA:BB",
            deviceName = "Trainer \"One\"",
            serviceUuid = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb"),
            characteristicUuid = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb"),
            value = byteArrayOf(0, 0x7f, 0xff.toByte()),
            status = 0,
            details = mapOf("source" to "callback\nline")
        ).toJsonLine()

        assertTrue(line.contains("\"sequence\":7"))
        assertTrue(line.contains("\"value_hex\":\"007FFF\""))
        assertTrue(line.contains("\"value_length\":3"))
        assertTrue(line.contains("Trainer \\\"One\\\""))
        assertTrue(line.contains("callback\\nline"))
        assertFalse(line.contains("\n"))
    }

    @Test
    fun `sink receives an immutable copy of packet bytes`() {
        var captured: BleTrafficEvent? = null
        BleTrafficTrace.installSink { captured = it }
        val value = byteArrayOf(1, 2, 3)

        BleTrafficTrace.record(
            BleTrafficRole.CENTRAL,
            BleTrafficDirection.OUT,
            "WRITE",
            value = value
        )
        value[0] = 9
        BleTrafficTrace.installSink(null)

        assertEquals(1, captured!!.value!![0].toInt())
    }
}
