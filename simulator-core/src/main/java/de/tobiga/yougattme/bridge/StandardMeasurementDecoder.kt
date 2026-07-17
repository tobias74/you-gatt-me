package de.tobiga.yougattme.bridge

import java.util.UUID

/** Strict, side-effect-free decoders for the standard services accepted by the bridge. */
object StandardMeasurementDecoder {
    fun decode(uuid: UUID, value: ByteArray, timestampMillis: Long): DecodedMeasurement? =
        try {
            when (uuid) {
                StandardBleUuids.FTMS_TREADMILL_DATA -> decodeTreadmill(value, uuid, timestampMillis)
                StandardBleUuids.FTMS_CROSS_TRAINER_DATA -> decodeCrossTrainer(value, uuid, timestampMillis)
                StandardBleUuids.FTMS_STEP_CLIMBER_DATA -> decodeStepClimber(value, uuid, timestampMillis)
                StandardBleUuids.FTMS_STAIR_CLIMBER_DATA -> decodeStairClimber(value, uuid, timestampMillis)
                StandardBleUuids.FTMS_ROWER_DATA -> decodeRower(value, uuid, timestampMillis)
                StandardBleUuids.FTMS_INDOOR_BIKE_DATA -> decodeIndoorBike(value, uuid, timestampMillis)
                StandardBleUuids.CPS_MEASUREMENT -> decodeCyclingPower(value, uuid, timestampMillis)
                StandardBleUuids.CPS_VECTOR -> decodeCyclingPowerVector(value, uuid, timestampMillis)
                StandardBleUuids.CSC_MEASUREMENT -> decodeCsc(value, uuid, timestampMillis)
                StandardBleUuids.RSC_MEASUREMENT -> decodeRsc(value, uuid, timestampMillis)
                StandardBleUuids.HEART_RATE_MEASUREMENT -> decodeHeartRate(value, uuid, timestampMillis)
                else -> null
            }
        } catch (_: MalformedPacketException) {
            null
        }

    private fun decodeIndoorBike(value: ByteArray, uuid: UUID, now: Long): DecodedMeasurement {
        val r = PacketReader(value)
        val flags = r.u16()
        val m = linkedMapOf<Metric, Double>()
        val p = linkedMapOf<String, Any>()
        if (!flags.bit(0)) putU16(r, m, Metric.SPEED_MPS, 360.0)
        if (flags.bit(1)) putU16(r, m, Metric.AVERAGE_SPEED_MPS, 360.0)
        if (flags.bit(2)) putU16(r, m, Metric.CADENCE_RPM, 2.0)
        if (flags.bit(3)) putU16(r, m, Metric.AVERAGE_CADENCE_RPM, 2.0)
        if (flags.bit(4)) putU24(r, m, Metric.DISTANCE_METERS)
        if (flags.bit(5)) putS16(r, m, Metric.RESISTANCE_LEVEL, 10.0)
        if (flags.bit(6)) putS16(r, m, Metric.POWER_WATTS)
        if (flags.bit(7)) putS16(r, m, Metric.AVERAGE_POWER_WATTS)
        if (flags.bit(8)) energy(r, m)
        if (flags.bit(9)) putU8(r, m, Metric.HEART_RATE_BPM)
        if (flags.bit(10)) putU8(r, m, Metric.METABOLIC_EQUIVALENT, 10.0)
        if (flags.bit(11)) putU16(r, m, Metric.ELAPSED_TIME_SECONDS)
        if (flags.bit(12)) putU16(r, m, Metric.REMAINING_TIME_SECONDS)
        r.requireConsumed()
        return ftms(uuid, flags, m, p, now)
    }

