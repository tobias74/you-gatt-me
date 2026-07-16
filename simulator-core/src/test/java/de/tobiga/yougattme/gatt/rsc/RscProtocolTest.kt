package de.tobiga.yougattme.gatt.rsc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RscProtocolTest {
    @Test
    fun `complete measurement follows GSS field order and resolutions`() {
        val configuration = RscSimulatorConfiguration().apply {
            includeStrideLength = true
            includeTotalDistance = true
            includeWalkingOrRunningStatus = true
            runningStatus = true
        }

        val encoded = RscProtocol.encodeMeasurement(
            RscMeasurementValues(
                instantaneousSpeedMetersPerSecond = 2.5,
                instantaneousCadencePerMinute = 80,
                instantaneousStrideLengthMeters = 1.1,
                totalDistanceMeters = 123.4
            ),
            configuration
        )

        assertArrayEquals(
            bytes("07 80 02 50 6E 00 D2 04 00 00"),
            encoded
        )
    }

    @Test
    fun `declared features can intentionally differ from actual behavior`() {
        val configuration = RscSimulatorConfiguration().apply {
            mirrorActualIntoFeature = false
            declaredFeatureBits = RscFeatureBits.CALIBRATION
            includeStrideLength = true
            includeTotalDistance = true
            includeWalkingOrRunningStatus = false
            supportedProcedures.clear()
        }

        assertEquals(RscFeatureBits.CALIBRATION, configuration.featureBitsForGatt())
        assertEquals(
            RscFeatureBits.STRIDE_LENGTH or RscFeatureBits.TOTAL_DISTANCE,
            configuration.actualFeatureBits()
        )
    }

    @Test
    fun `total distance retains the complete uint32 range`() {
        val configuration = RscSimulatorConfiguration().apply {
            includeStrideLength = false
            includeTotalDistance = true
            includeWalkingOrRunningStatus = false
        }
        val encoded = RscProtocol.encodeMeasurement(
            RscMeasurementValues(0.0, 0, 0.0, 429_496_729.5),
            configuration
        )

        assertArrayEquals(bytes("02 00 00 00 FF FF FF FF"), encoded)
    }

    private fun bytes(hex: String): ByteArray =
        hex.split(' ').map { token -> token.toInt(16).toByte() }.toByteArray()
}
