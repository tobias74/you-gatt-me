package de.tobiga.yougattme.bridge

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import de.tobiga.yougattme.ApplicationState
import de.tobiga.yougattme.MainActivity
import de.tobiga.yougattme.R
import de.tobiga.yougattme.SimulationStateStore
import de.tobiga.yougattme.bridge.BridgeStateStore
import de.tobiga.yougattme.bridge.CapabilityMode
import de.tobiga.yougattme.bridge.ControlRoute
import de.tobiga.yougattme.bridge.DecodedMeasurement
import de.tobiga.yougattme.bridge.DerivedMetricsEngine
import de.tobiga.yougattme.bridge.DeviceIdentity
import de.tobiga.yougattme.bridge.EndpointCapabilities
import de.tobiga.yougattme.bridge.InputConnectionStatus
import de.tobiga.yougattme.bridge.InputAdapterProviderLoader
import de.tobiga.yougattme.bridge.InputAdapterRegistry
import de.tobiga.yougattme.bridge.InputDeviceState
import de.tobiga.yougattme.bridge.Metric
import de.tobiga.yougattme.bridge.RouteFreshness
import de.tobiga.yougattme.bridge.RouteSource
import de.tobiga.yougattme.bridge.RemoteControlDispatcher
import de.tobiga.yougattme.bridge.RemoteControlResult
import de.tobiga.yougattme.bridge.RemoteControlTransport
import de.tobiga.yougattme.bridge.RoutingEngine
import de.tobiga.yougattme.bridge.ServiceRoute
import de.tobiga.yougattme.bridge.SourceEndpoint
import de.tobiga.yougattme.bridge.StandardBleUuids
import de.tobiga.yougattme.bridge.StandardInputAdapterProvider
import de.tobiga.yougattme.bridge.StandardService
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsSupportedRanges
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class BleBridgeService : Service() {
    inner class LocalBinder : Binder() {
        val service: BleBridgeService get() = this@BleBridgeService
    }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private val state = SimulationStateStore.shared
    private val bridge = BridgeStateStore.shared
    private val routing = RoutingEngine()
    private val derived = DerivedMetricsEngine()
    private val sessions = mutableMapOf<String, DeviceSession>()
    private val reconnectAttempts = mutableMapOf<String, Int>()
    private val reconnectRunnables = mutableMapOf<String, Runnable>()
    private val discovered = mutableMapOf<String, BluetoothDevice>()
    private val capabilities = mutableMapOf<String, EndpointCapabilities>()
    private val controlRouteGenerations = mutableMapOf<String, Long>()
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var inputAdapters: InputAdapterRegistry
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(applicationContext) }
    private var showAllDevices = false
    private var scanning = false
    private val stopScanAfterWindow = Runnable { stopScan() }

    private val staleCheck = object : Runnable {
        override fun run() {
            resolveAndApplyRoutes()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        inputAdapters = InputAdapterRegistry(
            listOf(StandardInputAdapterProvider()) + InputAdapterProviderLoader.loadFromManifest(this)
        )
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        bridge.setServiceRunning(true)
        RemoteControlDispatcher.transport = RemoteControlTransport { outputKey, request, calibration, completion ->
            val route = bridge.controlRoutes.value.orEmpty()[outputKey] as? ControlRoute.Remote
            val generation = controlRouteGenerations[outputKey] ?: 0L
            val expectedService = mapOf(
                "ftms" to StandardService.FTMS, "cycling_power" to StandardService.CYCLING_POWER,
                "csc" to StandardService.CSC, "rsc" to StandardService.RSC,
                "heart_rate" to StandardService.HEART_RATE
            )[outputKey]
            if (route == null || bridge.endpoint(route.endpointId)?.service != expectedService) {
                completion(RemoteControlResult(false, null))
            }
            else writeControl(route.endpointId, request, calibration) { success, response ->
                completion(RemoteControlResult(
                    success && (controlRouteGenerations[outputKey] ?: 0L) == generation,
                    if ((controlRouteGenerations[outputKey] ?: 0L) == generation) response else null
                ))
            }
        }
        restoreRoutes()
        restoreKnownDevices()
        ensureDefaultRoutes()
        handler.post(staleCheck)
        bridge.addLog("RUNTIME", "Bridge foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBridge()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (prefs.getBoolean("gatt_server_activated", false)) {
                    val appState = ApplicationState.getInstance(applicationContext)
                    appState.ensureYouGattMeServerIsRunning()
                }
            }
        }
        updateNotification()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        handler.removeCallbacks(staleCheck)
        stopScan()
        reconnectRunnables.values.forEach(handler::removeCallbacks)
        reconnectRunnables.clear()
        sessions.values.toList().forEach { it.close(userInitiated = true) }
        sessions.clear()
        bridge.setServiceRunning(false)
        RemoteControlDispatcher.transport = null
        bridge.addLog("RUNTIME", "Bridge foreground service stopped")
        super.onDestroy()
    }

    fun startScan(showAll: Boolean = false) {
        if (!hasBluetoothPermissions()) return
        stopScan()
        showAllDevices = showAll
        bridge.setShowAllDevices(showAll)
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, scanCallback)
        scanning = true
        bridge.setScanning(true)
        bridge.addLog("SCAN", if (showAll) "Scanning for all BLE devices" else "Scanning for standard fitness services")
        handler.postDelayed(stopScanAfterWindow, SCAN_WINDOW_MILLIS)
    }

    fun stopScan() {
        handler.removeCallbacks(stopScanAfterWindow)
        if (!scanning) return
        if (hasBluetoothPermissions()) {
            runCatching { bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        }
        scanning = false
        bridge.setScanning(false)
    }

    fun connect(address: String) {
        if (!hasBluetoothPermissions() || sessions[address]?.isOpen == true) return
        reconnectRunnables.remove(address)?.let(handler::removeCallbacks)
        val device = discovered[address] ?: runCatching {
            bluetoothManager.adapter.getRemoteDevice(address)
        }.getOrNull() ?: return
        val name = bridge.devices.value.orEmpty()[address]?.identity?.name
        val session = DeviceSession(device, name)
        sessions[address] = session
        session.connect()
        prefs.edit().putStringSet(PREF_KNOWN_DEVICES,
            prefs.getStringSet(PREF_KNOWN_DEVICES, emptySet()).orEmpty() + address)
            .putString("bridge.device.$address.name", name).apply()
    }

    fun disconnect(address: String) {
        reconnectRunnables.remove(address)?.let(handler::removeCallbacks)
        reconnectAttempts.remove(address)
        sessions.remove(address)?.close(userInitiated = true)
        bridge.devices.value.orEmpty()[address]?.let {
            bridge.updateDevice(it.copy(status = InputConnectionStatus.DISCONNECTED, detail = "Disconnected by user"))
        }
        updateNotification()
    }

    fun setRoute(route: ServiceRoute) {
        bridge.setRoute(route)
        persistRoute(route)
        resolveAndApplyRoutes()
    }

    fun setControlRoute(outputKey: String, route: ControlRoute) {
        controlRouteGenerations[outputKey] = (controlRouteGenerations[outputKey] ?: 0L) + 1L
        bridge.setControlRoute(outputKey, route)
        prefs.edit().putString("bridge.control.$outputKey", when (route) {
            ControlRoute.LocalSimulator -> "local"
            is ControlRoute.Remote -> "remote:${route.endpointId}"
        }).apply()
    }

    fun setCapabilityMode(outputKey: String, mode: CapabilityMode): Boolean {
        if (mode == CapabilityMode.MIRROR_CONNECTED_SERVICE) {
            val endpoint = (bridge.routes.value.orEmpty()[outputKey]?.defaultSource as? RouteSource.Endpoint)
                ?.let { bridge.endpoint(it.endpointId) }
            if (endpoint == null || !mirrorCapabilities(outputKey, endpoint)) return false
        }
        bridge.setCapabilityMode(outputKey, mode)
        prefs.edit().putString("bridge.capability.$outputKey", mode.name).apply()
        return true
    }

    private fun mirrorCapabilities(outputKey: String, endpoint: SourceEndpoint): Boolean {
        val caps = capabilities[endpoint.id] ?: return false
        val feature = caps.featureBytes
        fun u16(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        fun s16(bytes: ByteArray, offset: Int): Int = u16(bytes, offset).toShort().toInt()
        fun u32(bytes: ByteArray, offset: Int): Int = u16(bytes, offset) or (u16(bytes, offset + 2) shl 16)
        fun bit(bits: Int, index: Int) = bits and (1 shl index) != 0
        when (outputKey) {
            "csc" -> {
                if (endpoint.service != StandardService.CSC || feature == null || feature.size < 2) return false
                val bits = u16(feature, 0) and 0x07
                state.cscConfiguration.apply {
                    mirrorActualIntoFeature = false; declaredFeatureBits = bits
                    includeWheelRevolutionData = bit(bits, 0); includeCrankRevolutionData = bit(bits, 1)
                    sensorLocationCharacteristicEnabled = StandardBleUuids.CSC_SENSOR_LOCATION in caps.characteristicUuids
                    controlPointCharacteristicEnabled = StandardBleUuids.CSC_CONTROL_POINT in caps.characteristicUuids
                    caps.sensorLocation?.let { sensorLocation = it }
                }
            }
            "rsc" -> {
                if (endpoint.service != StandardService.RSC || feature == null || feature.size < 2) return false
                val bits = u16(feature, 0) and 0x1F
                state.rscConfiguration.apply {
                    mirrorActualIntoFeature = false; declaredFeatureBits = bits
                    includeStrideLength = bit(bits, 0); includeTotalDistance = bit(bits, 1)
                    includeWalkingOrRunningStatus = bit(bits, 2)
                    sensorLocationCharacteristicEnabled = StandardBleUuids.RSC_SENSOR_LOCATION in caps.characteristicUuids
                    controlPointCharacteristicEnabled = StandardBleUuids.RSC_CONTROL_POINT in caps.characteristicUuids
                    caps.sensorLocation?.let { sensorLocation = it }
                }
            }
            "cycling_power" -> {
                if (endpoint.service != StandardService.CYCLING_POWER || feature == null || feature.size < 4) return false
                val bits = u32(feature, 0) and 0x003F_FFFF
                state.cyclingPowerConfiguration.apply {
                    mirrorActualIntoFeature = false; declaredFeatureBits = bits
                    includePedalPowerBalance = bit(bits, 0); includeAccumulatedTorque = bit(bits, 1)
                    includeWheelRevolutionData = bit(bits, 2); includeCrankRevolutionData = bit(bits, 3)
                    includeExtremeMagnitudes = bit(bits, 4); includeExtremeAngles = bit(bits, 5)
                    includeTopDeadSpotAngle = bit(bits, 6); includeBottomDeadSpotAngle = bit(bits, 6)
                    includeAccumulatedEnergy = bit(bits, 7); offsetCompensationIndicatorSupported = bit(bits, 8)
                    sensorLocationCharacteristicEnabled = StandardBleUuids.CPS_SENSOR_LOCATION in caps.characteristicUuids
                    controlPointCharacteristicEnabled = StandardBleUuids.CPS_CONTROL_POINT in caps.characteristicUuids
                    vectorCharacteristicEnabled = StandardBleUuids.CPS_VECTOR in caps.characteristicUuids
                    caps.sensorLocation?.let { sensorLocation = it }
                }
            }
            "heart_rate" -> {
                if (endpoint.service != StandardService.HEART_RATE) return false
                state.heartRateConfiguration.apply {
                    bodySensorLocationCharacteristicEnabled = StandardBleUuids.BODY_SENSOR_LOCATION in caps.characteristicUuids
                    controlPointCharacteristicEnabled = StandardBleUuids.HEART_RATE_CONTROL_POINT in caps.characteristicUuids
                    caps.bodySensorLocation?.let { bodySensorLocation = it }
                }
            }
            "ftms" -> {
                if (endpoint.service != StandardService.FTMS || feature == null || feature.size < 8) return false
                val machineBits = u32(feature, 0)
                val targetBits = u32(feature, 4)
                state.ftmsControlPointEnabled = StandardBleUuids.FTMS_CONTROL_POINT in caps.characteristicUuids
                state.ftmsMirrorTargetFeatureDeclarations = false
                state.ftmsDeclaredTargetFeatures.apply {
                    targetSpeed = bit(targetBits, 0); targetInclination = bit(targetBits, 1)
                    targetResistanceLevel = bit(targetBits, 2); targetPower = bit(targetBits, 3)
                    targetHeartRate = bit(targetBits, 4); targetedExpendedEnergy = bit(targetBits, 5)
                    targetedSteps = bit(targetBits, 6); targetedStrides = bit(targetBits, 7)
                    targetedDistance = bit(targetBits, 8); targetedTrainingTime = bit(targetBits, 9)
                    targetedTimeInTwoHeartRateZones = bit(targetBits, 10)
                    targetedTimeInThreeHeartRateZones = bit(targetBits, 11)
                    targetedTimeInFiveHeartRateZones = bit(targetBits, 12)
                    indoorBikeSimulationParameters = bit(targetBits, 13); wheelCircumference = bit(targetBits, 14)
                    spinDownControl = bit(targetBits, 15); targetedCadence = bit(targetBits, 16)
                }
                state.ftmsTargetFeatures.copyFrom(state.ftmsDeclaredTargetFeatures)
                mirrorFtmsMeasurementFields(machineBits)
                val current = state.ftmsSupportedRanges
                fun range(uuid: UUID) = caps.ranges[uuid]
                val speed = range(StandardBleUuids.FTMS_SUPPORTED_SPEED_RANGE)
                val incline = range(StandardBleUuids.FTMS_SUPPORTED_INCLINATION_RANGE)
                val resistance = range(StandardBleUuids.FTMS_SUPPORTED_RESISTANCE_RANGE)
                val heart = range(StandardBleUuids.FTMS_SUPPORTED_HEART_RATE_RANGE)
                val power = range(StandardBleUuids.FTMS_SUPPORTED_POWER_RANGE)
                state.ftmsSupportedRanges = FtmsSupportedRanges(
                    minSpeedKmh = speed?.takeIf { it.size >= 6 }?.let { u16(it, 0) / 100f } ?: current.minSpeedKmh,
                    maxSpeedKmh = speed?.takeIf { it.size >= 6 }?.let { u16(it, 2) / 100f } ?: current.maxSpeedKmh,
                    speedIncrementKmh = speed?.takeIf { it.size >= 6 }?.let { u16(it, 4) / 100f } ?: current.speedIncrementKmh,
                    minInclinationPercent = incline?.takeIf { it.size >= 6 }?.let { s16(it, 0) / 10f } ?: current.minInclinationPercent,
                    maxInclinationPercent = incline?.takeIf { it.size >= 6 }?.let { s16(it, 2) / 10f } ?: current.maxInclinationPercent,
                    inclinationIncrementPercent = incline?.takeIf { it.size >= 6 }?.let { u16(it, 4) / 10f } ?: current.inclinationIncrementPercent,
                    minResistanceLevel = resistance?.takeIf { it.size >= 6 }?.let { s16(it, 0) / 10f } ?: current.minResistanceLevel,
                    maxResistanceLevel = resistance?.takeIf { it.size >= 6 }?.let { s16(it, 2) / 10f } ?: current.maxResistanceLevel,
                    resistanceIncrement = resistance?.takeIf { it.size >= 6 }?.let { u16(it, 4) / 10f } ?: current.resistanceIncrement,
                    minHeartRateBpm = heart?.getOrNull(0)?.toInt()?.and(0xFF) ?: current.minHeartRateBpm,
                    maxHeartRateBpm = heart?.getOrNull(1)?.toInt()?.and(0xFF) ?: current.maxHeartRateBpm,
                    heartRateIncrementBpm = heart?.getOrNull(2)?.toInt()?.and(0xFF) ?: current.heartRateIncrementBpm,
                    minPowerWatt = power?.takeIf { it.size >= 6 }?.let { s16(it, 0) } ?: current.minPowerWatt,
                    maxPowerWatt = power?.takeIf { it.size >= 6 }?.let { s16(it, 2) } ?: current.maxPowerWatt,
                    powerIncrementWatt = power?.takeIf { it.size >= 6 }?.let { u16(it, 4) } ?: current.powerIncrementWatt
                )
            }
            else -> return false
        }
        bridge.addLog("CAPABILITY", "Mirrored ${endpoint.label} into $outputKey; downstream GATT rebuild required")
        return true
    }

    private fun mirrorFtmsMeasurementFields(bits: Int) {
        fun bit(index: Int) = bits and (1 shl index) != 0
        state.ftmsIndoorBikeData.apply {
            includeAverageSpeed = bit(0); includeInstantaneousCadence = bit(1); includeAverageCadence = bit(1)
            includeTotalDistance = bit(2); includeResistanceLevel = bit(7); includeExpendedEnergy = bit(9)
            includeHeartRate = bit(10); includeMetabolicEquivalent = bit(11); includeElapsedTime = bit(12)
            includeRemainingTime = bit(13); includeInstantaneousPower = bit(14); includeAveragePower = bit(14)
        }
        state.ftmsTreadmillData.apply {
            includeAverageSpeed = bit(0); includeTotalDistance = bit(2); includeInclination = bit(3)
            includeElevationGain = bit(4); includeInstantaneousPace = bit(5); includeAveragePace = bit(5)
            includeExpendedEnergy = bit(9); includeHeartRate = bit(10); includeMetabolicEquivalent = bit(11)
            includeElapsedTime = bit(12); includeRemainingTime = bit(13); includeForceOnBeltAndPowerOutput = bit(15)
        }
        state.ftmsCrossTrainerData.apply {
            includeAverageSpeed = bit(0); includeTotalDistance = bit(2); includeInclination = bit(3)
            includeElevationGain = bit(4); includeStepCount = bit(6); includeResistanceLevel = bit(7)
            includeStrideCount = bit(8); includeExpendedEnergy = bit(9); includeHeartRate = bit(10)
            includeMetabolicEquivalent = bit(11); includeElapsedTime = bit(12); includeRemainingTime = bit(13)
            includeInstantaneousPower = bit(14); includeAveragePower = bit(14)
        }
        state.ftmsRowerData.apply {
            includeAverageStrokeRate = bit(1); includeTotalDistance = bit(2); includeInstantaneousPace = bit(5)
            includeAveragePace = bit(5); includeResistanceLevel = bit(7); includeExpendedEnergy = bit(9)
            includeHeartRate = bit(10); includeMetabolicEquivalent = bit(11); includeElapsedTime = bit(12)
            includeRemainingTime = bit(13); includeInstantaneousPower = bit(14); includeAveragePower = bit(14)
        }
        state.ftmsStepClimberData.apply {
            includeStepsPerMinute = bit(6); includeAverageStepRate = bit(6)
            includePositiveElevationGain = bit(4); includeExpendedEnergy = bit(9)
            includeHeartRate = bit(10); includeMetabolicEquivalent = bit(11)
            includeElapsedTime = bit(12); includeRemainingTime = bit(13)
        }
        state.ftmsStairClimberData.apply {
            includeStepsPerMinute = bit(6); includeAverageStepRate = bit(6)
            includePositiveElevationGain = bit(4); includeStrideCount = bit(8)
            includeExpendedEnergy = bit(9); includeHeartRate = bit(10)
            includeMetabolicEquivalent = bit(11); includeElapsedTime = bit(12)
            includeRemainingTime = bit(13)
        }
    }

    fun writeControl(endpointId: String, request: ByteArray, calibration: Boolean = false,
        completion: (Boolean, ByteArray?) -> Unit) {
        val endpoint = bridge.endpoint(endpointId)
        val session = endpoint?.let { sessions[it.deviceAddress] }
        val characteristic = endpoint?.controlPointCharacteristic?.let { session?.findCharacteristic(it) }
        if (session == null || characteristic == null) {
            completion(false, null)
            return
        }
        if (!validateAgainstUpstreamRanges(endpoint, request)) {
            bridge.addLog("CONTROL TX", "Rejected out-of-range request for ${endpoint.label}", request)
            completion(false, null)
            return
        }
        bridge.addLog("CONTROL TX", endpoint.label, request)
        session.queue.enqueue(
            GattOperation.Write(
                characteristic, request, awaitIndication = endpoint.service != StandardService.HEART_RATE,
                timeoutMillis = if (calibration) 60_000L else 10_000L
            ) { success, response ->
                bridge.addLog("CONTROL RX", "${endpoint.label}: ${if (success) "completed" else "failed"}", response)
                completion(success, response)
            }
        )
    }

    private fun validateAgainstUpstreamRanges(endpoint: SourceEndpoint, request: ByteArray): Boolean {
        if (endpoint.service != StandardService.FTMS || request.isEmpty()) return true
        val ranges = capabilities[endpoint.id]?.ranges.orEmpty()
        fun u16(offset: Int) = (request[offset].toInt() and 0xFF) or ((request[offset + 1].toInt() and 0xFF) shl 8)
        fun s16(offset: Int) = u16(offset).toShort().toInt()
        fun range16(uuid: UUID, signed: Boolean): Pair<Int, Int>? {
            val data = ranges[uuid] ?: return null
            if (data.size < 4) return null
            val low = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
            val high = (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)
            return if (signed) low.toShort().toInt() to high.toShort().toInt() else low to high
        }
        return when (request[0].toInt() and 0xFF) {
            0x02 -> request.size == 3 && range16(StandardBleUuids.FTMS_SUPPORTED_SPEED_RANGE, false)
                ?.let { u16(1) in it.first..it.second } != false
            0x03 -> request.size == 3 && range16(StandardBleUuids.FTMS_SUPPORTED_INCLINATION_RANGE, true)
                ?.let { s16(1) in it.first..it.second } != false
            0x04 -> request.size == 3 && range16(StandardBleUuids.FTMS_SUPPORTED_RESISTANCE_RANGE, true)
                ?.let { s16(1) in it.first..it.second } != false
            0x05 -> request.size == 3 && range16(StandardBleUuids.FTMS_SUPPORTED_POWER_RANGE, true)
                ?.let { s16(1) in it.first..it.second } != false
            0x06 -> {
                val data = ranges[StandardBleUuids.FTMS_SUPPORTED_HEART_RATE_RANGE]
                request.size == 2 && (data == null || data.size < 2 ||
                    (request[1].toInt() and 0xFF) in (data[0].toInt() and 0xFF)..(data[1].toInt() and 0xFF))
            }
            else -> true
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val advertisedUuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }.toSet()
            val advertised = advertisedUuids.mapNotNull { uuid ->
                StandardService.fromUuid(uuid)
            }.toSet()
            if (!showAllDevices && advertisedUuids.none { it in inputAdapters.supportedServiceUuids }) return
            val device = result.device
            discovered[device.address] = device
            val existing = bridge.devices.value.orEmpty()[device.address]
            bridge.updateDevice(
                InputDeviceState(
                    identity = DeviceIdentity(device.address, result.scanRecord?.deviceName ?: existing?.identity?.name),
                    rssi = result.rssi,
                    status = existing?.status?.takeUnless { it == InputConnectionStatus.ERROR } ?: InputConnectionStatus.DISCOVERED,
                    advertisedServices = advertised,
                    endpoints = existing?.endpoints.orEmpty(),
                    detail = existing?.detail
                )
            )
            prefs.edit()
                .putStringSet(PREF_KNOWN_DEVICES,
                    prefs.getStringSet(PREF_KNOWN_DEVICES, emptySet()).orEmpty() + device.address)
                .putString("bridge.device.${device.address}.name", result.scanRecord?.deviceName ?: existing?.identity?.name)
                .apply()
        }

        override fun onScanFailed(errorCode: Int) {
            handler.removeCallbacks(stopScanAfterWindow)
            scanning = false
            bridge.setScanning(false)
            bridge.addLog("SCAN", "Scan failed ($errorCode)")
        }
    }

    private inner class DeviceSession(
        val device: BluetoothDevice,
        private val knownName: String?
    ) {
        var gatt: BluetoothGatt? = null
        val queue = GattOperationQueue({ gatt }, handler) {
            onDisconnected("GATT operation timed out")
        }
        var isOpen = false
        private var userInitiatedClose = false
        private val endpointIds = mutableSetOf<String>()
        private val capabilityBytes = mutableMapOf<Pair<UUID, UUID>, ByteArray>()
        private val failedSubscriptions = mutableSetOf<Pair<UUID, UUID>>()

        fun connect() {
            userInitiatedClose = false
            updateStatus(InputConnectionStatus.CONNECTING, "Connecting")
            bridge.addLog("GATT", "Connecting to ${knownName ?: device.address}")
            gatt = device.connectGatt(this@BleBridgeService, false, callback, BluetoothDevice.TRANSPORT_LE)
            isOpen = gatt != null
            if (!isOpen) onDisconnected("connectGatt returned null")
        }

        fun close(userInitiated: Boolean) {
            userInitiatedClose = userInitiated
            isOpen = false
            queue.clear()
            endpointIds.forEach { bridge.invalidateEndpoint(it); derived.clear(it) }
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            gatt = null
        }

        fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? =
            gatt?.services?.asSequence()?.mapNotNull { it.getCharacteristic(uuid) }?.firstOrNull()

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(callbackGatt: BluetoothGatt, status: Int, newState: Int) {
                handler.post {
                    if (callbackGatt !== this@DeviceSession.gatt) return@post
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        reconnectAttempts.remove(device.address)
                        isOpen = true
                        updateStatus(InputConnectionStatus.INITIALIZING, "Negotiating MTU")
                        queue.enqueue(GattOperation.RequestMtu(247))
                        queue.enqueue(GattOperation.Discover { success, _ ->
                            if (success) initializeServices() else onDisconnected("Service discovery failed")
                        })
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        onDisconnected("Disconnected (status $status)")
                    }
                }
            }

            override fun onMtuChanged(callbackGatt: BluetoothGatt, mtu: Int, status: Int) {
                handler.post { if (callbackGatt === this@DeviceSession.gatt) queue.onMtuChanged(status) }
            }
            override fun onServicesDiscovered(callbackGatt: BluetoothGatt, status: Int) {
                handler.post { if (callbackGatt === this@DeviceSession.gatt) queue.onServicesDiscovered(status) }
            }
            override fun onCharacteristicRead(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
                value: ByteArray, status: Int) {
                val snapshot = value.copyOf()
                handler.post {
                    if (callbackGatt === this@DeviceSession.gatt) queue.onCharacteristicRead(characteristic, snapshot, status)
                }
            }
            @Deprecated("API 32 callback")
            override fun onCharacteristicRead(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                val snapshot = characteristic.value?.copyOf() ?: ByteArray(0)
                handler.post {
                    if (callbackGatt === this@DeviceSession.gatt) queue.onCharacteristicRead(characteristic, snapshot, status)
                }
            }
            override fun onDescriptorWrite(callbackGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                handler.post {
                    if (callbackGatt === this@DeviceSession.gatt) queue.onDescriptorWrite(descriptor, status)
                }
            }
            override fun onCharacteristicWrite(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                handler.post {
                    if (callbackGatt === this@DeviceSession.gatt) queue.onCharacteristicWrite(characteristic, status)
                }
            }
            override fun onCharacteristicChanged(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
                value: ByteArray) {
                val snapshot = value.copyOf()
                handler.post { if (callbackGatt === this@DeviceSession.gatt) handleChanged(characteristic, snapshot) }
            }
            @Deprecated("API 32 callback")
            override fun onCharacteristicChanged(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val snapshot = characteristic.value?.copyOf() ?: ByteArray(0)
                handler.post { if (callbackGatt === this@DeviceSession.gatt) handleChanged(characteristic, snapshot) }
            }
        }

        private fun initializeServices() {
            updateStatus(InputConnectionStatus.INITIALIZING, "Reading capabilities and subscribing")
            val services = gatt?.services.orEmpty().filter { inputAdapters.adapterFor(it.uuid) != null }
            services.forEach { service ->
                val adapter = inputAdapters.adapterFor(service.uuid) ?: return@forEach
                service.characteristics.forEach { characteristic ->
                    when (characteristic.uuid) {
                        in adapter.readableCapabilityCharacteristics ->
                            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                                queue.enqueue(GattOperation.Read(characteristic) { success, value ->
                                    if (success && value != null) {
                                        capabilityBytes[service.uuid to characteristic.uuid] = value.copyOf()
                                    }
                                })
                            }
                        in adapter.measurementCharacteristics,
                        in adapter.statusCharacteristics -> subscribe(service.uuid, characteristic, indications = false)
                        in adapter.controlPointCharacteristics -> {
                            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                                subscribe(service.uuid, characteristic, indications = true)
                            } else if (characteristic.properties and (
                                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                                    ) == 0
                            ) {
                                failedSubscriptions += service.uuid to characteristic.uuid
                            }
                        }
                    }
                }
            }
            queue.enqueue(GattOperation.Barrier { success, _ -> if (success) markReady(services) })
        }

        private fun subscribe(serviceUuid: UUID, characteristic: BluetoothGattCharacteristic, indications: Boolean) {
            val supported = if (indications) characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            else characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            if (!supported) {
                failedSubscriptions += serviceUuid to characteristic.uuid
                return
            }
            queue.enqueue(GattOperation.Subscribe(characteristic, indications) { success, _ ->
                if (!success) {
                    failedSubscriptions += serviceUuid to characteristic.uuid
                    bridge.addLog("GATT", "Subscription failed: ${characteristic.uuid}")
                }
            })
        }

        private fun markReady(services: List<BluetoothGattService>) {
            val endpointServices = mutableMapOf<String, BluetoothGattService>()
            val endpoints = services.flatMap { service ->
                val adapter = inputAdapters.adapterFor(service.uuid) ?: return@flatMap emptyList()
                val standard = StandardService.fromUuid(service.uuid)
                val control = service.characteristics.firstOrNull {
                    it.uuid in adapter.controlPointCharacteristics &&
                        (service.uuid to it.uuid) !in failedSubscriptions
                }?.uuid
                service.characteristics.filter {
                    it.uuid in adapter.measurementCharacteristics &&
                        (service.uuid to it.uuid) !in failedSubscriptions
                }.map { measurement ->
                    val id = endpointId(device.address, service.uuid, measurement.uuid)
                    endpointIds += id
                    endpointServices[id] = service
                    SourceEndpoint(
                        id, device.address, knownName ?: bridge.devices.value.orEmpty()[device.address]?.identity?.name,
                        standard, measurement.uuid, adapter.supportedMetrics(measurement.uuid), control,
                        initialized = true, serviceName = adapter.serviceName(service.uuid)
                    )
                }
            }
            endpoints.forEach { endpoint ->
                val endpointService = endpointServices.getValue(endpoint.id)
                val serviceCapabilities = capabilityBytes.filterKeys { (serviceUuid, _) ->
                    serviceUuid == endpointService.uuid
                }
                capabilities[endpoint.id] = EndpointCapabilities(
                    featureBytes = endpoint.service?.let {
                        serviceCapabilities[endpointService.uuid to featureUuid(it)]?.copyOf()
                    },
                    ranges = serviceCapabilities.filterKeys { (_, characteristicUuid) -> characteristicUuid in setOf(
                        StandardBleUuids.FTMS_SUPPORTED_SPEED_RANGE, StandardBleUuids.FTMS_SUPPORTED_INCLINATION_RANGE,
                        StandardBleUuids.FTMS_SUPPORTED_RESISTANCE_RANGE, StandardBleUuids.FTMS_SUPPORTED_HEART_RATE_RANGE,
                        StandardBleUuids.FTMS_SUPPORTED_POWER_RANGE
                    ) }.mapKeys { it.key.second },
                    sensorLocation = serviceCapabilities[endpointService.uuid to StandardBleUuids.CPS_SENSOR_LOCATION]
                        ?.firstOrNull()?.toInt()?.and(0xFF),
                    bodySensorLocation = serviceCapabilities[endpointService.uuid to StandardBleUuids.BODY_SENSOR_LOCATION]
                        ?.firstOrNull()?.toInt()?.and(0xFF),
                    characteristicUuids = endpointService.characteristics.map { it.uuid }.toSet()
                )
            }
            val advertised = services.mapNotNull { StandardService.fromUuid(it.uuid) }.toSet()
            bridge.updateDevice(InputDeviceState(
                DeviceIdentity(device.address, knownName),
                bridge.devices.value.orEmpty()[device.address]?.rssi,
                InputConnectionStatus.READY, advertised, endpoints,
                "${endpoints.size} measurement endpoint${if (endpoints.size == 1) "" else "s"} ready"
            ))
            bridge.addLog("GATT", "${knownName ?: device.address} initialized (${endpoints.size} endpoints)")
            updateNotification()
        }

        private fun handleChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (queue.onCharacteristicChanged(characteristic, value)) return
            val service = characteristic.service ?: return
            val endpointId = endpointId(device.address, service.uuid, characteristic.uuid)
            val adapter = inputAdapters.adapterFor(service.uuid) ?: return
            if (characteristic.uuid in adapter.measurementCharacteristics) {
                val decoded = adapter.decode(characteristic.uuid, value, System.currentTimeMillis())
                if (decoded == null) {
                    bridge.addLog("UPSTREAM RX", "Rejected malformed ${characteristic.uuid}", value)
                    return
                }
                val normalized = derived.addDerived(endpointId, decoded)
                bridge.addLog("UPSTREAM RX", "${StandardService.fromUuid(service.uuid)?.shortName} ${characteristic.uuid}", value)
                bridge.accept(endpointId, normalized)
                bridge.addLog("DECODED", normalized.metrics.entries.joinToString { "${it.key.name}=${"%.2f".format(it.value)}" })
                resolveAndApplyRoutes()
            } else {
                bridge.addLog("UPSTREAM RX", "Status ${characteristic.uuid}", value)
            }
        }

        private fun onDisconnected(detail: String) {
            val currentStatus = bridge.devices.value.orEmpty()[device.address]?.status
            if (!isOpen && gatt == null && currentStatus in setOf(
                    InputConnectionStatus.RECONNECTING, InputConnectionStatus.DISCONNECTED
                )
            ) return
            val shouldReconnect = !userInitiatedClose && bridge.serviceRunning.value == true
            isOpen = false
            queue.clear()
            runCatching { gatt?.close() }
            gatt = null
            endpointIds.forEach { bridge.invalidateEndpoint(it); derived.clear(it) }
            updateStatus(if (shouldReconnect) InputConnectionStatus.RECONNECTING else InputConnectionStatus.DISCONNECTED, detail)
            resolveAndApplyRoutes()
            if (shouldReconnect) scheduleReconnect(device.address)
        }

        private fun updateStatus(status: InputConnectionStatus, detail: String) {
            val previous = bridge.devices.value.orEmpty()[device.address]
            bridge.updateDevice(InputDeviceState(
                DeviceIdentity(device.address, knownName ?: previous?.identity?.name),
                previous?.rssi, status, previous?.advertisedServices.orEmpty(), previous?.endpoints.orEmpty(), detail
            ))
            updateNotification()
        }
    }

    private fun scheduleReconnect(address: String) {
        val attempt = reconnectAttempts[address] ?: 0
        val delay = RECONNECT_DELAYS_MILLIS[minOf(attempt, RECONNECT_DELAYS_MILLIS.lastIndex)]
        reconnectAttempts[address] = attempt + 1
        reconnectRunnables.remove(address)?.let(handler::removeCallbacks)
        val runnable = Runnable {
            reconnectRunnables.remove(address)
            sessions.remove(address)?.close(userInitiated = true)
            connect(address)
        }
        reconnectRunnables[address] = runnable
        handler.postDelayed(runnable, delay)
        bridge.addLog("GATT", "Reconnect ${address} in ${delay / 1000}s")
    }

    private fun resolveAndApplyRoutes() {
        val samples = bridge.samples.value.orEmpty()
        val now = System.currentTimeMillis()
        bridge.routes.value.orEmpty().values.forEach { route ->
            val resolved = routing.resolve(route, requiredMetrics(route.outputKey), samples, now)
            bridge.setResolved(resolved)
            if (resolved.freshness == RouteFreshness.FRESH) {
                applyValues(route.outputKey, resolved.values.mapValues { it.value.value })
                if (resolved.values.isNotEmpty()) {
                    bridge.addLog("ROUTE", "${route.outputKey}: ${resolved.values.keys.joinToString { it.displayName }}")
                    ApplicationState.getInstance(applicationContext).publishBridgeOutput(route.outputKey)
                }
            }
        }
    }

    private fun applyValues(outputKey: String, values: Map<Metric, Double>) {
        fun value(metric: Metric) = values[metric]
        when (outputKey) {
            "csc" -> {
                value(Metric.SPEED_MPS)?.let { state.cscSpeedKmH.postValue(it * 3.6) }
                value(Metric.CADENCE_RPM)?.let { state.cscCadence.postValue(it.roundToInt()) }
            }
            "cycling_power" -> {
                value(Metric.SPEED_MPS)?.let { state.cyclingPowerSpeedKmH = it * 3.6 }
                value(Metric.CADENCE_RPM)?.let { state.cyclingPowerCadence = it.roundToInt() }
                value(Metric.POWER_WATTS)?.let { state.cyclingPowerWatts = it.roundToInt() }
                value(Metric.PEDAL_POWER_BALANCE_PERCENT)?.let {
                    state.cyclingPowerConfiguration.pedalPowerBalancePercent = it
                }
                value(Metric.ACCUMULATED_TORQUE_NM)?.let {
                    state.cyclingPowerConfiguration.accumulatedTorqueNm = it
                }
            }
            "rsc" -> {
                value(Metric.SPEED_MPS)?.let(state.runningSpeedAndCadenceData::postInstantaneousSpeed)
                value(Metric.CADENCE_RPM)?.let { state.runningSpeedAndCadenceData.postInstantaneousCadence(it.roundToInt()) }
                value(Metric.STRIDE_LENGTH_METERS)?.let(state.runningSpeedAndCadenceData::postInstantaneousStrideLength)
                value(Metric.WALKING_RUNNING_STATUS)?.let { state.rscConfiguration.runningStatus = it >= 0.5 }
            }
            "heart_rate" -> {
                value(Metric.HEART_RATE_BPM)?.let { state.heartRateConfiguration.heartRateBpm = it.roundToInt() }
                value(Metric.HEART_RATE_ENERGY_KJ)?.let { state.heartRateConfiguration.energyExpendedKj = it.roundToInt() }
                value(Metric.RR_INTERVAL_SECONDS)?.let { state.heartRateConfiguration.rrIntervalsSeconds = listOf(it) }
            }
            "ftms" -> applyFtms(values)
        }
    }

    private fun applyFtms(values: Map<Metric, Double>) {
        val speedKmh = values[Metric.SPEED_MPS]?.times(3.6)?.toFloat()
        val cadence = values[Metric.CADENCE_RPM]?.roundToInt()
        val power = values[Metric.POWER_WATTS]?.roundToInt()
        val hr = values[Metric.HEART_RATE_BPM]?.roundToInt()
        val resistance = values[Metric.RESISTANCE_LEVEL]?.roundToInt()
        val incline = values[Metric.INCLINATION_PERCENT]?.toFloat()
        val metabolicEquivalent = values[Metric.METABOLIC_EQUIVALENT]?.toFloat()
        state.ftmsIndoorBikeData.apply {
            speedKmh?.let { instantaneousSpeed = it }; cadence?.let { instantaneousCadence = it }
            power?.let { instantaneousPower = it }; hr?.let { heartRate = it }; resistance?.let { resistanceLevel = it }
            metabolicEquivalent?.let { this.metabolicEquivalent = it }
        }
        state.ftmsTreadmillData.apply {
            speedKmh?.let { instantaneousSpeed = it }; power?.let { instantaneousPower = it }
            hr?.let { heartRate = it }; incline?.let { inclinationPercent = it }
            metabolicEquivalent?.let { this.metabolicEquivalent = it }
        }
        state.ftmsCrossTrainerData.apply {
            speedKmh?.let { instantaneousSpeed = it }; power?.let { instantaneousPower = it }
            hr?.let { heartRate = it }; resistance?.let { resistanceLevel = it }
            incline?.let { inclinationPercent = it }
            values[Metric.STEP_RATE_PER_MINUTE]?.let { instantaneousStepsPerMinute = it.roundToInt() }
            values[Metric.MOVEMENT_DIRECTION]?.let { movementDirectionBackward = it < 0.0 }
            metabolicEquivalent?.let { this.metabolicEquivalent = it }
        }
        state.ftmsRowerData.apply {
            power?.let { instantaneousPower = it }; hr?.let { heartRate = it }
            resistance?.let { resistanceLevel = it }
            values[Metric.STROKE_RATE_PER_MINUTE]?.let { strokeRate = it.roundToInt() }
            metabolicEquivalent?.let { this.metabolicEquivalent = it }
        }
        state.ftmsStepClimberData.apply {
            hr?.let { heartRate = it }
            values[Metric.STEP_RATE_PER_MINUTE]?.let { stepsPerMinute = it.roundToInt() }
            metabolicEquivalent?.let { this.metabolicEquivalent = it }
        }
        state.ftmsStairClimberData.apply {
            hr?.let { heartRate = it }
            values[Metric.STEP_RATE_PER_MINUTE]?.let { stepsPerMinute = it.roundToInt() }
            metabolicEquivalent?.let { this.metabolicEquivalent = it }
        }
    }

    private fun requiredMetrics(outputKey: String): Set<Metric> = when (outputKey) {
        "csc" -> buildSet {
            if (state.cscConfiguration.includeWheelRevolutionData) add(Metric.SPEED_MPS)
            if (state.cscConfiguration.includeCrankRevolutionData) add(Metric.CADENCE_RPM)
        }
        "cycling_power" -> buildSet {
            add(Metric.POWER_WATTS)
            if (state.cyclingPowerConfiguration.includeWheelRevolutionData) add(Metric.SPEED_MPS)
            if (state.cyclingPowerConfiguration.includeCrankRevolutionData) add(Metric.CADENCE_RPM)
            if (state.cyclingPowerConfiguration.includePedalPowerBalance) add(Metric.PEDAL_POWER_BALANCE_PERCENT)
            if (state.cyclingPowerConfiguration.includeAccumulatedTorque) add(Metric.ACCUMULATED_TORQUE_NM)
        }
        "rsc" -> buildSet {
            add(Metric.SPEED_MPS); add(Metric.CADENCE_RPM)
            if (state.rscConfiguration.includeStrideLength) add(Metric.STRIDE_LENGTH_METERS)
            if (state.rscConfiguration.includeWalkingOrRunningStatus) add(Metric.WALKING_RUNNING_STATUS)
        }
        "heart_rate" -> buildSet {
            add(Metric.HEART_RATE_BPM)
            if (state.heartRateConfiguration.includeEnergyExpended) add(Metric.HEART_RATE_ENERGY_KJ)
            if (state.heartRateConfiguration.includeRrIntervals) add(Metric.RR_INTERVAL_SECONDS)
        }
        "ftms" -> ftmsRequiredMetrics()
        else -> emptySet()
    }

    private fun ftmsRequiredMetrics(): Set<Metric> = when (state.selectedFitnessMachineMode.value) {
        "treadmill" -> buildSet {
            add(Metric.SPEED_MPS)
            if (state.ftmsTreadmillData.includeInclination) add(Metric.INCLINATION_PERCENT)
            if (state.ftmsTreadmillData.includeForceOnBeltAndPowerOutput) add(Metric.POWER_WATTS)
            if (state.ftmsTreadmillData.includeHeartRate) add(Metric.HEART_RATE_BPM)
            if (state.ftmsTreadmillData.includeMetabolicEquivalent) add(Metric.METABOLIC_EQUIVALENT)
        }
        "cross_trainer" -> buildSet {
            add(Metric.SPEED_MPS)
            if (state.ftmsCrossTrainerData.includeStepCount || state.ftmsCrossTrainerData.includeStrideCount) {
                add(Metric.STEP_RATE_PER_MINUTE)
            }
            if (state.ftmsCrossTrainerData.includeInstantaneousPower) add(Metric.POWER_WATTS)
            if (state.ftmsCrossTrainerData.includeResistanceLevel) add(Metric.RESISTANCE_LEVEL)
            if (state.ftmsCrossTrainerData.includeHeartRate) add(Metric.HEART_RATE_BPM)
            if (state.ftmsCrossTrainerData.includeMetabolicEquivalent) add(Metric.METABOLIC_EQUIVALENT)
            add(Metric.MOVEMENT_DIRECTION)
        }
        "rower" -> buildSet {
            add(Metric.STROKE_RATE_PER_MINUTE)
            if (state.ftmsRowerData.includeInstantaneousPower) add(Metric.POWER_WATTS)
            if (state.ftmsRowerData.includeResistanceLevel) add(Metric.RESISTANCE_LEVEL)
            if (state.ftmsRowerData.includeHeartRate) add(Metric.HEART_RATE_BPM)
            if (state.ftmsRowerData.includeMetabolicEquivalent) add(Metric.METABOLIC_EQUIVALENT)
        }
        "step_climber" -> buildSet {
            add(Metric.STEP_RATE_PER_MINUTE)
            if (state.ftmsStepClimberData.includeHeartRate) add(Metric.HEART_RATE_BPM)
            if (state.ftmsStepClimberData.includeMetabolicEquivalent) add(Metric.METABOLIC_EQUIVALENT)
        }
        "stair_climber" -> buildSet {
            add(Metric.STEP_RATE_PER_MINUTE)
            if (state.ftmsStairClimberData.includeHeartRate) add(Metric.HEART_RATE_BPM)
            if (state.ftmsStairClimberData.includeMetabolicEquivalent) add(Metric.METABOLIC_EQUIVALENT)
        }
        else -> buildSet {
            add(Metric.SPEED_MPS)
            if (state.ftmsIndoorBikeData.includeInstantaneousCadence) add(Metric.CADENCE_RPM)
            if (state.ftmsIndoorBikeData.includeInstantaneousPower) add(Metric.POWER_WATTS)
            if (state.ftmsIndoorBikeData.includeResistanceLevel) add(Metric.RESISTANCE_LEVEL)
            if (state.ftmsIndoorBikeData.includeHeartRate) add(Metric.HEART_RATE_BPM)
            if (state.ftmsIndoorBikeData.includeMetabolicEquivalent) add(Metric.METABOLIC_EQUIVALENT)
        }
    }

    private fun ensureDefaultRoutes() {
        mapOf(
            "ftms" to StandardService.FTMS, "cycling_power" to StandardService.CYCLING_POWER,
            "csc" to StandardService.CSC, "rsc" to StandardService.RSC,
            "heart_rate" to StandardService.HEART_RATE
        ).forEach { (key, service) ->
            if (bridge.routes.value.orEmpty()[key] == null) bridge.setRoute(ServiceRoute(key, service))
            if (bridge.controlRoutes.value.orEmpty()[key] == null) bridge.setControlRoute(key, ControlRoute.LocalSimulator)
            if (bridge.capabilityModes.value.orEmpty()[key] == null) bridge.setCapabilityMode(key, CapabilityMode.SIMULATOR_CONFIGURATION)
        }
    }

    private fun persistRoute(route: ServiceRoute) {
        val json = JSONObject()
            .put("service", route.outputService.name)
            .put("default", encodeSource(route.defaultSource))
        val fields = JSONObject()
        route.fieldOverrides.forEach { (metric, source) -> fields.put(metric.name, encodeSource(source)) }
        json.put("fields", fields)
        prefs.edit().putString("bridge.route.${route.outputKey}", json.toString()).apply()
    }

    private fun restoreRoutes() {
        listOf("ftms", "cycling_power", "csc", "rsc", "heart_rate").forEach { key ->
            prefs.getString("bridge.route.$key", null)?.let { encoded ->
                runCatching {
                    val json = JSONObject(encoded)
                    val fieldsJson = json.optJSONObject("fields") ?: JSONObject()
                    val fields = fieldsJson.keys().asSequence().mapNotNull { name ->
                        runCatching { Metric.valueOf(name) to decodeSource(fieldsJson.getString(name)) }.getOrNull()
                    }.toMap()
                    bridge.setRoute(ServiceRoute(key, StandardService.valueOf(json.getString("service")),
                        decodeSource(json.getString("default")), fields))
                }
            }
            prefs.getString("bridge.control.$key", null)?.let { encoded ->
                bridge.setControlRoute(key, if (encoded == "local") ControlRoute.LocalSimulator
                else ControlRoute.Remote(encoded.removePrefix("remote:")))
            }
            prefs.getString("bridge.capability.$key", null)?.let { encoded ->
                runCatching { bridge.setCapabilityMode(key, CapabilityMode.valueOf(encoded)) }
            }
        }
    }

    private fun restoreKnownDevices() {
        prefs.getStringSet(PREF_KNOWN_DEVICES, emptySet()).orEmpty().forEach { address ->
            bridge.updateDevice(InputDeviceState(
                DeviceIdentity(address, prefs.getString("bridge.device.$address.name", null)),
                status = InputConnectionStatus.DISCONNECTED,
                detail = "Known device · tap Connect to start after this cold launch"
            ))
        }
    }

    private fun encodeSource(source: RouteSource): String = when (source) {
        RouteSource.Manual -> "manual"
        RouteSource.Inherit -> "inherit"
        is RouteSource.Endpoint -> "endpoint:${source.endpointId}"
    }

    private fun decodeSource(encoded: String): RouteSource = when (encoded) {
        "manual" -> RouteSource.Manual
        "inherit" -> RouteSource.Inherit
        else -> RouteSource.Endpoint(encoded.removePrefix("endpoint:"))
    }

    private fun endpointId(address: String, service: UUID, characteristic: UUID) = "$address|$service|$characteristic"

    private fun featureUuid(service: StandardService): UUID = when (service) {
        StandardService.FTMS -> StandardBleUuids.FTMS_FEATURE
        StandardService.CYCLING_POWER -> StandardBleUuids.CPS_FEATURE
        StandardService.CSC -> StandardBleUuids.CSC_FEATURE
        StandardService.RSC -> StandardBleUuids.RSC_FEATURE
        StandardService.HEART_RATE -> StandardBleUuids.BODY_SENSOR_LOCATION
    }

    private fun hasBluetoothPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun stopBridge() {
        ApplicationState.getInstance(applicationContext).stopYouGattMeServer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "BLE bridge", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Keeps connected fitness devices and simulated BLE services active" })
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, BleBridgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val ready = bridge.devices.value.orEmpty().values.count { it.status == InputConnectionStatus.READY }
        val routed = bridge.routes.value.orEmpty().values.count { it.defaultSource is RouteSource.Endpoint }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_nfc)
            .setContentTitle("You GATT Me BLE bridge")
            .setContentText("$ready input device${if (ready == 1) "" else "s"} · $routed routed service${if (routed == 1) "" else "s"}")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, "Stop", stop).build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        const val ACTION_START = "de.tobiga.yougattme.bridge.START"
        const val ACTION_STOP = "de.tobiga.yougattme.bridge.STOP"
        private const val CHANNEL_ID = "ble_bridge"
        private const val NOTIFICATION_ID = 1842
        private const val SCAN_WINDOW_MILLIS = 15_000L
        private const val PREF_KNOWN_DEVICES = "bridge.known_devices"
        private val RECONNECT_DELAYS_MILLIS = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BleBridgeService::class.java).setAction(ACTION_START))
        }
    }
}