    private fun decodeTreadmill(value: ByteArray, uuid: UUID, now: Long): DecodedMeasurement {
        val r = PacketReader(value)
        val flags = r.u16()
        val m = linkedMapOf<Metric, Double>()
        val p = linkedMapOf<String, Any>()
        if (!flags.bit(0)) putU16(r, m, Metric.SPEED_MPS, 360.0)
        if (flags.bit(1)) putU16(r, m, Metric.AVERAGE_SPEED_MPS, 360.0)
        if (flags.bit(2)) putU24(r, m, Metric.DISTANCE_METERS)
        if (flags.bit(3)) {
            putS16(r, m, Metric.INCLINATION_PERCENT, 10.0)
            putS16(r, m, Metric.RAMP_ANGLE_DEGREES, 10.0)
        }
        if (flags.bit(4)) {
            putU16(r, m, Metric.POSITIVE_ELEVATION_METERS, 10.0)
            putU16(r, m, Metric.NEGATIVE_ELEVATION_METERS, 10.0)
        }
        if (flags.bit(5)) putU16(r, m, Metric.PACE_SECONDS)
        if (flags.bit(6)) putU16(r, m, Metric.AVERAGE_PACE_SECONDS)
        if (flags.bit(7)) energy(r, m)
        if (flags.bit(8)) putU8(r, m, Metric.HEART_RATE_BPM)
        if (flags.bit(9)) putU8(r, m, Metric.METABOLIC_EQUIVALENT, 10.0)
        if (flags.bit(10)) putU16(r, m, Metric.ELAPSED_TIME_SECONDS)
        if (flags.bit(11)) putU16(r, m, Metric.REMAINING_TIME_SECONDS)
        if (flags.bit(12)) {
            putS16(r, m, Metric.FORCE_NEWTONS)
            putS16(r, m, Metric.POWER_WATTS)
        }
        r.requireConsumed()
        return ftms(uuid, flags, m, p, now)
    }

    private fun decodeCrossTrainer(value: ByteArray, uuid: UUID, now: Long): DecodedMeasurement {
        val r = PacketReader(value)
        val flags = r.u24()
        val m = linkedMapOf<Metric, Double>()
        val p = linkedMapOf<String, Any>()
        if (!flags.bit(0)) putU16(r, m, Metric.SPEED_MPS, 360.0)
        if (flags.bit(1)) putU16(r, m, Metric.AVERAGE_SPEED_MPS, 360.0)
        if (flags.bit(2)) putU24(r, m, Metric.DISTANCE_METERS)
        if (flags.bit(3)) {
            putU16(r, m, Metric.STEP_RATE_PER_MINUTE)
            putU16(r, m, Metric.AVERAGE_STEP_RATE_PER_MINUTE)
        }
        if (flags.bit(4)) putU16(r, m, Metric.STRIDE_COUNT, 10.0)
        if (flags.bit(5)) {
            putU16(r, m, Metric.POSITIVE_ELEVATION_METERS)
            putU16(r, m, Metric.NEGATIVE_ELEVATION_METERS)
        }
        if (flags.bit(6)) {
            putS16(r, m, Metric.INCLINATION_PERCENT, 10.0)
            putS16(r, m, Metric.RAMP_ANGLE_DEGREES, 10.0)
        }
        if (flags.bit(7)) putS16(r, m, Metric.RESISTANCE_LEVEL, 10.0)
        if (flags.bit(8)) putS16(r, m, Metric.POWER_WATTS)
        if (flags.bit(9)) putS16(r, m, Metric.AVERAGE_POWER_WATTS)
        if (flags.bit(10)) energy(r, m)
        if (flags.bit(11)) putU8(r, m, Metric.HEART_RATE_BPM)
        if (flags.bit(12)) putU8(r, m, Metric.METABOLIC_EQUIVALENT, 10.0)
        if (flags.bit(13)) putU16(r, m, Metric.ELAPSED_TIME_SECONDS)
        if (flags.bit(14)) putU16(r, m, Metric.REMAINING_TIME_SECONDS)
        m[Metric.MOVEMENT_DIRECTION] = if (flags.bit(15)) -1.0 else 1.0
        r.requireConsumed()
        return ftms(uuid, flags, m, p, now)
    }

