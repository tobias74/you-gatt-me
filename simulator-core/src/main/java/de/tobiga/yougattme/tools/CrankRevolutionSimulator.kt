package de.tobiga.yougattme.tools

import kotlin.math.floor

class CrankRevolutionSimulator {

    private var cumulativeCrankRevolutions: Double = 0.0
    private var crankPosition: Double = 0.0
    private var lastCrankEventTimestamp: Long = 0L
    private var lastSimulationTimestamp: Long = 0L

    // Methods
    fun simulate(cadence: Int) {
        val now = System.currentTimeMillis()
        if (lastSimulationTimestamp == 0L) {
            lastSimulationTimestamp = now
            lastCrankEventTimestamp = now
            return
        }
        val durationMillis = now - lastSimulationTimestamp
        lastSimulationTimestamp = now
        if (cadence <= 0) return

        val revolutionsPerSecond = cadence / 60.0
        val totalPosition = crankPosition + revolutionsPerSecond * durationMillis / 1000.0
        val fullRevolutions = floor(totalPosition).toInt()
        crankPosition = totalPosition - fullRevolutions
        cumulativeCrankRevolutions = (cumulativeCrankRevolutions + fullRevolutions) % 65536.0
        if (fullRevolutions > 0) {
            lastCrankEventTimestamp =
                now - (crankPosition / revolutionsPerSecond * 1000.0).toLong()
        }

    }

    fun getCumulativeCrankRevolutions(): Int {
        return cumulativeCrankRevolutions.toInt()
    }

    fun getLastCrankEventTimestamp(): Long {
        return lastCrankEventTimestamp
    }

}


