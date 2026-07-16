package de.tobiga.yougattme.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class CyclingMeasurementSimulatorTest {
    @Test
    fun `wheel cumulative value saturates instead of rolling over`() {
        val simulator = CyclingMeasurementSimulator()
        simulator.simulateWheel(20.0, durationMillis = 500, currentTimeMillis = 1_000)
        simulator.setCumulativeWheelRevolutions(0xFFFF_FFFFL)
        simulator.simulateWheel(20.0, durationMillis = 1_000, currentTimeMillis = 2_000)

        assertEquals(0xFFFF_FFFFL, simulator.getCumulativeWheelRevolutions())
    }

    @Test
    fun `crank cumulative value rolls over at uint16 boundary`() {
        val simulator = CyclingMeasurementSimulator()
        simulator.simulateCrank(120.0, durationMillis = 500, currentTimeMillis = 1_000)
        repeat(65_536) {
            simulator.simulateCrank(60_000.0, durationMillis = 1, currentTimeMillis = 1_001L + it)
        }

        assertEquals(0, simulator.getCumulativeCrankRevolutions())
    }
}
