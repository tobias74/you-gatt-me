package de.tobiga.yougattme.bridge

/** Derives normalized speed/cadence from rolling standard revolution/event-time fields. */
class DerivedMetricsEngine(private val wheelCircumferenceMeters: Double = 2.10) {
    private data class CounterState(val revolutions: Long, val eventTicks: Int)
    private val wheel = mutableMapOf<String, CounterState>()
    private val crank = mutableMapOf<String, CounterState>()

    fun addDerived(endpointId: String, decoded: DecodedMeasurement): DecodedMeasurement {
        val metrics = decoded.metrics.toMutableMap()
        val wheelUnits = if (decoded.service == StandardService.CYCLING_POWER) 2048.0 else 1024.0
        val wheelRevolutions = metrics[Metric.WHEEL_REVOLUTIONS]?.toLong()
        val wheelTicks = metrics[Metric.WHEEL_EVENT_TIME_TICKS]?.toInt()
        if (wheelRevolutions != null && wheelTicks != null) {
            val previous = wheel.put(endpointId, CounterState(wheelRevolutions, wheelTicks))
            if (previous != null) {
                val revolutionDelta = unsignedDelta(wheelRevolutions, previous.revolutions, 32)
                val tickDelta = unsignedDelta(wheelTicks.toLong(), previous.eventTicks.toLong(), 16)
                if (tickDelta > 0 && revolutionDelta <= 100_000) {
                    metrics[Metric.SPEED_MPS] = revolutionDelta * wheelCircumferenceMeters * wheelUnits / tickDelta
                }
            }
        }
        val crankRevolutions = metrics[Metric.CRANK_REVOLUTIONS]?.toLong()
        val crankTicks = metrics[Metric.CRANK_EVENT_TIME_TICKS]?.toInt()
        if (crankRevolutions != null && crankTicks != null) {
            val previous = crank.put(endpointId, CounterState(crankRevolutions, crankTicks))
            if (previous != null) {
                val revolutionDelta = unsignedDelta(crankRevolutions, previous.revolutions, 16)
                val tickDelta = unsignedDelta(crankTicks.toLong(), previous.eventTicks.toLong(), 16)
                if (tickDelta > 0 && revolutionDelta <= 10_000) {
                    metrics[Metric.CADENCE_RPM] = revolutionDelta * 60.0 * 1024.0 / tickDelta
                }
            }
        }
        return decoded.copy(metrics = metrics)
    }

    fun clear(endpointId: String) {
        wheel.remove(endpointId)
        crank.remove(endpointId)
    }

    private fun unsignedDelta(current: Long, previous: Long, bits: Int): Long {
        val modulus = 1L shl bits
        return (current - previous + modulus) % modulus
    }
}
