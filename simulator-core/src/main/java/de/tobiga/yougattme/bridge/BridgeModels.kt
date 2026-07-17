package de.tobiga.yougattme.bridge

import java.util.UUID

enum class StandardService(val shortName: String, val serviceUuid: UUID) {
    FTMS("FTMS", bluetoothUuid(0x1826)),
    CYCLING_POWER("Cycling Power", bluetoothUuid(0x1818)),
    CSC("CSC", bluetoothUuid(0x1816)),
    RSC("RSC", bluetoothUuid(0x1814)),
    HEART_RATE("Heart Rate", bluetoothUuid(0x180D));

    companion object {
        fun fromUuid(uuid: UUID): StandardService? = entries.firstOrNull { it.serviceUuid == uuid }
    }
}

enum class Metric(val displayName: String) {
    SPEED_MPS("Speed"),
    AVERAGE_SPEED_MPS("Average speed"),
    CADENCE_RPM("Cadence"),
    AVERAGE_CADENCE_RPM("Average cadence"),
    POWER_WATTS("Power"),
    AVERAGE_POWER_WATTS("Average power"),
    HEART_RATE_BPM("Heart rate"),
    DISTANCE_METERS("Distance"),
    RESISTANCE_LEVEL("Resistance"),
    INCLINATION_PERCENT("Inclination"),
    RAMP_ANGLE_DEGREES("Ramp angle"),
    PACE_SECONDS("Pace"),
    AVERAGE_PACE_SECONDS("Average pace"),
    ENERGY_KCAL("Energy"),
    ENERGY_PER_HOUR_KCAL("Energy/hour"),
    ENERGY_PER_MINUTE_KCAL("Energy/minute"),
    METABOLIC_EQUIVALENT("MET"),
    ELAPSED_TIME_SECONDS("Elapsed time"),
    REMAINING_TIME_SECONDS("Remaining time"),
    STEP_RATE_PER_MINUTE("Step rate"),
    AVERAGE_STEP_RATE_PER_MINUTE("Average step rate"),
    STEP_COUNT("Steps"),
    STRIDE_COUNT("Strides"),
    STROKE_RATE_PER_MINUTE("Stroke rate"),
    AVERAGE_STROKE_RATE_PER_MINUTE("Average stroke rate"),
    STROKE_COUNT("Strokes"),
    FLOORS("Floors"),
    POSITIVE_ELEVATION_METERS("Positive elevation"),
    NEGATIVE_ELEVATION_METERS("Negative elevation"),
    FORCE_NEWTONS("Force"),
    STRIDE_LENGTH_METERS("Stride length"),
    WHEEL_REVOLUTIONS("Wheel revolutions"),
    WHEEL_EVENT_TIME_TICKS("Wheel event time"),
    CRANK_REVOLUTIONS("Crank revolutions"),
    CRANK_EVENT_TIME_TICKS("Crank event time"),
    PEDAL_POWER_BALANCE_PERCENT("Pedal balance"),
    ACCUMULATED_TORQUE_NM("Accumulated torque"),
    ACCUMULATED_ENERGY_KJ("Accumulated energy"),
    RR_INTERVAL_SECONDS("RR interval"),
    HEART_RATE_ENERGY_KJ("HR energy"),
    MOVEMENT_DIRECTION("Movement direction"),
    WALKING_RUNNING_STATUS("Walking/running")
}

data class SourceEndpoint(
    val id: String,
    val deviceAddress: String,
    val deviceName: String?,
    val service: StandardService?,
    val measurementCharacteristic: UUID,
    val supportedMetrics: Set<Metric>,
    val controlPointCharacteristic: UUID? = null,
    val initialized: Boolean = false,
    val serviceName: String = service?.shortName ?: "Input adapter"
) {
    val label: String
        get() = "${deviceName?.takeIf(String::isNotBlank) ?: deviceAddress} · $serviceName"
}

