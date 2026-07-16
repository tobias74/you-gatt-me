package de.tobiga.yougattme.gatt.cyclingpower

import de.tobiga.yougattme.gatt.common.littleEndianUInt16
import de.tobiga.yougattme.gatt.common.littleEndianUInt32
import kotlin.math.roundToInt

data class CyclingPowerMeasurementValues(
    val instantaneousPowerWatts: Int,
    val cumulativeWheelRevolutions: Long,
    val lastWheelEventTimeMillis: Long,
    val cumulativeCrankRevolutions: Int,
    val lastCrankEventTimeMillis: Long
)

private data class MeasurementField(
    val presentFlag: Int,
    val companionFlags: Int = 0,
    val bytes: ByteArray
)

object CyclingPowerProtocol {
    const val MEASUREMENT_UUID = "00002A63-0000-1000-8000-00805f9b34fb"
    const val VECTOR_UUID = "00002A64-0000-1000-8000-00805f9b34fb"

    fun encodeFeature(bits: Int): ByteArray = littleEndianUInt32(bits.toLong() and 0xFFFF_FFFFL)

    fun encodeMeasurement(
        values: CyclingPowerMeasurementValues,
        configuration: CyclingPowerSimulatorConfiguration,
        contentMask: Int = 0
    ): ByteArray = encodeMeasurementPackets(values, configuration, contentMask, Int.MAX_VALUE).single()

    fun encodeMeasurementPackets(
        values: CyclingPowerMeasurementValues,
        configuration: CyclingPowerSimulatorConfiguration,
        contentMask: Int,
        maxPayloadBytes: Int
    ): List<ByteArray> {
        val fields = measurementFields(values, configuration, contentMask)
        val payloadLimit = maxPayloadBytes.coerceAtLeast(20)
        val batches = mutableListOf<MutableList<MeasurementField>>()
        var current = mutableListOf<MeasurementField>()
        var currentSize = 4
        fields.forEach { field ->
            if (current.isNotEmpty() && currentSize + field.bytes.size > payloadLimit) {
                batches += current
                current = mutableListOf()
                currentSize = 4
            }
            current += field
            currentSize += field.bytes.size
        }
        if (current.isNotEmpty() || batches.isEmpty()) batches += current

        return batches.map { batch ->
            var flags = if (configuration.offsetCompensationRequired) 1 shl 12 else 0
            batch.forEach { field -> flags = flags or field.presentFlag or field.companionFlags }
            buildList<Byte> {
                addAll(littleEndianUInt16(flags).toList())
                addAll(littleEndianUInt16(values.instantaneousPowerWatts).toList())
                batch.forEach { field -> addAll(field.bytes.toList()) }
            }.toByteArray()
        }
    }

    fun encodeVectorPackets(
        values: CyclingPowerMeasurementValues,
        configuration: CyclingPowerSimulatorConfiguration,
        maxPayloadBytes: Int,
        includeFirstCrankAngle: Boolean = configuration.vectorIncludeFirstCrankAngle
    ): List<ByteArray> {
        val magnitudes = if (configuration.vectorIncludeMagnitudeArray) {
            configuration.vectorMagnitudes.toList()
        } else {
            emptyList()
        }
        val payloadLimit = maxPayloadBytes.coerceAtLeast(20)
        val packets = mutableListOf<ByteArray>()
        var index = 0
        var firstPacket = true
        do {
            val includeCrank = configuration.vectorIncludeCrankData
            val includeAngle = firstPacket && includeFirstCrankAngle
            val baseSize = 1 +
                (if (includeCrank) 4 else 0) +
                (if (includeAngle) 2 else 0)
            val valuesThatFit = ((payloadLimit - baseSize) / 2).coerceIn(0, 9)
            val remaining = magnitudes.size - index
            val take = minOf(remaining.coerceAtLeast(0), valuesThatFit)
            var flags = 0
            if (includeCrank) flags = flags or 0x01
            if (includeAngle) flags = flags or 0x02
            if (take > 0) {
                flags = flags or if (configuration.sensorMeasurementContextTorqueBased) 0x08 else 0x04
            }
            flags = flags or ((configuration.instantaneousMeasurementDirection and 0x03) shl 4)

            packets += buildList<Byte> {
                add(flags.toByte())
                if (includeCrank) {
                    addAll(littleEndianUInt16(values.cumulativeCrankRevolutions).toList())
                    addAll(littleEndianUInt16(eventTime(values.lastCrankEventTimeMillis, 1024)).toList())
                }
                if (includeAngle) {
                    addAll(littleEndianUInt16(configuration.maximumAngleDegrees.coerceIn(0, 359)).toList())
                }
                repeat(take) { offset ->
                    val magnitude = magnitudes[index + offset]
                    val raw = if (configuration.sensorMeasurementContextTorqueBased) {
                        (magnitude * 32).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    } else {
                        magnitude.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    }
                    addAll(littleEndianUInt16(raw).toList())
                }
            }.toByteArray()
            index += take
            firstPacket = false
        } while (index < magnitudes.size)
        return packets
    }

