package de.tobiga.yougattme.bridge

import android.content.Context
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
    val serviceName: String = service?.shortName ?: "Input adapter",
    /** Stable provider-defined identity. It deliberately does not imply a Bluetooth SIG service. */
    val protocolId: String = service?.name ?: "input-adapter",
    val controlCapabilities: Set<ControlCommand.Kind> = emptySet(),
    val settingsActivityClassName: String? = null,
    val serviceUuid: UUID? = service?.serviceUuid,
    val capabilityValues: Map<UUID, ByteArray> = emptyMap()
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
    val outputService: StandardService?,
    val defaultSource: RouteSource = RouteSource.Manual,
    val fieldOverrides: Map<Metric, RouteSource> = emptyMap(),
    val outputProtocolId: String = outputService?.name ?: outputKey
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

enum class GattWritePreference {
    WITH_RESPONSE,
    WITHOUT_RESPONSE,
    WITHOUT_RESPONSE_FALLBACK_WITH_RESPONSE
}

enum class TransactionCompletion {
    WRITE_CALLBACK,
    FIRE_AND_FORGET,
    MATCHING_NOTIFICATION
}

/** A provider-defined initialization write performed after subscriptions have completed. */
data class AdapterTransaction(
    val characteristicUuid: UUID,
    val value: ByteArray,
    val writePreference: GattWritePreference = GattWritePreference.WITH_RESPONSE,
    val completion: TransactionCompletion = TransactionCompletion.WRITE_CALLBACK,
    val responseCharacteristicUuid: UUID? = null,
    val delayBeforeMillis: Long = 0L,
    val timeoutMillis: Long = 10_000L,
    val fatalOnTimeout: Boolean = true,
    val responseMatcher: (ByteArray) -> Boolean = { true }
)

/** Protocol-neutral controls used when a command crosses a protocol boundary. */
sealed interface ControlCommand {
    val kind: Kind

    enum class Kind {
        REQUEST_CONTROL, RELEASE_CONTROL, RESET, START, STOP,
        TARGET_SPEED, TARGET_INCLINATION, TARGET_POWER, BASIC_RESISTANCE,
        TARGET_DISTANCE, TARGET_TRAINING_TIME, TARGET_ENERGY, TARGET_HEART_RATE,
        SIMULATION_PARAMETERS,
        WHEEL_CONFIGURATION, CALIBRATION, PROTOCOL_EXTENSION
    }

    data object RequestControl : ControlCommand { override val kind = Kind.REQUEST_CONTROL }
    data object ReleaseControl : ControlCommand { override val kind = Kind.RELEASE_CONTROL }
    data object Reset : ControlCommand { override val kind = Kind.RESET }
    data object Start : ControlCommand { override val kind = Kind.START }
    data class Stop(val pause: Boolean = false) : ControlCommand { override val kind = Kind.STOP }
    data class TargetSpeed(val metersPerSecond: Double) : ControlCommand { override val kind = Kind.TARGET_SPEED }
    data class TargetInclination(val percent: Double) : ControlCommand { override val kind = Kind.TARGET_INCLINATION }
    data class TargetPower(val watts: Double) : ControlCommand { override val kind = Kind.TARGET_POWER }
    data class BasicResistance(val percent: Double) : ControlCommand { override val kind = Kind.BASIC_RESISTANCE }
    data class TargetDistance(val meters: Double) : ControlCommand { override val kind = Kind.TARGET_DISTANCE }
    data class TargetTrainingTime(val seconds: Int) : ControlCommand { override val kind = Kind.TARGET_TRAINING_TIME }
    data class TargetEnergy(val kilocalories: Int) : ControlCommand { override val kind = Kind.TARGET_ENERGY }
    data class TargetHeartRate(val bpm: Int) : ControlCommand { override val kind = Kind.TARGET_HEART_RATE }
    data class SimulationParameters(
        val windSpeedMetersPerSecond: Double,
        val gradePercent: Double,
        val rollingResistanceCoefficient: Double,
        val windResistanceCoefficient: Double,
        val draftingFactor: Double = 1.0
    ) : ControlCommand { override val kind = Kind.SIMULATION_PARAMETERS }
    data class WheelConfiguration(
        val circumferenceMeters: Double,
        val riderWeightKg: Double? = null,
        val bikeWeightKg: Double? = null
    ) : ControlCommand { override val kind = Kind.WHEEL_CONFIGURATION }
    data class Calibration(val spinDown: Boolean, val zeroOffset: Boolean = false) : ControlCommand {
        override val kind = Kind.CALIBRATION
    }
    data class ProtocolExtension(val namespace: String, val payload: ByteArray) : ControlCommand {
        override val kind = Kind.PROTOCOL_EXTENSION
    }
}