    private fun decodeStepClimber(value: ByteArray, uuid: UUID, now: Long): DecodedMeasurement {
        val r = PacketReader(value)
        val flags = r.u16()
        val m = linkedMapOf<Metric, Double>()
        val p = linkedMapOf<String, Any>()
        if (!flags.bit(0)) {
            putU16(r, m, Metric.FLOORS)
            putU16(r, m, Metric.STEP_COUNT)
        }
        if (flags.bit(1)) putU16(r, m, Metric.STEP_RATE_PER_MINUTE)
        if (flags.bit(2)) putU16(r, m, Metric.AVERAGE_STEP_RATE_PER_MINUTE)
        if (flags.bit(3)) putU16(r, m, Metric.POSITIVE_ELEVATION_METERS)
        if (flags.bit(4)) energy(r, m)
        if (flags.bit(5)) putU8(r, m, Metric.HEART_RATE_BPM)
        if (flags.bit(6)) putU8(r, m, Metric.METABOLIC_EQUIVALENT, 10.0)
        if (flags.bit(7)) putU16(r, m, Metric.ELAPSED_TIME_SECONDS)
        if (flags.bit(8)) putU16(r, m, Metric.REMAINING_TIME_SECONDS)
        r.requireConsumed()
        return ftms(uuid, flags, m, p, now)
    }

    private fun decodeStairClimber(value: ByteArray, uuid: UUID, now: Long): DecodedMeasurement {
        val r = PacketReader(value)
        val flags = r.u16()
        val m = linkedMapOf<Metric, Double>()
        val p = linkedMapOf<String, Any>()
        if (!flags.bit(0)) putU16(r, m, Metric.FLOORS)
        if (flags.bit(1)) putU16(r, m, Metric.STEP_RATE_PER_MINUTE)
        if (flags.bit(2)) putU16(r, m, Metric.AVERAGE_STEP_RATE_PER_MINUTE)
        if (flags.bit(3)) putU16(r, m, Metric.POSITIVE_ELEVATION_METERS)
        if (flags.bit(4)) putU16(r, m, Metric.STRIDE_COUNT)
        if (flags.bit(5)) energy(r, m)
        if (flags.bit(6)) putU8(r, m, Metric.HEART_RATE_BPM)
        if (flags.bit(7)) putU8(r, m, Metric.METABOLIC_EQUIVALENT, 10.0)
        if (flags.bit(8)) putU16(r, m, Metric.ELAPSED_TIME_SECONDS)
        if (flags.bit(9)) putU16(r, m, Metric.REMAINING_TIME_SECONDS)
        r.requireConsumed()
        return ftms(uuid, flags, m, p, now)
    }

    private fun decodeRower(value: ByteArray, uuid: UUID, now: Long): DecodedMeasurement {
        val r = PacketReader(value)
        val flags = r.u16()
        val m = linkedMapOf<Metric, Double>()
        val p = linkedMapOf<String, Any>()
        if (!flags.bit(0)) {
            putU8(r, m, Metric.STROKE_RATE_PER_MINUTE, 2.0)
            putU16(r, m, Metric.STROKE_COUNT)
        }
        if (flags.bit(1)) putU8(r, m, Metric.AVERAGE_STROKE_RATE_PER_MINUTE, 2.0)
        if (flags.bit(2)) putU24(r, m, Metric.DISTANCE_METERS)
        if (flags.bit(3)) putU16(r, m, Metric.PACE_SECONDS)
        if (flags.bit(4)) putU16(r, m, Metric.AVERAGE_PACE_SECONDS)
        if (flags.bit(5)) putS16(r, m, Metric.POWER_WATTS)
        if (flags.bit(6)) putS16(r, m, Metric.AVERAGE_POWER_WATTS)
        if (flags.bit(7)) putS16(r, m, Metric.RESISTANCE_LEVEL, 10.0)
        if (flags.bit(8)) energy(r, m)
        if (flags.bit(9)) putU8(r, m, Metric.HEART_RATE_BPM)
        if (flags.bit(10)) putU8(r, m, Metric.METABOLIC_EQUIVALENT, 10.0)
        if (flags.bit(11)) putU16(r, m, Metric.ELAPSED_TIME_SECONDS)
        if (flags.bit(12)) putU16(r, m, Metric.REMAINING_TIME_SECONDS)
        r.requireConsumed()
        return ftms(uuid, flags, m, p, now)
    }