    private fun measurementFields(
        values: CyclingPowerMeasurementValues,
        configuration: CyclingPowerSimulatorConfiguration,
        contentMask: Int
    ): List<MeasurementField> = buildList {
        if (configuration.includePedalPowerBalance && contentMask and (1 shl 0) == 0) {
            add(
                MeasurementField(
                    presentFlag = 1 shl 0,
                    companionFlags = if (configuration.pedalPowerBalanceReferenceIsLeft) 1 shl 1 else 0,
                    bytes = byteArrayOf(
                        (configuration.pedalPowerBalancePercent * 2.0)
                            .roundToInt()
                            .coerceIn(0, 200)
                            .toByte()
                    )
                )
            )
        }
        if (configuration.includeAccumulatedTorque && contentMask and (1 shl 1) == 0) {
            add(
                MeasurementField(
                    presentFlag = 1 shl 2,
                    companionFlags = if (configuration.accumulatedTorqueSourceIsCrank) 1 shl 3 else 0,
                    bytes = littleEndianUInt16(
                        (configuration.accumulatedTorqueNm * 32.0).roundToInt().coerceIn(0, 0xFFFF)
                    )
                )
            )
        }
        if (configuration.includeWheelRevolutionData && contentMask and (1 shl 2) == 0) {
            add(
                MeasurementField(
                    presentFlag = 1 shl 4,
                    bytes = littleEndianUInt32(values.cumulativeWheelRevolutions and 0xFFFF_FFFFL) +
                        littleEndianUInt16(eventTime(values.lastWheelEventTimeMillis, 2048))
                )
            )
        }
        if (configuration.includeCrankRevolutionData && contentMask and (1 shl 3) == 0) {
            add(
                MeasurementField(
                    presentFlag = 1 shl 5,
                    bytes = littleEndianUInt16(values.cumulativeCrankRevolutions) +
                        littleEndianUInt16(eventTime(values.lastCrankEventTimeMillis, 1024))
                )
            )
        }
        if (configuration.includeExtremeMagnitudes && contentMask and (1 shl 4) == 0) {
            if (configuration.sensorMeasurementContextTorqueBased) {
                add(
                    MeasurementField(
                        presentFlag = 1 shl 7,
                        bytes = littleEndianUInt16((configuration.maximumTorqueMagnitudeNm * 32).roundToInt()) +
                            littleEndianUInt16((configuration.minimumTorqueMagnitudeNm * 32).roundToInt())
                    )
                )
            } else {
                add(
                    MeasurementField(
                        presentFlag = 1 shl 6,
                        bytes = littleEndianUInt16(configuration.maximumForceMagnitudeN) +
                            littleEndianUInt16(configuration.minimumForceMagnitudeN)
                    )
                )
            }
        }
        if (configuration.includeExtremeAngles && contentMask and (1 shl 5) == 0) {
            val packed = (configuration.maximumAngleDegrees.coerceIn(0, 0xFFF) and 0xFFF) or
                ((configuration.minimumAngleDegrees.coerceIn(0, 0xFFF) and 0xFFF) shl 12)
            add(
                MeasurementField(
                    presentFlag = 1 shl 8,
                    bytes = byteArrayOf(
                        (packed and 0xFF).toByte(),
                        ((packed ushr 8) and 0xFF).toByte(),
                        ((packed ushr 16) and 0xFF).toByte()
                    )
                )
            )
        }
        if (configuration.includeTopDeadSpotAngle && contentMask and (1 shl 6) == 0) {
            add(MeasurementField(1 shl 9, bytes = littleEndianUInt16(configuration.topDeadSpotAngleDegrees)))
        }
        if (configuration.includeBottomDeadSpotAngle && contentMask and (1 shl 7) == 0) {
            add(MeasurementField(1 shl 10, bytes = littleEndianUInt16(configuration.bottomDeadSpotAngleDegrees)))
        }
        if (configuration.includeAccumulatedEnergy && contentMask and (1 shl 8) == 0) {
            add(MeasurementField(1 shl 11, bytes = littleEndianUInt16(configuration.accumulatedEnergyKj)))
        }
    }

    private fun eventTime(milliseconds: Long, unitsPerSecond: Int): Int =
        ((milliseconds * unitsPerSecond / 1000L) and 0xFFFF).toInt()
}
