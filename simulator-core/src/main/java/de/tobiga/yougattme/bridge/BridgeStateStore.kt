package de.tobiga.yougattme.bridge

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.os.Looper
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

enum class InputConnectionStatus {
    DISCOVERED, CONNECTING, INITIALIZING, PROTOCOL_SELECTION_REQUIRED,
    READY, RECONNECTING, DISCONNECTED, ERROR
}

data class InputProtocolOption(
    val adapterId: String,
    val displayName: String
)

data class InputDeviceState(
    val identity: DeviceIdentity,
    val rssi: Int? = null,
    val status: InputConnectionStatus = InputConnectionStatus.DISCOVERED,
    val advertisedServices: Set<StandardService> = emptySet(),
    val endpoints: List<SourceEndpoint> = emptyList(),
    val detail: String? = null,
    val protocolCandidates: List<InputProtocolOption> = emptyList(),
    val resolvedAdapterIds: Set<String> = emptySet(),
    val protocolOverride: String? = null
)

data class BridgeLogEntry(
    val sequence: Long,
    val timestampMillis: Long,
    val category: String,
    val message: String,
    val payloadHex: String? = null
)

/** Thread-safe application-lifetime bridge state shared by UI, profiles and BleBridgeService. */
class BridgeStateStore private constructor() {
    companion object {
        @JvmStatic val shared: BridgeStateStore by lazy { BridgeStateStore() }
    }

    private val _devices = MutableLiveData<Map<String, InputDeviceState>>(emptyMap())
    val devices: LiveData<Map<String, InputDeviceState>> = _devices
    private val _routes = MutableLiveData<Map<String, ServiceRoute>>(emptyMap())
    val routes: LiveData<Map<String, ServiceRoute>> = _routes
    private val _controlRoutes = MutableLiveData<Map<String, ControlRoute>>(emptyMap())
    val controlRoutes: LiveData<Map<String, ControlRoute>> = _controlRoutes
    private val _capabilityRoutes = MutableLiveData<Map<String, CapabilityRoute>>(emptyMap())
    val capabilityRoutes: LiveData<Map<String, CapabilityRoute>> = _capabilityRoutes
    @Deprecated("Use capabilityRoutes")
    private val _capabilityModes = MutableLiveData<Map<String, CapabilityMode>>(emptyMap())
    @Deprecated("Use capabilityRoutes")
    val capabilityModes: LiveData<Map<String, CapabilityMode>> = _capabilityModes
    private val _samples = MutableLiveData<Map<String, Map<Metric, MetricSample>>>(emptyMap())
    val samples: LiveData<Map<String, Map<Metric, MetricSample>>> = _samples
    private val _latestMeasurements = MutableLiveData<Map<String, DecodedMeasurement>>(emptyMap())
    /** Last complete typed packet per endpoint, including protocol fields without common equivalents. */
    val latestMeasurements: LiveData<Map<String, DecodedMeasurement>> = _latestMeasurements
    private val _resolvedRoutes = MutableLiveData<Map<String, ResolvedRoute>>(emptyMap())
    val resolvedRoutes: LiveData<Map<String, ResolvedRoute>> = _resolvedRoutes
    private val _scanning = MutableLiveData(false)
    val scanning: LiveData<Boolean> = _scanning
    private val _serviceRunning = MutableLiveData(false)
    val serviceRunning: LiveData<Boolean> = _serviceRunning
    private val _showAllDevices = MutableLiveData(false)
    val showAllDevices: LiveData<Boolean> = _showAllDevices
    private val _log = MutableLiveData<List<BridgeLogEntry>>(emptyList())
    val log: LiveData<List<BridgeLogEntry>> = _log

    private val lastSampleTimes = mutableMapOf<Pair<String, Metric>, Long>()
    private val intervals = mutableMapOf<Pair<String, Metric>, Long>()
    private val logBuffer = ArrayDeque<BridgeLogEntry>()
    private val sequence = AtomicLong()

    @Synchronized fun setServiceRunning(running: Boolean) { _serviceRunning.publish(running) }
    @Synchronized fun setScanning(scanning: Boolean) { _scanning.publish(scanning) }
    @Synchronized fun setShowAllDevices(show: Boolean) { _showAllDevices.publish(show) }

    @Synchronized
    fun updateDevice(state: InputDeviceState) {
        _devices.publish(_devices.value.orEmpty() + (state.identity.address to state))
    }

