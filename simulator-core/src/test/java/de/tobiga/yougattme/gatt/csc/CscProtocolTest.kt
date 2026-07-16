package de.tobiga.yougattme.gatt.csc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CscProtocolTest {
    @Test
    fun `wheel and crank pairs use 1024 hertz event time and normative order`() {
        val encoded = CscProtocol.encodeMeasurement(
            CscMeasurementValues(
                cumulativeWheelRevolutions = 0x01020304,
                lastWheelEventTimeMillis = 1_000,
                cumulativeCrankRevolutions = 0x1122,
                lastCrankEventTimeMillis = 2_000
            ),
            CscSimulatorConfiguration()
        )

        assertArrayEquals(
            bytes("03 04 03 02 01 00 04 22 11 00 08"),
            encoded
        )
    }

    @Test
    fun `nonstandard empty measurement is representable for collector robustness tests`() {
        val configuration = CscSimulatorConfiguration().apply {
            includeWheelRevolutionData = false
            includeCrankRevolutionData = false
        }
        val encoded = CscProtocol.encodeMeasurement(
            CscMeasurementValues(0, 0, 0, 0),
            configuration
        )

        assertArrayEquals(byteArrayOf(0), encoded)
        assertEquals(0, configuration.actualFeatureBits() and 0x03)
    }

    private fun bytes(hex: String): ByteArray =
        hex.split(' ').map { token -> token.toInt(16).toByte() }.toByteArray()
}
