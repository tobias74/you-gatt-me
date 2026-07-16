package de.tobiga.yougattme.gatt.cyclingpower

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CyclingPowerProtocolTest {
    private val values = CyclingPowerMeasurementValues(
        instantaneousPowerWatts = 250,
        cumulativeWheelRevolutions = 0x01020304,
        lastWheelEventTimeMillis = 1_000,
        cumulativeCrankRevolutions = 0x1122,
        lastCrankEventTimeMillis = 2_000
    )

    @Test
    fun `complete measurement is split only at field boundaries for default ATT MTU`() {
        val packets = CyclingPowerProtocol.encodeMeasurementPackets(
            values,
            CyclingPowerSimulatorConfiguration(),
            contentMask = 0,
            maxPayloadBytes = 20
        )

        assertEquals(2, packets.size)
        assertEquals(17, packets[0].size)
        assertEquals(17, packets[1].size)
        assertArrayEquals(bytes("3F 00"), packets[0].copyOfRange(0, 2))
        assertArrayEquals(bytes("40 0F"), packets[1].copyOfRange(0, 2))
        packets.forEach { packet ->
            assertArrayEquals(bytes("FA 00"), packet.copyOfRange(2, 4))
        }
    }

    @Test
    fun `measurement content mask removes the requested optional fields and their flags`() {
        val packets = CyclingPowerProtocol.encodeMeasurementPackets(
            values,
            CyclingPowerSimulatorConfiguration(),
            contentMask = (1 shl 0) or (1 shl 2),
            maxPayloadBytes = 512
        )

        assertEquals(1, packets.size)
        val flags = uint16(packets.single(), 0)
        assertFalse(flags and (1 shl 0) != 0)
        assertFalse(flags and (1 shl 1) != 0)
        assertFalse(flags and (1 shl 4) != 0)
        assertTrue(flags and (1 shl 2) != 0)
        assertTrue(flags and (1 shl 5) != 0)
    }

    @Test
    fun `vector continuation omits first angle and respects six-value default-MTU limit`() {
        val packets = CyclingPowerProtocol.encodeVectorPackets(
            values,
            CyclingPowerSimulatorConfiguration(),
            maxPayloadBytes = 20
        )

        assertEquals(2, packets.size)
        assertEquals(0x17, packets[0][0].toInt() and 0xFF)
        assertEquals(19, packets[0].size)
        assertEquals(0x15, packets[1][0].toInt() and 0xFF)
        assertEquals(17, packets[1].size)
    }

    @Test
    fun `actual feature word reflects every default supported procedure and force context`() {
        val configuration = CyclingPowerSimulatorConfiguration()
        assertEquals(0x001EFFFF, configuration.actualFeatureBits())
        assertArrayEquals(
            bytes("FF FF 1E 00"),
            CyclingPowerProtocol.encodeFeature(configuration.actualFeatureBits())
        )
    }

    @Test
    fun `extreme angles are packed as minimum high twelve bits and maximum low twelve bits`() {
        val configuration = CyclingPowerSimulatorConfiguration().apply {
            includePedalPowerBalance = false
            includeAccumulatedTorque = false
            includeWheelRevolutionData = false
            includeCrankRevolutionData = false
            includeExtremeMagnitudes = false
            includeExtremeAngles = true
            includeTopDeadSpotAngle = false
            includeBottomDeadSpotAngle = false
            includeAccumulatedEnergy = false
            maximumAngleDegrees = 0xABC
            minimumAngleDegrees = 0x123
        }
        val packet = CyclingPowerProtocol.encodeMeasurementPackets(
            values,
            configuration,
            contentMask = 0,
            maxPayloadBytes = 20
        ).single()

        assertArrayEquals(bytes("BC 3A 12"), packet.copyOfRange(4, 7))
    }

    private fun uint16(value: ByteArray, offset: Int): Int =
        (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)

    private fun bytes(hex: String): ByteArray =
        hex.split(' ').map { token -> token.toInt(16).toByte() }.toByteArray()
}