enum class ControlStatus { SUCCESS, UNSUPPORTED, INVALID_PARAMETER, REJECTED, FAILED, TIMEOUT }

data class ControlResult(
    val status: ControlStatus,
    val responseData: ByteArray = ByteArray(0),
    val protocolResponse: ByteArray? = null
) {
    val success: Boolean get() = status == ControlStatus.SUCCESS
}

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
    val protocolId: String get() = providerId
    val preferredWriteType: GattWritePreference get() = GattWritePreference.WITH_RESPONSE
    val controlCapabilities: Set<ControlCommand.Kind> get() = emptySet()
    val settingsActivityClassName: String? get() = null
    val requiresFirstMeasurementBeforeReady: Boolean get() = false
    fun initialize(context: Context) = Unit
    fun qualifiesAsReadyMeasurement(measurement: DecodedMeasurement): Boolean = true
    fun protocolId(serviceUuid: UUID): String = StandardService.fromUuid(serviceUuid)?.name ?: protocolId
    fun controlCapabilities(serviceUuid: UUID): Set<ControlCommand.Kind> = controlCapabilities
    fun controlCapabilities(
        serviceUuid: UUID,
        capabilityValues: Map<UUID, ByteArray>
    ): Set<ControlCommand.Kind> = controlCapabilities(serviceUuid)
    fun serviceName(serviceUuid: UUID): String
    fun supportedMetrics(characteristicUuid: UUID): Set<Metric>
    fun matchesAdvertisement(serviceUuids: Set<UUID>): Boolean = serviceUuids.any { it in supportedServices }
    fun initializationTransactions(serviceUuid: UUID): List<AdapterTransaction> = emptyList()
    fun followUpTransactions(endpointId: String, measurement: DecodedMeasurement): List<AdapterTransaction> = emptyList()
    fun encodeControl(command: ControlCommand): AdapterTransaction? = null
    fun encodeControlTransactions(command: ControlCommand, endpoint: SourceEndpoint? = null): List<AdapterTransaction> =
        encodeControl(command)?.let(::listOf).orEmpty()
    fun handleControlLocally(command: ControlCommand): ControlResult? = null
    fun controlResult(command: ControlCommand, success: Boolean, response: ByteArray?): ControlResult =
        ControlResult(if (success) ControlStatus.SUCCESS else ControlStatus.FAILED, protocolResponse = response)
    fun decode(characteristicUuid: UUID, value: ByteArray, timestampMillis: Long): DecodedMeasurement?
    fun decode(endpointId: String, characteristicUuid: UUID, value: ByteArray, timestampMillis: Long): DecodedMeasurement? =
        decode(characteristicUuid, value, timestampMillis)
}

interface InputAdapterProvider {
    val providerId: String
    fun createAdapters(): List<InputAdapter>
}

class InputAdapterRegistry(providers: Iterable<InputAdapterProvider>, context: Context? = null) {
    private val adapters = providers.flatMap(InputAdapterProvider::createAdapters)
        .onEach { adapter -> context?.let(adapter::initialize) }
    fun adapterFor(serviceUuid: UUID): InputAdapter? =
        adapters.firstOrNull { serviceUuid in it.supportedServices }
    fun adapterForProtocol(protocolId: String): InputAdapter? = adapters.firstOrNull { adapter ->
        adapter.protocolId == protocolId || adapter.supportedServices.any { adapter.protocolId(it) == protocolId }
    }
    fun matchesAdvertisement(serviceUuids: Set<UUID>): Boolean = adapters.any { it.matchesAdvertisement(serviceUuids) }
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
