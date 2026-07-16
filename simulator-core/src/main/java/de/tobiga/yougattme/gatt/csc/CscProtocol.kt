package de.tobiga.yougattme.gatt.csc

import de.tobiga.yougattme.gatt.common.littleEndianUInt16
import de.tobiga.yougattme.gatt.common.littleEndianUInt32

data class CscMeasurementValues(
    val cumulativeWheelRevolutions: Long,
    val lastWheelEventTimeMillis: Long,
    val cumulativeCrankRevolutions: Int,
    val lastCrankEventTimeMillis: Long
)

object CscProtocol {
    fun encodeFeature(bits: Int): ByteArray = littleEndianUInt16(bits and CscFeatureBits.ALL)

    fun encodeMeasurement(
        values: CscMeasurementValues,
        configuration: CscSimulatorConfiguration
    ): ByteArray {
        var flags = 0
        if (configuration.includeWheelRevolutionData) flags = flags or 0x01
        if (configuration.includeCrankRevolutionData) flags = flags or 0x02
        return buildList<Byte> {
            add(flags.toByte())
            if (configuration.includeWheelRevolutionData) {
                addAll(littleEndianUInt32(values.cumulativeWheelRevolutions and 0xFFFF_FFFFL).toList())
                addAll(littleEndianUInt16(eventTime(values.lastWheelEventTimeMillis, 1024)).toList())
            }
            if (configuration.includeCrankRevolutionData) {
                addAll(littleEndianUInt16(values.cumulativeCrankRevolutions and 0xFFFF).toList())
                addAll(littleEndianUInt16(eventTime(values.lastCrankEventTimeMillis, 1024)).toList())
            }
        }.toByteArray()
    }

    private fun eventTime(milliseconds: Long, unitsPerSecond: Int): Int =
        ((milliseconds * unitsPerSecond / 1000L) and 0xFFFF).toInt()
}