    private fun decodeCyclingPower(value: ByteArray, uuid: UUID, now: Long): DecodedMeasurement {
        val r = PacketReader(value)
        val flags = r.u16()
        val m = linkedMapOf<Metric, Double>()
        val p = linkedMapOf<String, Any>()
        m[Metric.POWER_WATTS] = r.s16().toDouble()
        if (flags.bit(0)) m[Metric.PEDAL_POWER_BALANCE_PERCENT] = r.u8() / 2.0
        p["pedalPowerBalanceReference"] = if (flags.bit(1)) "left" else "unknown"
        if (flags.bit(2)) m[Metric.ACCUMULATED_TORQUE_NM] = r.u16() / 32.0
        p["accumulatedTorqueSource"] = if (flags.bit(3)) "crank" else "wheel"
        if (flags.bit(4)) {
            m[Metric.WHEEL_REVOLUTIONS] = r.u32().toDouble()
            m[Metric.WHEEL_EVENT_TIME_TICKS] = r.u16().toDouble()
        }
        if (flags.bit(5)) {
            m[Metric.CRANK_REVOLUTIONS] = r.u16().toDouble()
            m[Metric.CRANK_EVENT_TIME_TICKS] = r.u16().toDouble()
        }
        if (flags.bit(6)) {
            p["maximumForceMagnitudeN"] = r.s16()
            p["minimumForceMagnitudeN"] = r.s16()
        }
        if (flags.bit(7)) {
            p["maximumTorqueMagnitudeNm"] = r.s16() / 32.0
            p["minimumTorqueMagnitudeNm"] = r.s16() / 32.0
        }
        if (flags.bit(8)) {
            val packed = r.u24()
            p["maximumAngleDegrees"] = packed and 0xFFF
            p["minimumAngleDegrees"] = (packed ushr 12) and 0xFFF
        }
        if (flags.bit(9)) p["topDeadSpotAngleDegrees"] = r.u16()
        if (flags.bit(10)) p["bottomDeadSpotAngleDegrees"] = r.u16()
        if (flags.bit(11)) m[Metric.ACCUMULATED_ENERGY_KJ] = r.u16().toDouble()
        p["offsetCompensationIndicator"] = flags.bit(12)
        p["rfuFlags"] = flags and 0xE000
        r.requireConsumed()
        return DecodedMeasurement(StandardService.CYCLING_POWER, uuid, flags.toLong(), m, p, now)
    }

    private fun decodeCyclingPowerVector(value: ByteArray, uuid: UUID, now: Long): DecodedMeasurement {
        val r = PacketReader(value)
        val flags = r.u8()
        val m = linkedMapOf<Metric, Double>()
        val p = linkedMapOf<String, Any>()
        if (flags.bit(0)) {
            m[Metric.CRANK_REVOLUTIONS] = r.u16().toDouble()
            m[Metric.CRANK_EVENT_TIME_TICKS] = r.u16().toDouble()
        }
        if (flags.bit(1)) p["firstCrankMeasurementAngleDegrees"] = r.u16()
        val torqueArray = flags.bit(3)
        val forceArray = flags.bit(2)
        if (torqueArray && forceArray) throw MalformedPacketException()
        val magnitudes = mutableListOf<Double>()
        while (r.remaining > 0) {
            magnitudes += if (torqueArray) r.s16() / 32.0 else r.s16().toDouble()
        }
        if (forceArray) p["instantaneousForceMagnitudesN"] = magnitudes
        if (torqueArray) p["instantaneousTorqueMagnitudesNm"] = magnitudes
        p["instantaneousMeasurementDirection"] = (flags ushr 4) and 0x03
        p["rfuFlags"] = flags and 0xC0
        return DecodedMeasurement(StandardService.CYCLING_POWER, uuid, flags.toLong(), m, p, now)
    }

    private fun decodeCsc(value: ByteArray, uuid: UUID, now: Long): DecodedMeasurement {
        val r = PacketReader(value)
        val flags = r.u8()
        val m = linkedMapOf<Metric, Double>()
        if (flags.bit(0)) {
            m[Metric.WHEEL_REVOLUTIONS] = r.u32().toDouble()
            m[Metric.WHEEL_EVENT_TIME_TICKS] = r.u16().toDouble()
        }
        if (flags.bit(1)) {
            m[Metric.CRANK_REVOLUTIONS] = r.u16().toDouble()
            m[Metric.CRANK_EVENT_TIME_TICKS] = r.u16().toDouble()
        }
        r.requireConsumed()
        return DecodedMeasurement(StandardService.CSC, uuid, flags.toLong(), m,
            mapOf("rfuFlags" to (flags and 0xFC)), now)
    }