    @Synchronized
    fun removeDevice(address: String) {
        _devices.publish(_devices.value.orEmpty() - address)
    }

    @Synchronized
    fun setRoute(route: ServiceRoute) {
        _routes.publish(_routes.value.orEmpty() + (route.outputKey to route))
    }

    @Synchronized
    fun setControlRoute(outputKey: String, route: ControlRoute) {
        _controlRoutes.publish(_controlRoutes.value.orEmpty() + (outputKey to route))
    }

    @Synchronized
    fun setCapabilityRoute(outputKey: String, route: CapabilityRoute) {
        _capabilityRoutes.publish(_capabilityRoutes.value.orEmpty() + (outputKey to route))
        val legacyMode = when (route) {
            CapabilityRoute.SimulatorConfiguration -> CapabilityMode.SIMULATOR_CONFIGURATION
            is CapabilityRoute.Mirror -> CapabilityMode.MIRROR_CONNECTED_SERVICE
        }
        _capabilityModes.publish(_capabilityModes.value.orEmpty() + (outputKey to legacyMode))
    }

    @Deprecated("Use setCapabilityRoute")
    @Synchronized
    fun setCapabilityMode(outputKey: String, mode: CapabilityMode) {
        val route = when (mode) {
            CapabilityMode.SIMULATOR_CONFIGURATION -> CapabilityRoute.SimulatorConfiguration
            CapabilityMode.MIRROR_CONNECTED_SERVICE -> {
                val endpointId = (_capabilityRoutes.value.orEmpty()[outputKey] as? CapabilityRoute.Mirror)?.endpointId
                    ?: _routes.value.orEmpty()[outputKey]?.fieldOverrides?.values
                        ?.filterIsInstance<RouteSource.Endpoint>()?.firstOrNull()?.endpointId
                    ?: (_routes.value.orEmpty()[outputKey]?.defaultSource as? RouteSource.Endpoint)?.endpointId
                endpointId?.let { CapabilityRoute.Mirror(it) } ?: CapabilityRoute.SimulatorConfiguration
            }
        }
        setCapabilityRoute(outputKey, route)
    }

    @Synchronized
    fun accept(endpointId: String, decoded: DecodedMeasurement) {
        _latestMeasurements.publish(_latestMeasurements.value.orEmpty() + (endpointId to decoded))
        val current = _samples.value.orEmpty().toMutableMap()
        val endpointSamples = current[endpointId].orEmpty().toMutableMap()
        decoded.metrics.forEach { (metric, value) ->
            val key = endpointId to metric
            val previous = lastSampleTimes[key]
            if (previous != null && decoded.timestampMillis > previous) {
                val measured = decoded.timestampMillis - previous
                intervals[key] = intervals[key]?.let { (it * 3 + measured) / 4 } ?: measured
            }
            lastSampleTimes[key] = decoded.timestampMillis
            endpointSamples[metric] = MetricSample(
                metric, value, endpointId, decoded.timestampMillis,
                valid = true, observedIntervalMillis = intervals[key]
            )
        }
        current[endpointId] = endpointSamples
        _samples.publish(current)
    }

    @Synchronized
    fun invalidateEndpoint(endpointId: String) {
        val current = _samples.value.orEmpty().toMutableMap()
        current[endpointId] = current[endpointId].orEmpty().mapValues { (_, value) ->
            value.copy(valid = false)
        }
        _samples.publish(current)
    }

    @Synchronized
    fun setResolved(route: ResolvedRoute) {
        _resolvedRoutes.publish(_resolvedRoutes.value.orEmpty() + (route.outputKey to route))
    }

    @Synchronized
    fun addLog(category: String, message: String, payload: ByteArray? = null) {
        logBuffer.addFirst(
            BridgeLogEntry(
                sequence.incrementAndGet(), System.currentTimeMillis(), category, message,
                payload?.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            )
        )
        while (logBuffer.size > 200) logBuffer.removeLast()
        _log.publish(logBuffer.toList())
    }

    @Synchronized
    fun clearLog() {
        logBuffer.clear()
        _log.publish(emptyList())
    }

    fun endpoint(endpointId: String): SourceEndpoint? =
        _devices.value.orEmpty().values.flatMap(InputDeviceState::endpoints).firstOrNull { it.id == endpointId }
}

private fun <T> MutableLiveData<T>.publish(value: T) {
    if (Looper.myLooper() == Looper.getMainLooper()) this.value = value else postValue(value)
}
