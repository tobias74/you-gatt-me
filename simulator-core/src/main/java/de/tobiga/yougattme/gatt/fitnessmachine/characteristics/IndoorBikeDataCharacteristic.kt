package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.fitnessmachine.IndoorBikeData
import java.util.UUID

private object IndoorBikeDataFlags {
    const val AVERAGE_SPEED_PRESENT = 1
    const val INSTANTANEOUS_CADENCE_PRESENT = 2
    const val AVERAGE_CADENCE_PRESENT = 3
    const val TOTAL_DISTANCE_PRESENT = 4
    const val RESISTANCE_LEVEL_PRESENT = 5
    const val INSTANTANEOUS_POWER_PRESENT = 6
    const val AVERAGE_POWER_PRESENT = 7
    const val EXPENDED_ENERGY_PRESENT = 8
    const val HEART_RATE_PRESENT = 9
    const val METABOLIC_EQUIVALENT_PRESENT = 10
    const val ELAPSED_TIME_PRESENT = 11
    const val REMAINING_TIME_PRESENT = 12
}

class IndoorBikeDataCharacteristic(
    gattProfile: AbstractGattProfile,
    private val indoorBikeData: IndoorBikeData
) : AbstractFitnessMachineDataCharacteristic(gattProfile) {

    private val GATT_INDOOR_BIKE_DATA_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID {
        return GATT_INDOOR_BIKE_DATA_CHARACTERISTIC_UUID
    }

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_INDOOR_BIKE_DATA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )

        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getFtmsDataRecord(): FtmsDataRecord {
        val optional = buildList {
            if (indoorBikeData.includeAverageSpeed) add(ftmsField(IndoorBikeDataFlags.AVERAGE_SPEED_PRESENT) { u16(indoorBikeData.getAverageSpeedHundredthsKmh()) })
            if (indoorBikeData.includeInstantaneousCadence) add(ftmsField(IndoorBikeDataFlags.INSTANTANEOUS_CADENCE_PRESENT) { u16(indoorBikeData.getInstantaneousCadenceHalfRpm()) })
            if (indoorBikeData.includeAverageCadence) add(ftmsField(IndoorBikeDataFlags.AVERAGE_CADENCE_PRESENT) { u16(indoorBikeData.getAverageCadenceHalfRpm()) })
            if (indoorBikeData.includeTotalDistance) add(ftmsField(IndoorBikeDataFlags.TOTAL_DISTANCE_PRESENT) { u24(indoorBikeData.getTotalDistanceInt()) })
            if (indoorBikeData.includeResistanceLevel) add(ftmsField(IndoorBikeDataFlags.RESISTANCE_LEVEL_PRESENT) { u8(indoorBikeData.getResistanceLevelRaw()) })
            if (indoorBikeData.includeInstantaneousPower) add(ftmsField(IndoorBikeDataFlags.INSTANTANEOUS_POWER_PRESENT) { s16(indoorBikeData.getInstantaneousPowerRaw()) })
            if (indoorBikeData.includeAveragePower) add(ftmsField(IndoorBikeDataFlags.AVERAGE_POWER_PRESENT) { s16(indoorBikeData.getAveragePowerRaw()) })
            if (indoorBikeData.includeExpendedEnergy) add(ftmsField(IndoorBikeDataFlags.EXPENDED_ENERGY_PRESENT) {
                u16(indoorBikeData.getTotalEnergyKcal())
                u16(indoorBikeData.getEnergyPerHourKcal())
                u8(indoorBikeData.getEnergyPerMinuteKcal())
            })
            if (indoorBikeData.includeHeartRate) add(ftmsField(IndoorBikeDataFlags.HEART_RATE_PRESENT) { u8(indoorBikeData.getHeartRateBpm()) })
            if (indoorBikeData.includeMetabolicEquivalent) add(ftmsField(IndoorBikeDataFlags.METABOLIC_EQUIVALENT_PRESENT) { u8(indoorBikeData.getMetabolicEquivalentTenths()) })
            if (indoorBikeData.includeElapsedTime) add(ftmsField(IndoorBikeDataFlags.ELAPSED_TIME_PRESENT) { u16(indoorBikeData.getElapsedTimeInt()) })
            if (indoorBikeData.includeRemainingTime) add(ftmsField(IndoorBikeDataFlags.REMAINING_TIME_PRESENT) { u16(indoorBikeData.getRemainingTimeInt()) })
        }
        return FtmsDataRecord(
            flagsWidth = 2,
            mandatoryFields = ftmsMandatory { u16(indoorBikeData.getInstantaneousSpeedHundredthsKmh()) },
            optionalFields = optional
        )
    }
}