data class MetricSample(
    val metric: Metric,
    val value: Double,
    val endpointId: String,
    val timestampMillis: Long,
    val valid: Boolean = true,
    val observedIntervalMillis: Long? = null
) {
    fun isFresh(nowMillis: Long): Boolean {
        val threshold = maxOf(5_000L, (observedIntervalMillis ?: 0L) * 3L)
        return valid && nowMillis - timestampMillis <= threshold
    }
}

sealed interface RouteSource {
    data object Manual : RouteSource
    data object Inherit : RouteSource
    data class Endpoint(val endpointId: String) : RouteSource
}

data class ServiceRoute(
    val outputKey: String,
    val outputService: StandardService,
    val defaultSource: RouteSource = RouteSource.Manual,
    val fieldOverrides: Map<Metric, RouteSource> = emptyMap()
)

sealed interface ControlRoute {
    data object LocalSimulator : ControlRoute
    data class Remote(val endpointId: String) : ControlRoute
}

enum class CapabilityMode {
    SIMULATOR_CONFIGURATION,
    MIRROR_CONNECTED_SERVICE
}

enum class RouteFreshness { FRESH, INCOMPLETE, STALE }

data class ResolvedRoute(
    val outputKey: String,
    val freshness: RouteFreshness,
    val values: Map<Metric, MetricSample>,
    val manualMetrics: Set<Metric>,
    val missingMetrics: Set<Metric>,
    val staleMetrics: Set<Metric>
)

data class EndpointCapabilities(
    val featureBytes: ByteArray? = null,
    val ranges: Map<UUID, ByteArray> = emptyMap(),
    val sensorLocation: Int? = null,
    val bodySensorLocation: Int? = null,
    val characteristicUuids: Set<UUID> = emptySet()
)

data class DeviceIdentity(val address: String, val name: String?)

fun bluetoothUuid(shortUuid: Int): UUID =
    UUID.fromString("0000%04X-0000-1000-8000-00805F9B34FB".format(shortUuid and 0xFFFF))

object StandardBleUuids {
    val CCCD: UUID = bluetoothUuid(0x2902)
    val FTMS_FEATURE: UUID = bluetoothUuid(0x2ACC)
    val FTMS_TREADMILL_DATA: UUID = bluetoothUuid(0x2ACD)
    val FTMS_CROSS_TRAINER_DATA: UUID = bluetoothUuid(0x2ACE)
    val FTMS_STEP_CLIMBER_DATA: UUID = bluetoothUuid(0x2ACF)
    val FTMS_STAIR_CLIMBER_DATA: UUID = bluetoothUuid(0x2AD0)
    val FTMS_ROWER_DATA: UUID = bluetoothUuid(0x2AD1)
    val FTMS_INDOOR_BIKE_DATA: UUID = bluetoothUuid(0x2AD2)
    val FTMS_TRAINING_STATUS: UUID = bluetoothUuid(0x2AD3)
    val FTMS_SUPPORTED_SPEED_RANGE: UUID = bluetoothUuid(0x2AD4)
    val FTMS_SUPPORTED_INCLINATION_RANGE: UUID = bluetoothUuid(0x2AD5)
    val FTMS_SUPPORTED_RESISTANCE_RANGE: UUID = bluetoothUuid(0x2AD6)
    val FTMS_SUPPORTED_HEART_RATE_RANGE: UUID = bluetoothUuid(0x2AD7)
    val FTMS_SUPPORTED_POWER_RANGE: UUID = bluetoothUuid(0x2AD8)
    val FTMS_CONTROL_POINT: UUID = bluetoothUuid(0x2AD9)
    val FTMS_STATUS: UUID = bluetoothUuid(0x2ADA)
    val CPS_MEASUREMENT: UUID = bluetoothUuid(0x2A63)
    val CPS_VECTOR: UUID = bluetoothUuid(0x2A64)
    val CPS_FEATURE: UUID = bluetoothUuid(0x2A65)
    val CPS_SENSOR_LOCATION: UUID = bluetoothUuid(0x2A5D)
    val CPS_CONTROL_POINT: UUID = bluetoothUuid(0x2A66)
    val CSC_MEASUREMENT: UUID = bluetoothUuid(0x2A5B)
    val CSC_FEATURE: UUID = bluetoothUuid(0x2A5C)
    val CSC_SENSOR_LOCATION: UUID = bluetoothUuid(0x2A5D)
    val CSC_CONTROL_POINT: UUID = bluetoothUuid(0x2A55)
    val RSC_MEASUREMENT: UUID = bluetoothUuid(0x2A53)
    val RSC_FEATURE: UUID = bluetoothUuid(0x2A54)
    val RSC_SENSOR_LOCATION: UUID = bluetoothUuid(0x2A5D)
    val RSC_CONTROL_POINT: UUID = bluetoothUuid(0x2A55)
    val HEART_RATE_MEASUREMENT: UUID = bluetoothUuid(0x2A37)
    val BODY_SENSOR_LOCATION: UUID = bluetoothUuid(0x2A38)
    val HEART_RATE_CONTROL_POINT: UUID = bluetoothUuid(0x2A39)

