package de.tobiga.yougattme.gatt.rsc

import de.tobiga.yougattme.gatt.common.littleEndianUInt16
import de.tobiga.yougattme.gatt.common.littleEndianUInt32
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class RscMeasurementValues(
    val instantaneousSpeedMetersPerSecond: Double,
    val instantaneousCadencePerMinute: Int,
    val instantaneousStrideLengthMeters: Double,
    val totalDistanceMeters: Double
)

object RscProtocol {
    fun encodeFeature(bits: Int): ByteArray = littleEndianUInt16(bits and RscFeatureBits.ALL)

    fun encodeMeasurement(
        values: RscMeasurementValues,
        configuration: RscSimulatorConfiguration
    ): ByteArray {
        var flags = 0
        if (configuration.includeStrideLength) flags = flags or 0x01
        if (configuration.includeTotalDistance) flags = flags or 0x02
        if (configuration.includeWalkingOrRunningStatus && configuration.runningStatus) {
            flags = flags or 0x04
        }

        return buildList<Byte> {
            add(flags.toByte())
            addAll(
                littleEndianUInt16(
                    (values.instantaneousSpeedMetersPerSecond * 256.0)
                        .roundToInt()
                        .coerceIn(0, 0xFFFF)
                ).toList()
            )
            add(values.instantaneousCadencePerMinute.coerceIn(0, 0xFF).toByte())
            if (configuration.includeStrideLength) {
                addAll(
                    littleEndianUInt16(
                        (values.instantaneousStrideLengthMeters * 100.0)
                            .roundToInt()
                            .coerceIn(0, 0xFFFF)
                    ).toList()
                )
            }
            if (configuration.includeTotalDistance) {
                addAll(
                    littleEndianUInt32(
                        (values.totalDistanceMeters * 10.0)
                            .roundToLong()
                            .coerceIn(0, 0xFFFF_FFFFL)
                    ).toList()
                )
            }
        }.toByteArray()
    }
}
