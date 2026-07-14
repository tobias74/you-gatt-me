package de.tobiga.yougattme.tools

import kotlin.math.floor

class CrankRevolutionSimulator {

    private var cumulativeCrankRevolutions: Double = 0.0
    private var leftOverMillis: Double = 0.0
    private var lastCrankEventTimestamp: Long = 0L

    // Methods
    fun simulate(cadence: Int) {
        if (lastCrankEventTimestamp > 0) {

            if (cadence == 0) {
                lastCrankEventTimestamp = System.currentTimeMillis()
                return
            }


            val availableMillisToTravel = leftOverMillis + (System.currentTimeMillis() - lastCrankEventTimestamp)

            val crankRevolutionsPerSecond = cadence / 60.0

            val crankRevolutionsSinceLastEvent = crankRevolutionsPerSecond * (availableMillisToTravel/1000)
            val fullCrankRevolutionsSinceLastEvent = (floor(crankRevolutionsSinceLastEvent)).toInt()

            val timeNeededForFullRevolutions = (fullCrankRevolutionsSinceLastEvent / crankRevolutionsPerSecond)

            val excessRevolution = crankRevolutionsSinceLastEvent - fullCrankRevolutionsSinceLastEvent

            leftOverMillis = (excessRevolution / crankRevolutionsPerSecond) * 1000

            cumulativeCrankRevolutions += fullCrankRevolutionsSinceLastEvent

            // Check if the value has exceeded the maximum allowed by the characteristic (0xFFFFFFFF)
            if (cumulativeCrankRevolutions > 4294967295) {
                cumulativeCrankRevolutions -= 4294967296
            }

            // Update the last wheel event time



            lastCrankEventTimestamp += (timeNeededForFullRevolutions * 1000).toLong()

        } else {
            lastCrankEventTimestamp = System.currentTimeMillis()
        }

    }

    fun getCumulativeCrankRevolutions(): Int {
        return cumulativeCrankRevolutions.toInt()
    }

    fun getLastCrankEventTimestamp(): Long {
        return lastCrankEventTimestamp
    }

}


