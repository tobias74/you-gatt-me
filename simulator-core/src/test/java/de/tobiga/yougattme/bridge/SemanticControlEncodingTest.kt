package de.tobiga.yougattme.bridge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticControlEncodingTest {
    private fun ftmsEndpoint(ranges: Map<java.util.UUID, ByteArray> = emptyMap()) = SourceEndpoint(
        id = "endpoint",
        deviceAddress = "00:00",
        deviceName = "device",
        service = StandardService.FTMS,
        measurementCharacteristic = StandardBleUuids.FTMS_INDOOR_BIKE_DATA,
        supportedMetrics = emptySet(),
        controlPointCharacteristic = StandardBleUuids.FTMS_CONTROL_POINT,
        protocolId = StandardService.FTMS.name,
        serviceUuid = StandardService.FTMS.serviceUuid,
        capabilityValues = ranges
    )

    @Test
    fun resistancePercentageMapsLinearlyThroughAdvertisedRange() {
        // -10.0 through +30.0 in 0.1-level raw units.
        val range = byteArrayOf(0x9C.toByte(), 0xFF.toByte(), 0x2C, 0x01, 0x01, 0x00)
        val transaction = StandardInputAdapter.encodeControlTransactions(
            ControlCommand.BasicResistance(25.0),
            ftmsEndpoint(mapOf(StandardBleUuids.FTMS_SUPPORTED_RESISTANCE_RANGE to range))
        ).single()

        // 25% of the range is exactly 0.0 resistance level => raw 0.
        assertArrayEquals(byteArrayOf(0x04, 0x00, 0x00), transaction.value)
        assertTrue(StandardInputAdapter.encodeControlTransactions(
            ControlCommand.BasicResistance(25.0), ftmsEndpoint()
        ).isEmpty())
    }

    @Test
    fun simulationFoldsDraftingIntoWindCoefficient() {
        val transaction = StandardInputAdapter.encodeControlTransactions(
            ControlCommand.SimulationParameters(
                windSpeedMetersPerSecond = -2.0,
                gradePercent = 5.25,
                rollingResistanceCoefficient = 0.004,
                windResistanceCoefficient = 0.50,
                draftingFactor = 0.80
            ),
            ftmsEndpoint()
        ).single()
        assertArrayEquals(
            byteArrayOf(0x11, 0x30, 0xF8.toByte(), 0x0D, 0x02, 40, 40),
            transaction.value
        )
    }

    @Test
    fun unsupportedCrossProtocolControlsAreRejectedWithoutApproximation() {
        assertTrue(StandardInputAdapter.encodeControlTransactions(
            ControlCommand.ProtocolExtension("extension", byteArrayOf(1, 2, 3)), ftmsEndpoint()
        ).isEmpty())
        assertTrue(StandardInputAdapter.encodeControlTransactions(
            ControlCommand.Calibration(spinDown = false, zeroOffset = true), ftmsEndpoint()
        ).isEmpty())
    }

    @Test
    fun wheelCircumferenceUsesFtmsPointOneMillimeterResolution() {
        val value = StandardInputAdapter.encodeControlTransactions(
            ControlCommand.WheelConfiguration(2.105), ftmsEndpoint()
        ).single().value
        assertEquals(0x12, value[0].toInt())
        assertEquals(21050, (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8))
    }

    @Test
    fun targetCommandsUseFtmsOpcodesAndResolutions() {
        val endpoint = ftmsEndpoint(mapOf(
            StandardBleUuids.FTMS_SUPPORTED_SPEED_RANGE to byteArrayOf(0, 0, 0x10, 0x27, 1, 0),
            StandardBleUuids.FTMS_SUPPORTED_INCLINATION_RANGE to byteArrayOf(0x38, 0xFF.toByte(), 0xC8.toByte(), 0, 1, 0),
            StandardBleUuids.FTMS_SUPPORTED_HEART_RATE_RANGE to byteArrayOf(40, 200.toByte(), 1)
        ))
        fun encoded(command: ControlCommand) = StandardInputAdapter
            .encodeControlTransactions(command, endpoint).single().value

        assertArrayEquals(byteArrayOf(0x02, 0x84.toByte(), 0x03), encoded(ControlCommand.TargetSpeed(2.5)))
        assertArrayEquals(byteArrayOf(0x03, 0xC7.toByte(), 0x00), encoded(ControlCommand.TargetInclination(19.9)))
        assertArrayEquals(byteArrayOf(0x06, 175.toByte()), encoded(ControlCommand.TargetHeartRate(175)))
        assertArrayEquals(byteArrayOf(0x09, 0x2C, 0x01), encoded(ControlCommand.TargetEnergy(300)))
        assertArrayEquals(byteArrayOf(0x0C, 0x40, 0x0D, 0x03), encoded(ControlCommand.TargetDistance(200_000.0)))
        assertArrayEquals(byteArrayOf(0x0D, 0x10, 0x0E), encoded(ControlCommand.TargetTrainingTime(3_600)))
    }

    @Test
    fun targetCommandsRejectOutOfRangeWithoutClamping() {
        val endpoint = ftmsEndpoint(mapOf(
            StandardBleUuids.FTMS_SUPPORTED_SPEED_RANGE to byteArrayOf(0, 0, 0xE8.toByte(), 0x03, 1, 0),
            StandardBleUuids.FTMS_SUPPORTED_HEART_RATE_RANGE to byteArrayOf(40, 180.toByte(), 1)
        ))
        assertTrue(StandardInputAdapter.encodeControlTransactions(ControlCommand.TargetSpeed(3.0), endpoint).isEmpty())
        assertTrue(StandardInputAdapter.encodeControlTransactions(ControlCommand.TargetHeartRate(181), endpoint).isEmpty())
        assertTrue(StandardInputAdapter.encodeControlTransactions(ControlCommand.TargetDistance(-1.0), endpoint).isEmpty())
    }

    @Test
    fun capabilityAwareControlSetUsesFtmsTargetFeatureBits() {
        val feature = ByteArray(8)
        // Speed, heart-rate, energy, distance and training-time target setting support.
        val bits = (1L shl 0) or (1L shl 4) or (1L shl 5) or (1L shl 8) or (1L shl 9)
        for (index in 0 until 4) feature[4 + index] = (bits ushr (index * 8)).toByte()
        val controls = StandardInputAdapter.controlCapabilities(
            StandardService.FTMS.serviceUuid,
            mapOf(StandardBleUuids.FTMS_FEATURE to feature)
        )
        assertTrue(ControlCommand.Kind.TARGET_SPEED in controls)
        assertTrue(ControlCommand.Kind.TARGET_HEART_RATE in controls)
        assertTrue(ControlCommand.Kind.TARGET_ENERGY in controls)
        assertTrue(ControlCommand.Kind.TARGET_DISTANCE in controls)
        assertTrue(ControlCommand.Kind.TARGET_TRAINING_TIME in controls)
        assertTrue(ControlCommand.Kind.TARGET_POWER !in controls)

        val endpoint = ftmsEndpoint(mapOf(StandardBleUuids.FTMS_FEATURE to feature))
        assertTrue(StandardInputAdapter.encodeControlTransactions(
            ControlCommand.TargetPower(200.0), endpoint
        ).isEmpty())
        assertTrue(StandardInputAdapter.encodeControlTransactions(
            ControlCommand.TargetSpeed(2.0), endpoint
        ).isNotEmpty())
    }
}
