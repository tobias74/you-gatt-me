package de.tobiga.yougattme.gatt.fitnessmachine

import org.junit.Assert.assertEquals
import org.junit.Test

class FtmsResistanceEncodingTest {

    @Test
    fun correctedResistanceFieldsUseOneOctetAtPointOneResolution() {
        val bike = IndoorBikeData().apply { resistanceLevel = 20 }
        val rower = RowerData().apply { resistanceLevel = 5 }
        val crossTrainer = CrossTrainerData().apply { resistanceLevel = 25 }

        assertEquals(200, bike.getResistanceLevelRaw())
        assertEquals(50, rower.getResistanceLevelRaw())
        assertEquals(250, crossTrainer.getResistanceLevelRaw())
        assertEquals(255, FtmsSupportedRanges().resistanceRaw(25.5f))
    }
}