    private fun decodeRsc(value: ByteArray, uuid: UUID, now: Long): DecodedMeasurement {
        val r = PacketReader(value)
        val flags = r.u8()
        val m = linkedMapOf<Metric, Double>()
        m[Metric.SPEED_MPS] = r.u16() / 256.0
        m[Metric.CADENCE_RPM] = r.u8().toDouble()
        if (flags.bit(0)) m[Metric.STRIDE_LENGTH_METERS] = r.u16() / 100.0
        if (flags.bit(1)) m[Metric.DISTANCE_METERS] = r.u32() / 10.0
        m[Metric.WALKING_RUNNING_STATUS] = if (flags.bit(2)) 1.0 else 0.0
        r.requireConsumed()
        return DecodedMeasurement(StandardService.RSC, uuid, flags.toLong(), m,
            mapOf("rfuFlags" to (flags and 0xF8)), now)
    }

    private fun decodeHeartRate(value: ByteArray, uuid: UUID, now: Long): DecodedMeasurement {
        val r = PacketReader(value)
        val flags = r.u8()
        val m = linkedMapOf<Metric, Double>()
        val p = linkedMapOf<String, Any>()
        m[Metric.HEART_RATE_BPM] = if (flags.bit(0)) r.u16().toDouble() else r.u8().toDouble()
        p["sensorContactSupported"] = flags.bit(2)
        p["sensorContactDetected"] = flags.bit(2) && flags.bit(1)
        p["rfuFlags"] = flags and 0xE0
        if (flags.bit(3)) m[Metric.HEART_RATE_ENERGY_KJ] = r.u16().toDouble()
        if (flags.bit(4)) {
            val rr = mutableListOf<Double>()
            while (r.remaining > 0) rr += r.u16() / 1024.0
            p["rrIntervalsSeconds"] = rr
            if (rr.isNotEmpty()) m[Metric.RR_INTERVAL_SECONDS] = rr.last()
        }
        r.requireConsumed()
        return DecodedMeasurement(StandardService.HEART_RATE, uuid, flags.toLong(), m, p, now)
    }

    private fun ftms(
        uuid: UUID,
        flags: Int,
        metrics: Map<Metric, Double>,
        fields: Map<String, Any>,
        now: Long
    ): DecodedMeasurement {
        val knownMask = when (uuid) {
            StandardBleUuids.FTMS_CROSS_TRAINER_DATA -> 0xFFFF
            StandardBleUuids.FTMS_STEP_CLIMBER_DATA -> 0x01FF
            StandardBleUuids.FTMS_STAIR_CLIMBER_DATA -> 0x03FF
            else -> 0x1FFF
        }
        return DecodedMeasurement(
            StandardService.FTMS, uuid, flags.toLong(), metrics,
            fields + ("rfuFlags" to (flags and knownMask.inv())), now, flags.bit(0)
        )
    }

    private fun energy(r: PacketReader, m: MutableMap<Metric, Double>) {
        putU16(r, m, Metric.ENERGY_KCAL)
        putU16(r, m, Metric.ENERGY_PER_HOUR_KCAL)
        putU8(r, m, Metric.ENERGY_PER_MINUTE_KCAL)
    }

    private fun putU8(r: PacketReader, m: MutableMap<Metric, Double>, metric: Metric, divisor: Double = 1.0) {
        val raw = r.u8()
        if (raw != 0xFF) m[metric] = raw / divisor
    }

    private fun putU16(r: PacketReader, m: MutableMap<Metric, Double>, metric: Metric, divisor: Double = 1.0) {
        val raw = r.u16()
        if (raw != 0xFFFF) m[metric] = raw / divisor
    }

    private fun putS16(r: PacketReader, m: MutableMap<Metric, Double>, metric: Metric, divisor: Double = 1.0) {
        val raw = r.s16()
        if (raw != 0x7FFF) m[metric] = raw / divisor
    }

    private fun putU24(r: PacketReader, m: MutableMap<Metric, Double>, metric: Metric) {
        val raw = r.u24()
        if (raw != 0xFFFFFF) m[metric] = raw.toDouble()
    }
}