    val measurementCharacteristics: Set<UUID> = setOf(
        FTMS_TREADMILL_DATA, FTMS_CROSS_TRAINER_DATA, FTMS_STEP_CLIMBER_DATA,
        FTMS_STAIR_CLIMBER_DATA, FTMS_ROWER_DATA, FTMS_INDOOR_BIKE_DATA,
        CPS_MEASUREMENT, CPS_VECTOR, CSC_MEASUREMENT, RSC_MEASUREMENT,
        HEART_RATE_MEASUREMENT
    )

    val statusCharacteristics: Set<UUID> = setOf(FTMS_STATUS, FTMS_TRAINING_STATUS)
    val controlPointCharacteristics: Set<UUID> = setOf(
        FTMS_CONTROL_POINT, CPS_CONTROL_POINT, CSC_CONTROL_POINT, RSC_CONTROL_POINT,
        HEART_RATE_CONTROL_POINT
    )
    val readableCapabilityCharacteristics: Set<UUID> = setOf(
        FTMS_FEATURE, FTMS_SUPPORTED_SPEED_RANGE, FTMS_SUPPORTED_INCLINATION_RANGE,
        FTMS_SUPPORTED_RESISTANCE_RANGE, FTMS_SUPPORTED_HEART_RATE_RANGE,
        FTMS_SUPPORTED_POWER_RANGE, CPS_FEATURE, CPS_SENSOR_LOCATION, CSC_FEATURE,
        CSC_SENSOR_LOCATION, RSC_FEATURE, RSC_SENSOR_LOCATION, BODY_SENSOR_LOCATION
    )
}

interface InputAdapter {
    val providerId: String
    val supportedServices: Set<UUID>
    val measurementCharacteristics: Set<UUID>
    val statusCharacteristics: Set<UUID> get() = emptySet()
    val controlPointCharacteristics: Set<UUID> get() = emptySet()
    val readableCapabilityCharacteristics: Set<UUID> get() = emptySet()
    fun serviceName(serviceUuid: UUID): String
    fun supportedMetrics(characteristicUuid: UUID): Set<Metric>
    fun decode(characteristicUuid: UUID, value: ByteArray, timestampMillis: Long): DecodedMeasurement?
}

interface InputAdapterProvider {
    val providerId: String
    fun createAdapters(): List<InputAdapter>
}

class InputAdapterRegistry(providers: Iterable<InputAdapterProvider>) {
    private val adapters = providers.flatMap(InputAdapterProvider::createAdapters)
    fun adapterFor(serviceUuid: UUID): InputAdapter? =
        adapters.firstOrNull { serviceUuid in it.supportedServices }
    val supportedServiceUuids: Set<UUID> = adapters.flatMap(InputAdapter::supportedServices).toSet()
}

data class DecodedMeasurement(
    val service: StandardService?,
    val characteristicUuid: UUID,
    val flags: Long,
    val metrics: Map<Metric, Double>,
    val protocolFields: Map<String, Any>,
    val timestampMillis: Long,
    val moreData: Boolean = false,
    val protocolId: String = service?.name ?: "input-adapter"
)
