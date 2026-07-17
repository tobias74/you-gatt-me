package de.tobiga.yougattme.bridge

import org.junit.Assert.*
import org.junit.Test

class StandardMeasurementDecoderTest {
    @Test fun `decodes indoor bike signed resistance and optional fields`() {
        val packet = bytes(0x64, 0x02, 0xC4, 0x09, 0xA0, 0x00, 0xCE, 0xFF, 0xFA, 0x00, 120)
        val decoded = StandardMeasurementDecoder.decode(StandardBleUuids.FTMS_INDOOR_BIKE_DATA, packet, 1000)!!
        assertEquals(25.0 / 3.6, decoded.metrics[Metric.SPEED_MPS]!!, 0.0001)
        assertEquals(80.0, decoded.metrics[Metric.CADENCE_RPM]!!, 0.0)
        assertEquals(-5.0, decoded.metrics[Metric.RESISTANCE_LEVEL]!!, 0.0)
        assertEquals(250.0, decoded.metrics[Metric.POWER_WATTS]!!, 0.0)
        assertEquals(120.0, decoded.metrics[Metric.HEART_RATE_BPM]!!, 0.0)
    }

    @Test fun `decodes preliminary FTMS packet without mandatory speed`() {
        val decoded = StandardMeasurementDecoder.decode(
            StandardBleUuids.FTMS_INDOOR_BIKE_DATA,
            bytes(0x41, 0x00, 0x2C, 0x01), 1000
        )!!
        assertTrue(decoded.moreData)
        assertFalse(decoded.metrics.containsKey(Metric.SPEED_MPS))
        assertEquals(300.0, decoded.metrics[Metric.POWER_WATTS]!!, 0.0)
    }

    @Test fun `decodes cycling power complete measurement`() {
        val packet = bytes(
            0x31, 0x08, 0xEC, 0xFF, 100,
            100, 0, 0, 0, 0, 8,
            10, 0, 0, 4,
            12, 0
        )
        val decoded = StandardMeasurementDecoder.decode(StandardBleUuids.CPS_MEASUREMENT, packet, 1000)!!
        assertEquals(-20.0, decoded.metrics[Metric.POWER_WATTS]!!, 0.0)
        assertEquals(50.0, decoded.metrics[Metric.PEDAL_POWER_BALANCE_PERCENT]!!, 0.0)
        assertEquals(100.0, decoded.metrics[Metric.WHEEL_REVOLUTIONS]!!, 0.0)
        assertEquals(10.0, decoded.metrics[Metric.CRANK_REVOLUTIONS]!!, 0.0)
        assertEquals(12.0, decoded.metrics[Metric.ACCUMULATED_ENERGY_KJ]!!, 0.0)
    }

    @Test fun `decodes RSC and heart rate arrays`() {
        val rsc = StandardMeasurementDecoder.decode(
            StandardBleUuids.RSC_MEASUREMENT,
            bytes(0x07, 0x00, 0x02, 170, 120, 0, 123, 0, 0, 0), 1000
        )!!
        assertEquals(2.0, rsc.metrics[Metric.SPEED_MPS]!!, 0.0)
        assertEquals(1.2, rsc.metrics[Metric.STRIDE_LENGTH_METERS]!!, 0.0)
        assertEquals(12.3, rsc.metrics[Metric.DISTANCE_METERS]!!, 0.0)

        val heart = StandardMeasurementDecoder.decode(
            StandardBleUuids.HEART_RATE_MEASUREMENT,
            bytes(0x1E, 75, 10, 0, 0, 4, 0, 2), 1000
        )!!
        assertEquals(75.0, heart.metrics[Metric.HEART_RATE_BPM]!!, 0.0)
        assertEquals(0.5, heart.metrics[Metric.RR_INTERVAL_SECONDS]!!, 0.0)
        assertEquals(listOf(1.0, 0.5), heart.protocolFields["rrIntervalsSeconds"])
    }

    @Test fun `rejects truncated trailing and RFU-invalid packets`() {
        assertNull(StandardMeasurementDecoder.decode(StandardBleUuids.FTMS_INDOOR_BIKE_DATA, bytes(0, 0, 1), 0))
        assertNull(StandardMeasurementDecoder.decode(StandardBleUuids.RSC_MEASUREMENT, bytes(0, 0, 0, 0, 99), 0))
        val rfu = StandardMeasurementDecoder.decode(StandardBleUuids.CSC_MEASUREMENT, bytes(0x80), 0)!!
        assertEquals(0x80, rfu.protocolFields["rfuFlags"])
    }

    @Test fun `FTMS data-not-available sentinels do not become samples`() {
        val decoded = StandardMeasurementDecoder.decode(
            StandardBleUuids.FTMS_INDOOR_BIKE_DATA,
            bytes(0x44, 0x06, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x7F, 0xFF, 0xFF),
            1000
        )!!
        assertFalse(decoded.metrics.containsKey(Metric.SPEED_MPS))
        assertFalse(decoded.metrics.containsKey(Metric.CADENCE_RPM))
        assertFalse(decoded.metrics.containsKey(Metric.POWER_WATTS))
        assertFalse(decoded.metrics.containsKey(Metric.HEART_RATE_BPM))
        assertFalse(decoded.metrics.containsKey(Metric.METABOLIC_EQUIVALENT))
    }

    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
}
