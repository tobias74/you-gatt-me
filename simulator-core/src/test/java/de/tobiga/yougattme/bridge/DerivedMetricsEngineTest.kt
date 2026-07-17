package de.tobiga.yougattme.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class DerivedMetricsEngineTest {
    @Test fun `derives speed and cadence across counter rollover`() {
        val engine = DerivedMetricsEngine(2.0)
        val first = measurement(mapOf(
            Metric.WHEEL_REVOLUTIONS to 0xFFFF_FFFEL.toDouble(), Metric.WHEEL_EVENT_TIME_TICKS to 65000.0,
            Metric.CRANK_REVOLUTIONS to 65535.0, Metric.CRANK_EVENT_TIME_TICKS to 65000.0
        ))
        engine.addDerived("sensor", first)
        val second = engine.addDerived("sensor", measurement(mapOf(
            Metric.WHEEL_REVOLUTIONS to 1.0, Metric.WHEEL_EVENT_TIME_TICKS to 488.0,
            Metric.CRANK_REVOLUTIONS to 1.0, Metric.CRANK_EVENT_TIME_TICKS to 488.0
        )))
        assertEquals(6.0, second.metrics[Metric.SPEED_MPS]!!, 0.001)
        assertEquals(120.0, second.metrics[Metric.CADENCE_RPM]!!, 0.001)
    }

    private fun measurement(metrics: Map<Metric, Double>) = DecodedMeasurement(
        StandardService.CSC, StandardBleUuids.CSC_MEASUREMENT, 3, metrics, emptyMap(), 0
    )
}
