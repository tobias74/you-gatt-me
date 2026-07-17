package de.tobiga.yougattme.bridge

import org.junit.Assert.*
import org.junit.Test

class RoutingEngineTest {
    private val engine = RoutingEngine()

    @Test fun `field override wins over default and manual remains local`() {
        val route = ServiceRoute(
            "ftms", StandardService.FTMS, RouteSource.Endpoint("trainer"),
            mapOf(Metric.HEART_RATE_BPM to RouteSource.Endpoint("strap"), Metric.RESISTANCE_LEVEL to RouteSource.Manual)
        )
        val samples = mapOf(
            "trainer" to mapOf(Metric.POWER_WATTS to sample(Metric.POWER_WATTS, 200.0, "trainer", 10_000)),
            "strap" to mapOf(Metric.HEART_RATE_BPM to sample(Metric.HEART_RATE_BPM, 140.0, "strap", 10_000))
        )
        val resolved = engine.resolve(route,
            setOf(Metric.POWER_WATTS, Metric.HEART_RATE_BPM, Metric.RESISTANCE_LEVEL), samples, 11_000)
        assertEquals(RouteFreshness.FRESH, resolved.freshness)
        assertEquals("trainer", resolved.values[Metric.POWER_WATTS]?.endpointId)
        assertEquals("strap", resolved.values[Metric.HEART_RATE_BPM]?.endpointId)
        assertTrue(Metric.RESISTANCE_LEVEL in resolved.manualMetrics)
    }

    @Test fun `missing and stale inputs pause only dependent route`() {
        val route = ServiceRoute("csc", StandardService.CSC, RouteSource.Endpoint("bike"))
        val stale = sample(Metric.SPEED_MPS, 5.0, "bike", 1_000, observed = 1_000)
        val resolved = engine.resolve(route, setOf(Metric.SPEED_MPS, Metric.CADENCE_RPM),
            mapOf("bike" to mapOf(Metric.SPEED_MPS to stale)), 7_000)
        assertEquals(RouteFreshness.INCOMPLETE, resolved.freshness)
        assertTrue(Metric.CADENCE_RPM in resolved.missingMetrics)
        assertTrue(Metric.SPEED_MPS in resolved.staleMetrics)
    }

    private fun sample(metric: Metric, value: Double, endpoint: String, time: Long, observed: Long? = null) =
        MetricSample(metric, value, endpoint, time, observedIntervalMillis = observed)
}
