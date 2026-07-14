package de.tobiga.yougattme.gatt.fitnessmachine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FtmsProtocolStateTest {

    @Test
    fun supportedRanges_convertUiValuesToFtmsRawValues() {
        val ranges = FtmsSupportedRanges()

        assertEquals(1234, ranges.speedRaw(12.34f))
        assertEquals(-55, ranges.inclinationRaw(-5.5f))
        assertEquals(205, ranges.resistanceRaw(20.5f))
    }

    @Test
    fun targetFeatures_copyFromMirrorsAllDeclarationBits() {
        val source = FtmsTargetFeatures(
            targetSpeed = true,
            targetInclination = true,
            targetResistanceLevel = true,
            targetPower = true,
            targetHeartRate = true,
            targetedExpendedEnergy = true,
            targetedSteps = true,
            targetedStrides = true,
            targetedDistance = true,
            targetedTrainingTime = true,
            targetedTimeInTwoHeartRateZones = true,
            targetedTimeInThreeHeartRateZones = true,
            targetedTimeInFiveHeartRateZones = true,
            indoorBikeSimulationParameters = true,
            wheelCircumference = true,
            spinDownControl = true,
            targetedCadence = true
        )
        val target = FtmsTargetFeatures()

        target.copyFrom(source)

        assertEquals(source, target)
    }

    @Test
    fun targetStateSummary_includesNewControlTargets() {
        val state = FtmsTargetState(
            heartRateZoneTargets = FtmsHeartRateZoneTargets(3, listOf(60, 120, 180)),
            indoorBikeSimulation = IndoorCyclingSimulationParameters(1.2f, 3.5, 0.004f, 0.5f),
            wheelCircumferenceMm = 2100.0f,
            spinDown = FtmsSpinDownTarget(
                status = FTMS_SPIN_DOWN_STATUS_REQUESTED,
                targetSpeedLowKmh = 20.0f,
                targetSpeedHighKmh = 25.0f
            )
        )

        val summary = state.summary()

        assertTrue(summary.contains("HR zones 3 60s/120s/180s"))
        assertTrue(summary.contains("bike sim grade 3.5%"))
        assertTrue(summary.contains("wheel 2100.0 mm"))
        assertTrue(summary.contains("spin-down requested 20.0-25.0 km/h"))
    }
}