class StandardInputAdapterProvider : InputAdapterProvider {
    override val providerId: String = "bluetooth-sig-standard"
    override fun createAdapters(): List<InputAdapter> = listOf(StandardInputAdapter)
}

object StandardInputAdapter : InputAdapter {
    override val providerId: String = "bluetooth-sig-standard"
    override val supportedServices: Set<UUID> = StandardService.entries.map { it.serviceUuid }.toSet()
    override val measurementCharacteristics: Set<UUID> = StandardBleUuids.measurementCharacteristics
    override val statusCharacteristics: Set<UUID> = StandardBleUuids.statusCharacteristics
    override val controlPointCharacteristics: Set<UUID> = StandardBleUuids.controlPointCharacteristics
    override val readableCapabilityCharacteristics: Set<UUID> = StandardBleUuids.readableCapabilityCharacteristics
    override fun serviceName(serviceUuid: UUID): String =
        StandardService.fromUuid(serviceUuid)?.shortName ?: "Bluetooth SIG service"
    override fun supportedMetrics(characteristicUuid: UUID): Set<Metric> = when (characteristicUuid) {
        StandardBleUuids.FTMS_INDOOR_BIKE_DATA -> setOf(Metric.SPEED_MPS, Metric.AVERAGE_SPEED_MPS,
            Metric.CADENCE_RPM, Metric.AVERAGE_CADENCE_RPM, Metric.DISTANCE_METERS, Metric.RESISTANCE_LEVEL,
            Metric.POWER_WATTS, Metric.AVERAGE_POWER_WATTS, Metric.ENERGY_KCAL, Metric.ENERGY_PER_HOUR_KCAL,
            Metric.ENERGY_PER_MINUTE_KCAL, Metric.HEART_RATE_BPM, Metric.METABOLIC_EQUIVALENT,
            Metric.ELAPSED_TIME_SECONDS, Metric.REMAINING_TIME_SECONDS)
        StandardBleUuids.FTMS_TREADMILL_DATA -> setOf(Metric.SPEED_MPS, Metric.AVERAGE_SPEED_MPS,
            Metric.DISTANCE_METERS, Metric.INCLINATION_PERCENT, Metric.RAMP_ANGLE_DEGREES,
            Metric.POSITIVE_ELEVATION_METERS, Metric.NEGATIVE_ELEVATION_METERS, Metric.PACE_SECONDS,
            Metric.AVERAGE_PACE_SECONDS, Metric.ENERGY_KCAL, Metric.ENERGY_PER_HOUR_KCAL,
            Metric.ENERGY_PER_MINUTE_KCAL, Metric.POWER_WATTS, Metric.FORCE_NEWTONS, Metric.HEART_RATE_BPM,
            Metric.METABOLIC_EQUIVALENT, Metric.ELAPSED_TIME_SECONDS, Metric.REMAINING_TIME_SECONDS)
        StandardBleUuids.FTMS_CROSS_TRAINER_DATA -> setOf(Metric.SPEED_MPS, Metric.AVERAGE_SPEED_MPS,
            Metric.STEP_RATE_PER_MINUTE, Metric.AVERAGE_STEP_RATE_PER_MINUTE, Metric.STRIDE_COUNT,
            Metric.DISTANCE_METERS, Metric.POSITIVE_ELEVATION_METERS, Metric.NEGATIVE_ELEVATION_METERS,
            Metric.INCLINATION_PERCENT, Metric.RAMP_ANGLE_DEGREES, Metric.RESISTANCE_LEVEL, Metric.POWER_WATTS,
            Metric.AVERAGE_POWER_WATTS, Metric.ENERGY_KCAL, Metric.ENERGY_PER_HOUR_KCAL,
            Metric.ENERGY_PER_MINUTE_KCAL, Metric.HEART_RATE_BPM, Metric.METABOLIC_EQUIVALENT,
            Metric.ELAPSED_TIME_SECONDS, Metric.REMAINING_TIME_SECONDS, Metric.MOVEMENT_DIRECTION)
        StandardBleUuids.FTMS_ROWER_DATA -> setOf(Metric.STROKE_RATE_PER_MINUTE, Metric.STROKE_COUNT,
            Metric.AVERAGE_STROKE_RATE_PER_MINUTE, Metric.DISTANCE_METERS, Metric.PACE_SECONDS,
            Metric.AVERAGE_PACE_SECONDS, Metric.POWER_WATTS, Metric.AVERAGE_POWER_WATTS,
            Metric.RESISTANCE_LEVEL, Metric.ENERGY_KCAL, Metric.ENERGY_PER_HOUR_KCAL,
            Metric.ENERGY_PER_MINUTE_KCAL, Metric.HEART_RATE_BPM, Metric.METABOLIC_EQUIVALENT,
            Metric.ELAPSED_TIME_SECONDS, Metric.REMAINING_TIME_SECONDS)
        StandardBleUuids.FTMS_STEP_CLIMBER_DATA, StandardBleUuids.FTMS_STAIR_CLIMBER_DATA ->
            setOf(Metric.FLOORS, Metric.STEP_RATE_PER_MINUTE, Metric.AVERAGE_STEP_RATE_PER_MINUTE,
                Metric.STEP_COUNT, Metric.STRIDE_COUNT, Metric.POSITIVE_ELEVATION_METERS,
                Metric.ENERGY_KCAL, Metric.ENERGY_PER_HOUR_KCAL, Metric.ENERGY_PER_MINUTE_KCAL,
                Metric.HEART_RATE_BPM, Metric.METABOLIC_EQUIVALENT, Metric.ELAPSED_TIME_SECONDS,
                Metric.REMAINING_TIME_SECONDS)
        StandardBleUuids.CPS_MEASUREMENT -> setOf(Metric.POWER_WATTS, Metric.SPEED_MPS, Metric.CADENCE_RPM,
            Metric.PEDAL_POWER_BALANCE_PERCENT, Metric.ACCUMULATED_TORQUE_NM, Metric.ACCUMULATED_ENERGY_KJ,
            Metric.WHEEL_REVOLUTIONS, Metric.WHEEL_EVENT_TIME_TICKS, Metric.CRANK_REVOLUTIONS,
            Metric.CRANK_EVENT_TIME_TICKS)
        StandardBleUuids.CPS_VECTOR -> setOf(Metric.CADENCE_RPM, Metric.CRANK_REVOLUTIONS,
            Metric.CRANK_EVENT_TIME_TICKS)
        StandardBleUuids.CSC_MEASUREMENT -> setOf(Metric.SPEED_MPS, Metric.CADENCE_RPM,
            Metric.WHEEL_REVOLUTIONS, Metric.WHEEL_EVENT_TIME_TICKS, Metric.CRANK_REVOLUTIONS,
            Metric.CRANK_EVENT_TIME_TICKS)
        StandardBleUuids.RSC_MEASUREMENT -> setOf(Metric.SPEED_MPS, Metric.CADENCE_RPM,
            Metric.STRIDE_LENGTH_METERS, Metric.DISTANCE_METERS, Metric.WALKING_RUNNING_STATUS)
        StandardBleUuids.HEART_RATE_MEASUREMENT -> setOf(Metric.HEART_RATE_BPM,
            Metric.HEART_RATE_ENERGY_KJ, Metric.RR_INTERVAL_SECONDS)
        else -> emptySet()
    }
    override fun decode(characteristicUuid: UUID, value: ByteArray, timestampMillis: Long) =
        StandardMeasurementDecoder.decode(characteristicUuid, value, timestampMillis)
}

private class PacketReader(private val bytes: ByteArray) {
    private var offset = 0
    val remaining: Int get() = bytes.size - offset

    fun u8(): Int {
        requireBytes(1)
        return bytes[offset++].toInt() and 0xFF
    }

    fun u16(): Int = u8() or (u8() shl 8)
    fun s16(): Int = u16().toShort().toInt()
    fun u24(): Int = u8() or (u8() shl 8) or (u8() shl 16)
    fun u32(): Long = u8().toLong() or (u8().toLong() shl 8) or
        (u8().toLong() shl 16) or (u8().toLong() shl 24)

    fun requireConsumed() {
        if (remaining != 0) throw MalformedPacketException()
    }

    private fun requireBytes(count: Int) {
        if (remaining < count) throw MalformedPacketException()
    }
}

private fun Int.bit(bit: Int): Boolean = this and (1 shl bit) != 0
private class MalformedPacketException : RuntimeException()
