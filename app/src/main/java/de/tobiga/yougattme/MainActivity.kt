package de.tobiga.yougattme

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseExpandableListAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.button.MaterialButton
import de.tobiga.yougattme.Constants.GATT_SERVER_ACTIVATED
import de.tobiga.yougattme.databinding.ActivityMainBinding
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.common.SensorLocationValues
import de.tobiga.yougattme.gatt.common.SensorMeasurementSnapshot
import de.tobiga.yougattme.gatt.common.SensorProtocolEvent
import de.tobiga.yougattme.gatt.csc.CscControlPointOpCodes
import de.tobiga.yougattme.gatt.csc.CscFeatureBits
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerControlPointOpCodes
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerFeatureBits
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsTargetState
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsMeasurementSnapshot
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsProtocolEvent
import de.tobiga.yougattme.gatt.fitnessmachine.summary
import de.tobiga.yougattme.gatt.provider.GattProfileDefinition
import de.tobiga.yougattme.gatt.provider.GattProfileProvider
import de.tobiga.yougattme.gatt.provider.GattProfileProviderLoader
import de.tobiga.yougattme.gatt.provider.ProfileDependencies
import de.tobiga.yougattme.gatt.provider.ProfileSelectionMode
import de.tobiga.yougattme.gatt.provider.StandardGattProfileProvider
import de.tobiga.yougattme.gatt.rsc.RscFeatureBits
import de.tobiga.yougattme.gatt.rsc.ScControlPointOpCodes
import de.tobiga.yougattme.bridge.BleBridgeService
import de.tobiga.yougattme.bridge.CapabilityMode
import de.tobiga.yougattme.bridge.ControlRoute
import de.tobiga.yougattme.bridge.InputConnectionStatus
import de.tobiga.yougattme.bridge.Metric
import de.tobiga.yougattme.bridge.RouteFreshness
import de.tobiga.yougattme.bridge.RouteSource
import de.tobiga.yougattme.bridge.ServiceRoute
import de.tobiga.yougattme.bridge.SourceEndpoint
import de.tobiga.yougattme.bridge.StandardBleUuids
import de.tobiga.yougattme.bridge.StandardService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

const val TERMS_AND_CONDITIONS_READ_VERSION = "TERMS_AND_CONDITIONS_READ_VERSION"
const val CURRENT_VERSION_TERMS_AND_CONDITIONS = 4

class MainActivity : AppCompatActivity() {

    private enum class FieldToggleGroup {
        SPEED,
        CADENCE,
        POWER,
        RESISTANCE,
        INCLINATION,
        HEART_RATE,
        METABOLIC,
        DURATION
    }

    private data class FieldToggle(
        val group: FieldToggleGroup,
        val label: String,
        val checked: () -> Boolean,
        val update: (Boolean) -> Unit,
        val requiresGattRebuild: Boolean = false,
        val labelPrefix: String = "Feature/data",
        val enabled: () -> Boolean = { true }
    )

    private data class RenderedFieldToggle(
        val switchView: SwitchMaterial,
        val toggle: FieldToggle
    )

    private data class FtmsMachineOption(
        val profileKey: String,
        val label: String
    )

    private data class ExclusiveProfileGroup(
        val groupKey: String,
        val profiles: List<GattProfileDefinition>
    )

    private data class ServiceProfileRow(
        val key: String,
        val title: String,
        val subtitle: String,
        val contentView: View,
        val isEnabled: () -> Boolean,
        val summary: () -> String,
        val setEnabled: (Boolean) -> Unit,
        val toggleVisible: Boolean = true
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var applicationState: ApplicationState
    private lateinit var serviceProfileAdapter: ServiceProfileAdapter
    private var bridgeService: BleBridgeService? = null
    private var bridgeBound = false
    private val bridgeConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bridgeService = (service as? BleBridgeService.LocalBinder)?.service
            bridgeBound = bridgeService != null
            synchronizePersistedCapabilityMirrors()
            renderBridgeUi()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bridgeService = null
            bridgeBound = false
        }
    }
    private val viewModel: ApplicationViewModel by viewModels()
    private val logLines = ArrayDeque<String>()
    private val renderedFieldToggles = mutableListOf<RenderedFieldToggle>()
    private val bridgeRouteStatusViews = mutableMapOf<String, TextView>()
    private val appliedCapabilityMirrors = mutableMapOf<String, String>()
    private var ftmsEnabled = true
    private var selectedFtmsProfileKey = "ftms_bike"
    private val selectedOptionalServiceKeys = linkedSetOf("csc", "cycling_power")
    private val selectedExclusiveProfileKeys = mutableMapOf<String, String>()
    private var suppressRuntimeSwitchCallback = false
    private var suppressFieldToggleCallback = false
    private var gattRuntimeStatus = GattRuntimeStatus("Disabled")
    private var renderedMachineFieldKey: String? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private val indoorBikeSummaryRefresh = object : Runnable {
        override fun run() {
            if (::binding.isInitialized) {
                updateIndoorBikeComputedSummary()
                uiHandler.postDelayed(this, 1000L)
            }
        }
    }

    private val profileProviders: List<GattProfileProvider> by lazy {
        listOf(StandardGattProfileProvider()) + GattProfileProviderLoader.loadFromManifest(this)
    }

    private val profiles by lazy {
        profileProviders
            .flatMap { provider -> provider.profiles }
            .also { definitions ->
                require(definitions.map { it.key }.toSet().size == definitions.size) {
                    "Duplicate GATT profile provider keys are not allowed"
                }
            }
            .associateBy { definition -> definition.key }
    }

    private val profileProviderByKey by lazy {
        profileProviders
            .flatMap { provider ->
                provider.profiles.map { definition -> definition.key to provider }
            }
            .toMap()
    }

    private val ftmsMachineOptions = listOf(
        FtmsMachineOption("ftms_bike", "Indoor Bike"),
        FtmsMachineOption("ftms_rower", "Rower"),
        FtmsMachineOption("ftms_treadmill", "Treadmill"),
        FtmsMachineOption("ftms_cross", "Cross Trainer"),
        FtmsMachineOption("ftms_step", "Step Climber"),
        FtmsMachineOption("ftms_stair", "Stair Climber")
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (BLUETOOTH_PERMISSIONS.all { permission -> permissions[permission] == true }) {
                initializeApplicationState()
                appendLog("Runtime permissions granted")
                if (permissions[Manifest.permission.POST_NOTIFICATIONS] == false) {
                    appendLog("Notification permission denied; bridge remains available")
                }
            } else {
                appendLog("Runtime permissions denied")
                Toast.makeText(this, "Bluetooth permissions are required for GATT simulation.", Toast.LENGTH_SHORT).show()
            }
            updateRuntimeState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getDefaultSharedPreferences(application)
        cameraExecutor = Executors.newSingleThreadExecutor()

        styleSimulatorSwitch(binding.switchHeartRate)
        styleSimulatorSwitch(binding.switchFtmsControlPoint)
        setupRuntimeActions()
        setupBridgeActions()
        setupProfileActions()
        setupSimulationControls()
        setupSensorProtocolControls()
        setupHeartRateControls()
        setupDeviceStateObservers()
        updateDeviceStatePanel()
        updateProfileButtons()
        updateSimulationValuesFromControls(updateFtmsDuration = true)
        renderMachineFieldControls()
        updateRuntimeState()
        appendLog("Console initialized")
        uiHandler.post(indoorBikeSummaryRefresh)

        if (allPermissionsGranted()) {
            initializeApplicationState()
        } else {
            requestPermissionLauncher.launch(REQUESTED_PERMISSIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        updateRuntimeState()
        if (!hasReadTermsAndConditions()) {
            showTermsAndConditions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::applicationState.isInitialized) {
            applicationState.setRuntimeStatusListener(null)
        }
        uiHandler.removeCallbacks(indoorBikeSummaryRefresh)
        cameraExecutor.shutdown()
        if (bridgeBound) {
            unbindService(bridgeConnection)
            bridgeBound = false
        }
    }

    private fun initializeApplicationState() {
        val wasInitialized = ::applicationState.isInitialized
        applicationState = ApplicationState.getInstance(this)
        applicationState.mainViewModel = viewModel
        BleBridgeService.start(this)
        bindBridgeService()
        applicationState.setRuntimeStatusListener { status ->
            runOnUiThread {
                gattRuntimeStatus = status
                updateRuntimeState()
            }
        }
        applySelectedServiceStack(rebuildGatt = wasInitialized, logChange = false)
        if (!wasInitialized) {
            applicationState.initializeFromMainActivity()
        }
        updateRuntimeState()
    }

    private fun setupRuntimeActions() {
        binding.switchGattServer.setOnCheckedChangeListener { _, isChecked ->
            if (suppressRuntimeSwitchCallback) {
                return@setOnCheckedChangeListener
            }
            if (!::applicationState.isInitialized) {
                initializeApplicationState()
            }

            prefs.edit().putBoolean(GATT_SERVER_ACTIVATED, isChecked).apply()
            if (isChecked) {
                applicationState.ensureYouGattMeServerIsRunning()
                appendLog("GATT server enabled")
            } else {
                applicationState.stopYouGattMeServer()
                appendLog("GATT server disabled")
            }
            updateRuntimeState()
        }

        binding.buttonBluetoothSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            appendLog("Opened Bluetooth settings")
        }
    }

    private fun bindBridgeService() {
        if (bridgeBound) return
        bindService(Intent(this, BleBridgeService::class.java), bridgeConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupBridgeActions() {
        binding.headerInputDevices.setOnClickListener {
            val open = binding.contentInputDevices.visibility != View.VISIBLE
            binding.contentInputDevices.visibility = if (open) View.VISIBLE else View.GONE
            binding.iconInputDevicesExpand.setImageResource(
                if (open) R.drawable.ic_accordion_expand_more else R.drawable.ic_accordion_chevron_right
            )
        }
        binding.buttonScanInputs.setOnClickListener {
            bridgeService?.startScan(binding.switchShowAllBleDevices.isChecked)
                ?: Toast.makeText(this, "Bridge service is still starting", Toast.LENGTH_SHORT).show()
        }
        binding.buttonStopInputScan.setOnClickListener { bridgeService?.stopScan() }
        binding.switchShowAllBleDevices.setOnCheckedChangeListener { _, enabled ->
            viewModel.bridgeState.setShowAllDevices(enabled)
        }

        val bridgeState = viewModel.bridgeState
        bridgeState.devices.observe(this) {
            synchronizePersistedCapabilityMirrors()
            renderBridgeUi()
        }
        bridgeState.routes.observe(this) {
            synchronizePersistedCapabilityMirrors()
            renderRouteControls()
            updateSliderRoutingState()
        }
        bridgeState.controlRoutes.observe(this) { renderRouteControls() }
        bridgeState.capabilityModes.observe(this) { renderRouteControls() }
        bridgeState.resolvedRoutes.observe(this) {
            updateBridgeRouteStatuses()
            updateSliderRoutingState()
            updateRoutedValueLabels()
        }
        bridgeState.scanning.observe(this) { scanning ->
            binding.buttonScanInputs.isEnabled = !scanning
            binding.buttonStopInputScan.isEnabled = scanning
            renderBridgeSummary()
        }
        bridgeState.log.observe(this) { entries ->
            binding.textBridgeLog.text = entries.take(30).joinToString("\n") { entry ->
                val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(entry.timestampMillis))
                "$time ${entry.category.padEnd(10)} ${entry.message}${entry.payloadHex?.let { " · $it" } ?: ""}"
            }.ifEmpty { "No bridge traffic yet." }
        }
        renderBridgeUi()
    }

    private fun renderBridgeUi() {
        renderBridgeSummary()
        renderInputDevices()
        renderRouteControls()
        updateSliderRoutingState()
    }

    private fun renderBridgeSummary() {
        if (!::binding.isInitialized) return
        val devices = viewModel.bridgeState.devices.value.orEmpty().values
        val ready = devices.count { it.status == InputConnectionStatus.READY }
        val routed = viewModel.bridgeState.routes.value.orEmpty().values.count { route ->
            route.defaultSource is RouteSource.Endpoint || route.fieldOverrides.values.any { it is RouteSource.Endpoint }
        }
        binding.textBridgeSummary.text = when {
            viewModel.bridgeState.scanning.value == true -> "Scanning · $ready connected · $routed routed"
            else -> "$ready connected input${if (ready == 1) "" else "s"} · $routed routed output${if (routed == 1) "" else "s"}"
        }
    }

    private fun renderInputDevices() {
        if (!::binding.isInitialized) return
        val container = binding.layoutInputDeviceRows
        container.removeAllViews()
        val devices = viewModel.bridgeState.devices.value.orEmpty().values.sortedBy { it.identity.name ?: it.identity.address }
        if (devices.isEmpty()) {
            container.addView(bridgeText("No devices discovered yet. Start a scan above.", muted = true))
            return
        }
        devices.forEach { device ->
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundResource(R.drawable.simulator_toggle_group)
            }
            val title = device.identity.name?.takeIf(String::isNotBlank) ?: "Unnamed BLE device"
            panel.addView(bridgeText(title, bold = true))
            panel.addView(bridgeText(
                "${device.identity.address} · ${device.rssi?.let { "$it dBm · " } ?: ""}${device.status.name.lowercase().replaceFirstChar(Char::uppercase)}",
                muted = true
            ))
            device.detail?.let { panel.addView(bridgeText(it, muted = true)) }
            if (device.endpoints.isNotEmpty()) {
                panel.addView(bridgeText(device.endpoints.joinToString("\n") { "• ${it.serviceName} · ${it.supportedMetrics.joinToString { metric -> metric.displayName }}" }, muted = true))
            } else if (device.advertisedServices.isNotEmpty()) {
                panel.addView(bridgeText("Advertises ${device.advertisedServices.joinToString { it.shortName }}", muted = true))
            }
            val button = MaterialButton(this).apply {
                isAllCaps = false
                text = if (device.status in setOf(InputConnectionStatus.CONNECTING, InputConnectionStatus.INITIALIZING,
                        InputConnectionStatus.READY, InputConnectionStatus.RECONNECTING)) "Disconnect" else "Connect"
                setOnClickListener {
                    if (device.status in setOf(InputConnectionStatus.CONNECTING, InputConnectionStatus.INITIALIZING,
                            InputConnectionStatus.READY, InputConnectionStatus.RECONNECTING)) {
                        bridgeService?.disconnect(device.identity.address)
                    } else bridgeService?.connect(device.identity.address)
                }
            }
            panel.addView(button)
            container.addView(panel, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
        }
    }

    private fun renderRouteControls() {
        if (!::binding.isInitialized) return
        val container = binding.layoutBridgeRoutes
        container.removeAllViews()
        bridgeRouteStatusViews.clear()
        routeDefinitions().forEach { (key, title, service) ->
            val route = viewModel.bridgeState.routes.value.orEmpty()[key] ?: ServiceRoute(key, service)
            container.addView(createRoutePanel(key, title, route), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) })
        }
    }

    private fun createRoutePanel(outputKey: String, title: String, route: ServiceRoute): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundResource(R.drawable.simulator_toggle_group)
        }
        panel.addView(bridgeText(title, bold = true))
        val resolved = viewModel.bridgeState.resolvedRoutes.value.orEmpty()[outputKey]
        val status = when (resolved?.freshness) {
            RouteFreshness.FRESH -> "Fresh"
            RouteFreshness.STALE -> "Stale · ${resolved.staleMetrics.joinToString { it.displayName }}"
            RouteFreshness.INCOMPLETE -> "Incomplete · missing ${resolved.missingMetrics.joinToString { it.displayName }}"
            null -> "Manual"
        }
        val statusView = bridgeText(status, muted = resolved?.freshness != RouteFreshness.FRESH)
        bridgeRouteStatusViews[outputKey] = statusView
        panel.addView(statusView)

        panel.addView(bridgeText("Measurement source", muted = true))
        val endpoints = readyEndpoints().filter { endpoint ->
            endpoint.supportedMetrics.any { it in routeMetrics(outputKey) }
        }
        val sourceChoices = listOf("Manual sliders" to RouteSource.Manual) +
            endpoints.map { it.label to RouteSource.Endpoint(it.id) }
        panel.addView(sourceSpinner(sourceChoices, route.defaultSource) { source ->
            restoreManualBridgeValues()
            if (viewModel.bridgeState.capabilityModes.value.orEmpty()[outputKey] == CapabilityMode.MIRROR_CONNECTED_SERVICE) {
                val selectedEndpoint = (source as? RouteSource.Endpoint)?.let { selected ->
                    readyEndpoints().firstOrNull { it.id == selected.endpointId }
                }
                if (selectedEndpoint?.service != route.outputService) {
                    bridgeService?.setCapabilityMode(outputKey, CapabilityMode.SIMULATOR_CONFIGURATION)
                        ?: viewModel.bridgeState.setCapabilityMode(outputKey, CapabilityMode.SIMULATOR_CONFIGURATION)
                    appliedCapabilityMirrors.remove(outputKey)
                }
            }
            bridgeService?.setRoute(route.copy(defaultSource = source))
                ?: viewModel.bridgeState.setRoute(route.copy(defaultSource = source))
        })

        val overridesButton = MaterialButton(this).apply {
            text = "Field routing overrides"
            isAllCaps = false
        }
        val overrides = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        overridesButton.setOnClickListener {
            overrides.visibility = if (overrides.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        panel.addView(overridesButton)
        routeMetrics(outputKey).forEach { metric ->
            overrides.addView(bridgeText(metric.displayName, muted = true))
            val choices = listOf("Inherit service source" to RouteSource.Inherit, "Manual slider/value" to RouteSource.Manual) +
                readyEndpoints().filter { metric in it.supportedMetrics }.map { it.label to RouteSource.Endpoint(it.id) }
            overrides.addView(sourceSpinner(choices, route.fieldOverrides[metric] ?: RouteSource.Inherit) { source ->
                restoreManualBridgeValues()
                val fields = route.fieldOverrides.toMutableMap()
                if (source == RouteSource.Inherit) fields.remove(metric) else fields[metric] = source
                bridgeService?.setRoute(route.copy(fieldOverrides = fields))
                    ?: viewModel.bridgeState.setRoute(route.copy(fieldOverrides = fields))
            })
        }
        panel.addView(overrides)

        panel.addView(bridgeText("Control Point destination", muted = true))
        val remoteControls = readyEndpoints().filter { it.service == route.outputService && it.controlPointCharacteristic != null }
        val controlChoices = listOf("Local simulator" to ControlRoute.LocalSimulator) +
            remoteControls.map { it.label to ControlRoute.Remote(it.id) }
        val activeControl = viewModel.bridgeState.controlRoutes.value.orEmpty()[outputKey] ?: ControlRoute.LocalSimulator
        panel.addView(controlSpinner(outputKey, controlChoices, activeControl))

        panel.addView(bridgeText("Capability mode", muted = true))
        val modes = listOf("Simulator configuration", "Mirror same-protocol source")
        val capabilitySpinner = Spinner(this)
        capabilitySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        val activeMode = viewModel.bridgeState.capabilityModes.value.orEmpty()[outputKey] ?: CapabilityMode.SIMULATOR_CONFIGURATION
        capabilitySpinner.setSelection(if (activeMode == CapabilityMode.MIRROR_CONNECTED_SERVICE) 1 else 0, false)
        capabilitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var initial = true
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initial) { initial = false; return }
                val mode = if (position == 1) CapabilityMode.MIRROR_CONNECTED_SERVICE else CapabilityMode.SIMULATOR_CONFIGURATION
                var mirroredEndpoint: SourceEndpoint? = null
                if (mode == CapabilityMode.MIRROR_CONNECTED_SERVICE) {
                    mirroredEndpoint = (route.defaultSource as? RouteSource.Endpoint)?.let { source ->
                        readyEndpoints().firstOrNull { it.id == source.endpointId }
                    }
                    if (mirroredEndpoint?.service != route.outputService) {
                        Toast.makeText(this@MainActivity, "Capability mirroring requires a same-protocol measurement source.", Toast.LENGTH_LONG).show()
                        capabilitySpinner.setSelection(0, false)
                        return
                    }
                    if (route.outputService == StandardService.FTMS) {
                        selectMirroredFtmsMachine(mirroredEndpoint)
                    }
                }
                val applied = bridgeService?.setCapabilityMode(outputKey, mode) ?: run {
                    viewModel.bridgeState.setCapabilityMode(outputKey, mode)
                    true
                }
                if (!applied) {
                    Toast.makeText(this@MainActivity, "The connected service did not expose readable capabilities.", Toast.LENGTH_LONG).show()
                    capabilitySpinner.setSelection(0, false)
                    return
                }
                if (mode == CapabilityMode.MIRROR_CONNECTED_SERVICE && mirroredEndpoint != null) {
                    appliedCapabilityMirrors[outputKey] = mirroredEndpoint.id
                } else {
                    appliedCapabilityMirrors.remove(outputKey)
                }
                if (::applicationState.isInitialized) applySelectedServiceStack(rebuildGatt = true, logChange = true)
            }
        }
        panel.addView(capabilitySpinner)
        return panel
    }

    private fun synchronizePersistedCapabilityMirrors() {
        val service = bridgeService ?: return
        val ready = readyEndpoints().associateBy(SourceEndpoint::id)
        appliedCapabilityMirrors.entries.removeAll { (_, endpointId) -> endpointId !in ready }
        var rebuildGatt = false
        viewModel.bridgeState.capabilityModes.value.orEmpty().forEach { (outputKey, mode) ->
            if (mode != CapabilityMode.MIRROR_CONNECTED_SERVICE) return@forEach
            val route = viewModel.bridgeState.routes.value.orEmpty()[outputKey] ?: return@forEach
            val endpointId = (route.defaultSource as? RouteSource.Endpoint)?.endpointId ?: return@forEach
            val endpoint = ready[endpointId] ?: return@forEach
            if (endpoint.service != route.outputService || appliedCapabilityMirrors[outputKey] == endpointId) return@forEach
            if (route.outputService == StandardService.FTMS) selectMirroredFtmsMachine(endpoint)
            if (service.setCapabilityMode(outputKey, mode)) {
                appliedCapabilityMirrors[outputKey] = endpointId
                rebuildGatt = true
            }
        }
        if (rebuildGatt && ::applicationState.isInitialized) {
            applySelectedServiceStack(rebuildGatt = true, logChange = true)
        }
    }

    private fun selectMirroredFtmsMachine(endpoint: SourceEndpoint?) {
        val profileKey = when (endpoint?.measurementCharacteristic) {
            StandardBleUuids.FTMS_INDOOR_BIKE_DATA -> "ftms_bike"
            StandardBleUuids.FTMS_TREADMILL_DATA -> "ftms_treadmill"
            StandardBleUuids.FTMS_CROSS_TRAINER_DATA -> "ftms_cross"
            StandardBleUuids.FTMS_STEP_CLIMBER_DATA -> "ftms_step"
            StandardBleUuids.FTMS_STAIR_CLIMBER_DATA -> "ftms_stair"
            StandardBleUuids.FTMS_ROWER_DATA -> "ftms_rower"
            else -> return
        }
        if (selectedFtmsProfileKey == profileKey) return
        selectedFtmsProfileKey = profileKey
        renderedMachineFieldKey = null
        binding.spinnerFtmsMachine.setSelection(
            ftmsMachineOptions.indexOfFirst { it.profileKey == profileKey }.coerceAtLeast(0),
            false
        )
    }

    private fun updateBridgeRouteStatuses() {
        bridgeRouteStatusViews.forEach { (key, view) ->
            val resolved = viewModel.bridgeState.resolvedRoutes.value.orEmpty()[key]
            view.text = when (resolved?.freshness) {
                RouteFreshness.FRESH -> "Fresh"
                RouteFreshness.STALE -> "Stale · ${resolved.staleMetrics.joinToString { it.displayName }}"
                RouteFreshness.INCOMPLETE -> "Incomplete · missing ${resolved.missingMetrics.joinToString { it.displayName }}"
                null -> "Manual"
            }
            view.setTextColor(ContextCompat.getColor(this,
                if (resolved?.freshness == RouteFreshness.FRESH) R.color.simulator_ink else R.color.simulator_ink_muted))
        }
    }

    private fun sourceSpinner(
        choices: List<Pair<String, RouteSource>>,
        selected: RouteSource,
        changed: (RouteSource) -> Unit
    ): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, choices.map { it.first })
        val selectedIndex = choices.indexOfFirst { it.second == selected }.coerceAtLeast(0)
        setSelection(selectedIndex, false)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var initial = true
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initial) { initial = false; return }
                choices.getOrNull(position)?.second?.let(changed)
            }
        }
    }

    private fun controlSpinner(
        outputKey: String,
        choices: List<Pair<String, ControlRoute>>,
        selected: ControlRoute
    ): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, choices.map { it.first })
        setSelection(choices.indexOfFirst { it.second == selected }.coerceAtLeast(0), false)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var initial = true
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initial) { initial = false; return }
                val route = choices.getOrNull(position)?.second ?: return
                if (route is ControlRoute.Remote) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Enable physical trainer control?")
                        .setMessage("Downstream Control Point commands may change resistance, inclination or power on the connected device. Measurement routing alone never enables this.")
                        .setNegativeButton("Cancel") { _, _ ->
                            setSelection(choices.indexOfFirst { it.second == selected }.coerceAtLeast(0), false)
                        }
                        .setPositiveButton("Enable control") { _, _ -> bridgeService?.setControlRoute(outputKey, route) }
                        .show()
                } else bridgeService?.setControlRoute(outputKey, route)
            }
        }
    }

    private fun readyEndpoints(): List<SourceEndpoint> = viewModel.bridgeState.devices.value.orEmpty().values
        .filter { it.status == InputConnectionStatus.READY }
        .flatMap { it.endpoints }

    private fun routeDefinitions() = listOf(
        Triple("ftms", "FTMS output", StandardService.FTMS),
        Triple("cycling_power", "Cycling Power output", StandardService.CYCLING_POWER),
        Triple("csc", "CSC output", StandardService.CSC),
        Triple("rsc", "RSC output", StandardService.RSC),
        Triple("heart_rate", "Heart Rate output", StandardService.HEART_RATE)
    )

    private fun routeMetrics(outputKey: String): List<Metric> = when (outputKey) {
        "ftms" -> listOf(Metric.SPEED_MPS, Metric.CADENCE_RPM, Metric.POWER_WATTS,
            Metric.HEART_RATE_BPM, Metric.RESISTANCE_LEVEL, Metric.INCLINATION_PERCENT,
            Metric.STEP_RATE_PER_MINUTE, Metric.STROKE_RATE_PER_MINUTE,
            Metric.METABOLIC_EQUIVALENT, Metric.MOVEMENT_DIRECTION)
        "cycling_power" -> listOf(Metric.POWER_WATTS, Metric.SPEED_MPS, Metric.CADENCE_RPM,
            Metric.PEDAL_POWER_BALANCE_PERCENT, Metric.ACCUMULATED_TORQUE_NM)
        "csc" -> listOf(Metric.SPEED_MPS, Metric.CADENCE_RPM)
        "rsc" -> listOf(Metric.SPEED_MPS, Metric.CADENCE_RPM, Metric.STRIDE_LENGTH_METERS,
            Metric.WALKING_RUNNING_STATUS)
        "heart_rate" -> listOf(Metric.HEART_RATE_BPM, Metric.HEART_RATE_ENERGY_KJ, Metric.RR_INTERVAL_SECONDS)
        else -> emptyList()
    }

    private fun isDeviceRouted(outputKey: String, metric: Metric): Boolean {
        val route = viewModel.bridgeState.routes.value.orEmpty()[outputKey] ?: return false
        val field = route.fieldOverrides[metric] ?: RouteSource.Inherit
        val source = if (field == RouteSource.Inherit) route.defaultSource else field
        return source is RouteSource.Endpoint
    }

    private fun restoreManualBridgeValues() {
        updateSimulationValuesFromControls()
        viewModel.heartRateConfiguration.apply {
            heartRateBpm = binding.sliderHeartRateService.value.roundToInt()
            energyExpendedKj = binding.sliderHeartRateEnergy.value.roundToInt()
            rrIntervalsSeconds = listOf(binding.sliderHeartRateRr.value.toDouble())
        }
    }

    private fun updateSliderRoutingState() {
        if (!::binding.isInitialized) return
        binding.sliderSpeed.isEnabled = !isDeviceRouted("ftms", Metric.SPEED_MPS)
        binding.sliderCadence.isEnabled = !isDeviceRouted("ftms", Metric.CADENCE_RPM)
        binding.sliderPower.isEnabled = !isDeviceRouted("ftms", Metric.POWER_WATTS)
        binding.sliderHeartRate.isEnabled = !isDeviceRouted("ftms", Metric.HEART_RATE_BPM)
        binding.sliderResistance.isEnabled = !isDeviceRouted("ftms", Metric.RESISTANCE_LEVEL)
        binding.sliderInclination.isEnabled = !isDeviceRouted("ftms", Metric.INCLINATION_PERCENT)
        binding.sliderCscSpeed.isEnabled = !isDeviceRouted("csc", Metric.SPEED_MPS)
        binding.sliderCscCadence.isEnabled = !isDeviceRouted("csc", Metric.CADENCE_RPM)
        binding.sliderCyclingPower.isEnabled = !isDeviceRouted("cycling_power", Metric.POWER_WATTS)
        binding.sliderCyclingPowerSpeed.isEnabled = !isDeviceRouted("cycling_power", Metric.SPEED_MPS)
        binding.sliderCyclingPowerCadence.isEnabled = !isDeviceRouted("cycling_power", Metric.CADENCE_RPM)
        binding.sliderRscSpeed.isEnabled = !isDeviceRouted("rsc", Metric.SPEED_MPS)
        binding.sliderRscCadence.isEnabled = !isDeviceRouted("rsc", Metric.CADENCE_RPM)
        binding.sliderHeartRateService.isEnabled = !isDeviceRouted("heart_rate", Metric.HEART_RATE_BPM)
        binding.sliderHeartRateEnergy.isEnabled = !isDeviceRouted("heart_rate", Metric.HEART_RATE_ENERGY_KJ)
        binding.sliderHeartRateRr.isEnabled = !isDeviceRouted("heart_rate", Metric.RR_INTERVAL_SECONDS)
    }

    private fun updateRoutedValueLabels() {
        fun value(key: String, metric: Metric): Pair<Double, String>? {
            val sample = viewModel.bridgeState.resolvedRoutes.value.orEmpty()[key]?.values?.get(metric) ?: return null
            val source = viewModel.bridgeState.endpoint(sample.endpointId)?.label ?: sample.endpointId
            return sample.value to source
        }
        value("ftms", Metric.SPEED_MPS)?.let { (v, source) -> binding.labelSpeedValue.text = "Speed ${formatOne((v * 3.6).toFloat())} km/h · $source" }
        value("ftms", Metric.CADENCE_RPM)?.let { (v, source) -> binding.labelCadenceValue.text = "Cadence ${v.roundToInt()} rpm · $source" }
        value("ftms", Metric.POWER_WATTS)?.let { (v, source) -> binding.labelPowerValue.text = "Power ${v.roundToInt()} W · $source" }
        value("ftms", Metric.HEART_RATE_BPM)?.let { (v, source) -> binding.labelHeartRateValue.text = "Heart rate ${v.roundToInt()} bpm · $source" }
        value("csc", Metric.SPEED_MPS)?.let { (v, source) -> binding.labelCscSpeedValue.text = "CSC speed ${formatOne((v * 3.6).toFloat())} km/h · $source" }
        value("csc", Metric.CADENCE_RPM)?.let { (v, source) -> binding.labelCscCadenceValue.text = "CSC cadence ${v.roundToInt()} rpm · $source" }
        value("cycling_power", Metric.POWER_WATTS)?.let { (v, source) -> binding.labelCyclingPowerValue.text = "Power ${v.roundToInt()} W · $source" }
        value("rsc", Metric.SPEED_MPS)?.let { (v, source) -> binding.labelRscSpeedValue.text = "RSC speed ${String.format(Locale.US, "%.1f", v)} m/s · $source" }
        value("heart_rate", Metric.HEART_RATE_BPM)?.let { (v, source) -> binding.labelHeartRateServiceValue.text = "Heart rate ${v.roundToInt()} bpm · $source" }
        value("heart_rate", Metric.HEART_RATE_ENERGY_KJ)?.let { (v, source) -> binding.labelHeartRateEnergyValue.text = "Energy expended ${v.roundToInt()} kJ · $source" }
        value("heart_rate", Metric.RR_INTERVAL_SECONDS)?.let { (v, source) -> binding.labelHeartRateRrValue.text = "RR interval ${String.format(Locale.US, "%.2f", v)} s · $source" }
    }

    private fun bridgeText(text: String, muted: Boolean = false, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = if (bold) 15f else 12f
        setTextColor(ContextCompat.getColor(this@MainActivity,
            if (muted) R.color.simulator_ink_muted else R.color.simulator_ink))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun setupProfileActions() {
        setupServiceProfileList()
        setupFtmsMachineDropdown()
    }

    private fun setupServiceProfileList() {
        val standardServiceKeys = setOf("csc", "cycling_power", "rsc", "heart_rate")
        val ftmsMachineKeys = ftmsMachineOptions.map { option -> option.profileKey }.toSet()
        val extensionProfiles = profiles.values
            .filter { profile -> profile.key !in standardServiceKeys }
            .filter { profile -> profile.key !in ftmsMachineKeys }
        val exclusiveGroups = extensionProfiles
            .groupBy { profile -> profile.exclusiveGroup }
            .filter { (groupKey, groupProfiles) -> groupKey != null && groupProfiles.size > 1 }
            .map { (groupKey, groupProfiles) ->
                ExclusiveProfileGroup(
                    groupKey = groupKey.orEmpty(),
                    profiles = groupProfiles.sortedBy { profile -> profile.title }
                )
            }
            .sortedBy { group -> exclusiveGroupTitle(group.groupKey) }
        val groupedExtensionKeys = exclusiveGroups
            .flatMap { group -> group.profiles.map { profile -> profile.key } }
            .toSet()
        val extensionRows = exclusiveGroups.map { group -> exclusiveProfileGroupRow(group) } +
            extensionProfiles
                .filter { profile -> profile.key !in groupedExtensionKeys }
                .sortedBy { profile -> profile.title }
                .map { profile -> extensionProfileRow(profile) }

        val rows = listOf(
            ServiceProfileRow(
                key = "ftms",
                title = "FTMS",
                subtitle = "Fitness Machine Service. Choose exactly one equipment type inside this panel.",
                contentView = prepareServiceContent(binding.layoutFtmsLiveData),
                isEnabled = { ftmsEnabled },
                summary = { ftmsSummary() },
                setEnabled = { enabled -> setFtmsServiceEnabled(enabled) }
            ),
            ServiceProfileRow(
                key = "csc",
                title = "CSC",
                subtitle = profiles["csc"]?.subtitle ?: "Cycling Speed and Cadence simulation.",
                contentView = prepareServiceContent(binding.layoutCscLiveData),
                isEnabled = { selectedOptionalServiceKeys.contains("csc") },
                summary = { cscSummary() },
                setEnabled = { enabled -> setOptionalServiceEnabled("csc", enabled) }
            ),
            ServiceProfileRow(
                key = "cycling_power",
                title = "Cycling Power",
                subtitle = profiles["cycling_power"]?.subtitle ?: "Cycling Power Measurement simulation.",
                contentView = prepareServiceContent(binding.layoutCyclingPowerLiveData),
                isEnabled = { selectedOptionalServiceKeys.contains("cycling_power") },
                summary = { cyclingPowerSummary() },
                setEnabled = { enabled -> setOptionalServiceEnabled("cycling_power", enabled) }
            ),
            ServiceProfileRow(
                key = "rsc",
                title = "RSC",
                subtitle = profiles["rsc"]?.subtitle ?: "Running Speed and Cadence simulation.",
                contentView = prepareServiceContent(binding.layoutRscLiveData),
                isEnabled = { selectedOptionalServiceKeys.contains("rsc") },
                summary = { rscSummary() },
                setEnabled = { enabled -> setOptionalServiceEnabled("rsc", enabled) }
            ),
            ServiceProfileRow(
                key = "heart_rate",
                title = "Heart Rate",
                subtitle = profiles["heart_rate"]?.subtitle ?: "Heart Rate Service simulation.",
                contentView = prepareServiceContent(binding.layoutHeartRateLiveData),
                isEnabled = { selectedOptionalServiceKeys.contains("heart_rate") },
                summary = { heartRateSummary() },
                setEnabled = { enabled -> setOptionalServiceEnabled("heart_rate", enabled) }
            )
        ) + extensionRows

        serviceProfileAdapter = ServiceProfileAdapter(rows)
        binding.listServiceProfiles.apply {
            setAdapter(serviceProfileAdapter)
            setGroupIndicator(null)
            divider = null
            setChildDivider(null)
            setOnGroupExpandListener { expandedGroup ->
                for (groupIndex in 0 until serviceProfileAdapter.groupCount) {
                    if (groupIndex != expandedGroup && isGroupExpanded(groupIndex)) {
                        collapseGroup(groupIndex)
                    }
                }
                refreshServiceProfileListHeight()
            }
            setOnGroupCollapseListener { refreshServiceProfileListHeight() }
            post { refreshServiceProfileListHeight() }
        }
    }

    private fun prepareServiceContent(container: LinearLayout): LinearLayout {
        (container.parent as? ViewGroup)?.removeView(container)
        container.setPadding(dp(18), dp(18), dp(18), dp(22))
        container.setBackgroundResource(R.drawable.simulator_accordion_body)
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                AbsListView.LayoutParams.WRAP_CONTENT
            )
            addView(container)
            addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(18)
                )
            })
        }
    }

    private fun exclusiveProfileGroupRow(group: ExclusiveProfileGroup): ServiceProfileRow {
        val groupKeys = group.profiles.map { profile -> profile.key }.toSet()
        selectedExclusiveProfileKeys.putIfAbsent(
            group.groupKey,
            selectedOptionalServiceKeys.firstOrNull { key -> key in groupKeys } ?: group.profiles.first().key
        )

        return ServiceProfileRow(
            key = "exclusive:${group.groupKey}",
            title = exclusiveGroupTitle(group.groupKey),
            subtitle = "Select exactly one profile from this mutually exclusive group.",
            contentView = prepareServiceContent(createExclusiveProfileGroupContent(group)),
            isEnabled = { selectedOptionalServiceKeys.any { key -> key in groupKeys } },
            summary = {
                val profile = selectedProfileForExclusiveGroup(group)
                val state = if (selectedOptionalServiceKeys.any { key -> key in groupKeys }) "On" else "Off"
                "$state | ${profile.title} | ${profile.serviceUuid}"
            },
            setEnabled = { enabled -> setExclusiveProfileGroupEnabled(group, enabled) }
        )
    }

    private fun createExclusiveProfileGroupContent(group: ExclusiveProfileGroup): LinearLayout {
        val description = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink_muted))
            textSize = 14f
        }
        val service = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink_muted))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { params -> params.topMargin = dp(4) }
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                group.profiles.map { profile -> profile.title }
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).also { params -> params.topMargin = dp(8) }
        }

        fun bind(profile: GattProfileDefinition) {
            description.text = profile.subtitle
            service.text = "Service: ${profile.serviceUuid}"
        }

        val selectedProfile = selectedProfileForExclusiveGroup(group)
        bind(selectedProfile)
        spinner.setSelection(group.profiles.indexOf(selectedProfile).coerceAtLeast(0), false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val profile = group.profiles.getOrNull(position) ?: return
                bind(profile)
                selectExclusiveProfileInGroup(group, profile)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(18))
            layoutParams = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                AbsListView.LayoutParams.WRAP_CONTENT
            )

            addSectionLabel(this, "PROFILE SELECTOR")
            addView(spinner)
            addDivider(this)
            addView(description)
            addView(service)
            addDivider(this)
            addView(TextView(this@MainActivity).apply {
                text = "Only one profile in this group can be active at a time."
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink_muted))
                textSize = 13f
            })
        }
    }

    private fun addSectionLabel(container: LinearLayout, label: String) {
        container.addView(TextView(this).apply {
            text = label
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_moss))
            textSize = 13f
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = true
        })
    }

    private fun addDivider(container: LinearLayout) {
        container.addView(View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_line))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).also { params ->
                params.topMargin = dp(16)
                params.bottomMargin = dp(14)
            }
        })
    }

    private fun setupSensorProtocolControls() {
        renderRscProtocolControls()
        renderCscProtocolControls()
        renderCyclingPowerProtocolControls()
    }

    private fun setupHeartRateControls() {
        val configuration = viewModel.heartRateConfiguration
        binding.sliderHeartRateService.value = configuration.heartRateBpm.toFloat()
        binding.sliderHeartRateEnergy.value = configuration.energyExpendedKj.toFloat()
        binding.sliderHeartRateRr.value = configuration.rrIntervalsSeconds.lastOrNull()?.toFloat() ?: 0.8f
        binding.switchHeartRateEnergy.isChecked = configuration.includeEnergyExpended
        binding.switchHeartRateRr.isChecked = configuration.includeRrIntervals
        binding.switchHeartRateContact.isChecked = configuration.sensorContactSupported && configuration.sensorContactDetected
        binding.switchHeartRateBodyLocation.isChecked = configuration.bodySensorLocationCharacteristicEnabled
        binding.switchHeartRateControlPoint.isChecked = configuration.controlPointCharacteristicEnabled
        listOf(binding.switchHeartRateEnergy, binding.switchHeartRateRr, binding.switchHeartRateContact,
            binding.switchHeartRateBodyLocation, binding.switchHeartRateControlPoint).forEach(::styleSimulatorSwitch)

        fun refresh(rebuildGatt: Boolean = false) {
            binding.labelHeartRateServiceValue.text = "Heart rate ${configuration.heartRateBpm} bpm"
            binding.labelHeartRateEnergyValue.text = "Energy expended ${configuration.energyExpendedKj} kJ"
            binding.labelHeartRateRrValue.text = "RR interval ${String.format(Locale.US, "%.2f", configuration.rrIntervalsSeconds.lastOrNull() ?: 0.0)} s"
            updateProfileRowSummaries()
            if (rebuildGatt && ::applicationState.isInitialized && "heart_rate" in selectedOptionalServiceKeys) {
                applySelectedServiceStack(rebuildGatt = true, logChange = true)
            }
        }
        binding.sliderHeartRateService.addOnChangeListener { _, value, fromUser ->
            if (fromUser) { configuration.heartRateBpm = value.roundToInt(); refresh() }
        }
        binding.sliderHeartRateEnergy.addOnChangeListener { _, value, fromUser ->
            if (fromUser) { configuration.energyExpendedKj = value.roundToInt(); refresh() }
        }
        binding.sliderHeartRateRr.addOnChangeListener { _, value, fromUser ->
            if (fromUser) { configuration.rrIntervalsSeconds = listOf(value.toDouble()); refresh() }
        }
        binding.switchHeartRateEnergy.setOnCheckedChangeListener { _, checked ->
            configuration.includeEnergyExpended = checked; refresh(rebuildGatt = true)
        }
        binding.switchHeartRateRr.setOnCheckedChangeListener { _, checked ->
            configuration.includeRrIntervals = checked; refresh()
        }
        binding.switchHeartRateContact.setOnCheckedChangeListener { _, checked ->
            configuration.sensorContactSupported = checked; configuration.sensorContactDetected = checked; refresh()
        }
        binding.switchHeartRateBodyLocation.setOnCheckedChangeListener { _, checked ->
            configuration.bodySensorLocationCharacteristicEnabled = checked; refresh(rebuildGatt = true)
        }
        binding.switchHeartRateControlPoint.setOnCheckedChangeListener { _, checked ->
            configuration.controlPointCharacteristicEnabled = checked; refresh(rebuildGatt = true)
        }
        refresh()
    }

    private fun renderRscProtocolControls() {
        val container = binding.layoutRscProtocolControls
        val configuration = viewModel.rscConfiguration
        container.removeAllViews()

        addProtocolSection(container, "DECLARED RSC FEATURE · 0x${configuration.featureBitsForGatt().toString(16).uppercase().padStart(4, '0')}")
        addProtocolSwitch(
            container,
            "Mirror actual support into Feature bits",
            configuration.mirrorActualIntoFeature
        ) { enabled ->
            configuration.mirrorActualIntoFeature = enabled
            onSensorProtocolConfigurationChanged("rsc")
        }
        listOf(
            RscFeatureBits.STRIDE_LENGTH to "Bit 0 · Instantaneous Stride Length",
            RscFeatureBits.TOTAL_DISTANCE to "Bit 1 · Total Distance",
            RscFeatureBits.WALKING_OR_RUNNING to "Bit 2 · Walking/Running Status",
            RscFeatureBits.CALIBRATION to "Bit 3 · Sensor Calibration",
            RscFeatureBits.MULTIPLE_LOCATIONS to "Bit 4 · Multiple Sensor Locations"
        ).forEach { (bit, label) ->
            addDeclaredFeatureSwitch(
                container,
                label,
                configuration.declaredFeatureBits and bit != 0,
                !configuration.mirrorActualIntoFeature
            ) { enabled ->
                configuration.declaredFeatureBits = configuration.declaredFeatureBits.withBit(bit, enabled)
                onSensorProtocolConfigurationChanged("rsc")
            }
        }

        addProtocolDivider(container)
        addProtocolSection(container, "ACTUAL CHARACTERISTICS & MEASUREMENT")
        addProtocolSwitch(container, "Feature characteristic supports Indicate", configuration.featureIndicationsEnabled) {
            configuration.featureIndicationsEnabled = it
            onSensorProtocolConfigurationChanged("rsc")
        }
        addProtocolSwitch(container, "Sensor Location characteristic present", configuration.sensorLocationCharacteristicEnabled) {
            configuration.sensorLocationCharacteristicEnabled = it
            onSensorProtocolConfigurationChanged("rsc")
        }
        addProtocolSwitch(container, "SC Control Point characteristic present", configuration.controlPointCharacteristicEnabled) {
            configuration.controlPointCharacteristicEnabled = it
            onSensorProtocolConfigurationChanged("rsc")
        }
        addProtocolSwitch(container, "Measurement field · Stride Length", configuration.includeStrideLength) {
            configuration.includeStrideLength = it
            onSensorProtocolConfigurationChanged("rsc")
        }
        addProtocolSwitch(container, "Measurement field · Total Distance", configuration.includeTotalDistance) {
            configuration.includeTotalDistance = it
            onSensorProtocolConfigurationChanged("rsc")
        }
        addProtocolSwitch(container, "Measurement flag · Walking/Running Status", configuration.includeWalkingOrRunningStatus) {
            configuration.includeWalkingOrRunningStatus = it
            onSensorProtocolConfigurationChanged("rsc")
        }
        addProtocolSwitch(container, "Status value · Running (off = walking)", configuration.runningStatus) {
            configuration.runningStatus = it
            onSensorProtocolConfigurationChanged("rsc", rebuildGatt = false)
        }
        addSensorLocationSpinner(container, "Current Sensor Location", configuration.sensorLocation) { location ->
            configuration.sensorLocation = location
            onSensorProtocolConfigurationChanged("rsc", rebuildGatt = false)
        }

        addProtocolDivider(container)
        addProtocolSection(container, "ACTUAL SC CONTROL POINT PROCEDURES")
        addProcedureSwitch(container, configuration.supportedProcedures, ScControlPointOpCodes.SET_CUMULATIVE_VALUE, "0x01 · Set Cumulative Value", "rsc")
        addProcedureSwitch(container, configuration.supportedProcedures, ScControlPointOpCodes.START_SENSOR_CALIBRATION, "0x02 · Start Sensor Calibration", "rsc")
        addProcedureSwitch(container, configuration.supportedProcedures, ScControlPointOpCodes.UPDATE_SENSOR_LOCATION, "0x03 · Update Sensor Location", "rsc")
        addProcedureSwitch(container, configuration.supportedProcedures, ScControlPointOpCodes.REQUEST_SUPPORTED_SENSOR_LOCATIONS, "0x04 · Request Supported Sensor Locations", "rsc")
        addSupportedLocationControls(container, configuration.supportedSensorLocations, "rsc")
    }

    private fun renderCscProtocolControls() {
        val container = binding.layoutCscProtocolControls
        val configuration = viewModel.cscConfiguration
        container.removeAllViews()

        addProtocolSection(container, "DECLARED CSC FEATURE · 0x${configuration.featureBitsForGatt().toString(16).uppercase().padStart(4, '0')}")
        addProtocolSwitch(
            container,
            "Mirror actual support into Feature bits",
            configuration.mirrorActualIntoFeature
        ) { enabled ->
            configuration.mirrorActualIntoFeature = enabled
            onSensorProtocolConfigurationChanged("csc")
        }
        listOf(
            CscFeatureBits.WHEEL_REVOLUTION_DATA to "Bit 0 · Wheel Revolution Data",
            CscFeatureBits.CRANK_REVOLUTION_DATA to "Bit 1 · Crank Revolution Data",
            CscFeatureBits.MULTIPLE_LOCATIONS to "Bit 2 · Multiple Sensor Locations"
        ).forEach { (bit, label) ->
            addDeclaredFeatureSwitch(
                container,
                label,
                configuration.declaredFeatureBits and bit != 0,
                !configuration.mirrorActualIntoFeature
            ) { enabled ->
                configuration.declaredFeatureBits = configuration.declaredFeatureBits.withBit(bit, enabled)
                onSensorProtocolConfigurationChanged("csc")
            }
        }

        addProtocolDivider(container)
        addProtocolSection(container, "ACTUAL CHARACTERISTICS & MEASUREMENT")
        addProtocolSwitch(container, "Feature characteristic supports Indicate", configuration.featureIndicationsEnabled) {
            configuration.featureIndicationsEnabled = it
            onSensorProtocolConfigurationChanged("csc")
        }
        addProtocolSwitch(container, "Sensor Location characteristic present", configuration.sensorLocationCharacteristicEnabled) {
            configuration.sensorLocationCharacteristicEnabled = it
            onSensorProtocolConfigurationChanged("csc")
        }
        addProtocolSwitch(container, "SC Control Point characteristic present", configuration.controlPointCharacteristicEnabled) {
            configuration.controlPointCharacteristicEnabled = it
            onSensorProtocolConfigurationChanged("csc")
        }
        addProtocolSwitch(container, "Measurement pair · Wheel Revolution Data", configuration.includeWheelRevolutionData) {
            configuration.includeWheelRevolutionData = it
            onSensorProtocolConfigurationChanged("csc")
        }
        addProtocolSwitch(container, "Measurement pair · Crank Revolution Data", configuration.includeCrankRevolutionData) {
            configuration.includeCrankRevolutionData = it
            onSensorProtocolConfigurationChanged("csc")
        }
        addSensorLocationSpinner(container, "Current Sensor Location", configuration.sensorLocation) { location ->
            configuration.sensorLocation = location
            onSensorProtocolConfigurationChanged("csc", rebuildGatt = false)
        }

        addProtocolDivider(container)
        addProtocolSection(container, "ACTUAL SC CONTROL POINT PROCEDURES")
        addProcedureSwitch(container, configuration.supportedProcedures, CscControlPointOpCodes.SET_CUMULATIVE_VALUE, "0x01 · Set Cumulative Value", "csc")
        addProcedureSwitch(container, configuration.supportedProcedures, CscControlPointOpCodes.UPDATE_SENSOR_LOCATION, "0x03 · Update Sensor Location", "csc")
        addProcedureSwitch(container, configuration.supportedProcedures, CscControlPointOpCodes.REQUEST_SUPPORTED_SENSOR_LOCATIONS, "0x04 · Request Supported Sensor Locations", "csc")
        addSupportedLocationControls(container, configuration.supportedSensorLocations, "csc")
    }

    private fun renderCyclingPowerProtocolControls() {
        val container = binding.layoutCyclingPowerProtocolControls
        val configuration = viewModel.cyclingPowerConfiguration
        container.removeAllViews()

        addProtocolSection(
            container,
            "DECLARED CYCLING POWER FEATURE · 0x${configuration.featureBitsForGatt().toUInt().toString(16).uppercase().padStart(8, '0')}"
        )
        addProtocolSwitch(
            container,
            "Mirror actual support into Feature bits",
            configuration.mirrorActualIntoFeature
        ) { enabled ->
            configuration.mirrorActualIntoFeature = enabled
            onSensorProtocolConfigurationChanged("cycling_power")
        }
        val declaredBits = listOf(
            0 to "Pedal Power Balance",
            1 to "Accumulated Torque",
            2 to "Wheel Revolution Data",
            3 to "Crank Revolution Data",
            4 to "Extreme Magnitudes",
            5 to "Extreme Angles",
            6 to "Top/Bottom Dead Spot Angles",
            7 to "Accumulated Energy",
            8 to "Offset Compensation Indicator",
            9 to "Offset Compensation",
            10 to "Measurement Content Masking",
            11 to "Multiple Sensor Locations",
            12 to "Crank Length Adjustment",
            13 to "Chain Length Adjustment",
            14 to "Chain Weight Adjustment",
            15 to "Span Length Adjustment",
            16 to "Sensor Context · Torque-based",
            17 to "Instantaneous Measurement Direction",
            18 to "Factory Calibration Date",
            19 to "Enhanced Offset Compensation"
        )
        declaredBits.forEach { (bitNumber, label) ->
            val bit = 1 shl bitNumber
            addDeclaredFeatureSwitch(
                container,
                "Bit $bitNumber · $label",
                configuration.declaredFeatureBits and bit != 0,
                !configuration.mirrorActualIntoFeature
            ) { enabled ->
                configuration.declaredFeatureBits = configuration.declaredFeatureBits.withBit(bit, enabled)
                onSensorProtocolConfigurationChanged("cycling_power")
            }
        }
        addDistributedSystemSpinner(
            container,
            "Bits 20–21 · Declared Distributed System Support",
            (configuration.declaredFeatureBits ushr 20) and 0x03,
            enabled = !configuration.mirrorActualIntoFeature
        ) { selection ->
            configuration.declaredFeatureBits =
                (configuration.declaredFeatureBits and (0x03 shl 20).inv()) or (selection shl 20)
            onSensorProtocolConfigurationChanged("cycling_power")
        }

        addProtocolDivider(container)
        addProtocolSection(container, "ACTUAL CHARACTERISTICS")
        addProtocolSwitch(container, "Feature characteristic supports Indicate", configuration.featureIndicationsEnabled) {
            configuration.featureIndicationsEnabled = it
            onSensorProtocolConfigurationChanged("cycling_power")
        }
        addProtocolSwitch(container, "Sensor Location characteristic present (mandatory in standard CPS)", configuration.sensorLocationCharacteristicEnabled) {
            configuration.sensorLocationCharacteristicEnabled = it
            onSensorProtocolConfigurationChanged("cycling_power")
        }
        addProtocolSwitch(container, "Cycling Power Control Point present", configuration.controlPointCharacteristicEnabled) {
            configuration.controlPointCharacteristicEnabled = it
            onSensorProtocolConfigurationChanged("cycling_power")
        }
        addProtocolSwitch(container, "Cycling Power Vector present", configuration.vectorCharacteristicEnabled) {
            configuration.vectorCharacteristicEnabled = it
            onSensorProtocolConfigurationChanged("cycling_power")
        }
        addProtocolSwitch(container, "Measurement Broadcast property + SCCD present", configuration.measurementBroadcastPropertyEnabled) {
            configuration.measurementBroadcastPropertyEnabled = it
            onSensorProtocolConfigurationChanged("cycling_power")
        }
        addSensorLocationSpinner(container, "Current Sensor Location", configuration.sensorLocation) { location ->
            configuration.sensorLocation = location
            onSensorProtocolConfigurationChanged("cycling_power", rebuildGatt = false)
        }

        addProtocolDivider(container)
        addProtocolSection(container, "ACTUAL MEASUREMENT CONTENT")
        listOf(
            "Pedal Power Balance" to Pair({ configuration.includePedalPowerBalance }, { value: Boolean -> configuration.includePedalPowerBalance = value }),
            "Accumulated Torque" to Pair({ configuration.includeAccumulatedTorque }, { value: Boolean -> configuration.includeAccumulatedTorque = value }),
            "Wheel Revolution Data" to Pair({ configuration.includeWheelRevolutionData }, { value: Boolean -> configuration.includeWheelRevolutionData = value }),
            "Crank Revolution Data" to Pair({ configuration.includeCrankRevolutionData }, { value: Boolean -> configuration.includeCrankRevolutionData = value }),
            "Extreme Magnitudes" to Pair({ configuration.includeExtremeMagnitudes }, { value: Boolean -> configuration.includeExtremeMagnitudes = value }),
            "Extreme Angles" to Pair({ configuration.includeExtremeAngles }, { value: Boolean -> configuration.includeExtremeAngles = value }),
            "Top Dead Spot Angle" to Pair({ configuration.includeTopDeadSpotAngle }, { value: Boolean -> configuration.includeTopDeadSpotAngle = value }),
            "Bottom Dead Spot Angle" to Pair({ configuration.includeBottomDeadSpotAngle }, { value: Boolean -> configuration.includeBottomDeadSpotAngle = value }),
            "Accumulated Energy" to Pair({ configuration.includeAccumulatedEnergy }, { value: Boolean -> configuration.includeAccumulatedEnergy = value }),
            "Offset Compensation Indicator support" to Pair({ configuration.offsetCompensationIndicatorSupported }, { value: Boolean -> configuration.offsetCompensationIndicatorSupported = value }),
            "Offset Compensation Indicator set" to Pair({ configuration.offsetCompensationRequired }, { value: Boolean -> configuration.offsetCompensationRequired = value })
        ).forEach { (label, accessors) ->
            addProtocolSwitch(container, "Measurement · $label", accessors.first()) { enabled ->
                accessors.second(enabled)
                onSensorProtocolConfigurationChanged("cycling_power", rebuildGatt = false)
            }
        }
        addProtocolSwitch(container, "Context · Torque-based (off = force-based)", configuration.sensorMeasurementContextTorqueBased) {
            configuration.sensorMeasurementContextTorqueBased = it
            onSensorProtocolConfigurationChanged("cycling_power", rebuildGatt = false)
        }
        addProtocolSwitch(container, "Instantaneous Measurement Direction supported", configuration.instantaneousMeasurementDirectionSupported) {
            configuration.instantaneousMeasurementDirectionSupported = it
            onSensorProtocolConfigurationChanged("cycling_power", rebuildGatt = false)
        }
        addDistributedSystemSpinner(container, "Actual Distributed System Support", configuration.distributedSystemSupport, enabled = true) {
            configuration.distributedSystemSupport = it
            onSensorProtocolConfigurationChanged("cycling_power", rebuildGatt = false)
        }

        addProtocolDivider(container)
        addProtocolSection(container, "ACTUAL VECTOR CONTENT")
        addProtocolSwitch(container, "Vector · Crank Revolution Data", configuration.vectorIncludeCrankData) {
            configuration.vectorIncludeCrankData = it
            onSensorProtocolConfigurationChanged("cycling_power", rebuildGatt = false)
        }
        addProtocolSwitch(container, "Vector · First Crank Measurement Angle", configuration.vectorIncludeFirstCrankAngle) {
            configuration.vectorIncludeFirstCrankAngle = it
            onSensorProtocolConfigurationChanged("cycling_power", rebuildGatt = false)
        }
        addProtocolSwitch(container, "Vector · Instantaneous Magnitude Array", configuration.vectorIncludeMagnitudeArray) {
            configuration.vectorIncludeMagnitudeArray = it
            onSensorProtocolConfigurationChanged("cycling_power", rebuildGatt = false)
        }

        addProtocolDivider(container)
        addProtocolSection(container, "ACTUAL CYCLING POWER CONTROL POINT PROCEDURES")
        val procedures = listOf(
            0x01 to "Set Cumulative Value",
            0x02 to "Update Sensor Location",
            0x03 to "Request Supported Sensor Locations",
            0x04 to "Set Crank Length",
            0x05 to "Request Crank Length",
            0x06 to "Set Chain Length",
            0x07 to "Request Chain Length",
            0x08 to "Set Chain Weight",
            0x09 to "Request Chain Weight",
            0x0A to "Set Span Length",
            0x0B to "Request Span Length",
            0x0C to "Start Offset Compensation",
            0x0D to "Mask Measurement Content",
            0x0E to "Request Sampling Rate",
            0x0F to "Request Factory Calibration Date",
            0x10 to "Start Enhanced Offset Compensation"
        )
        procedures.forEach { (opCode, name) ->
            addProcedureSwitch(
                container,
                configuration.supportedProcedures,
                opCode,
                "0x${opCode.toString(16).uppercase().padStart(2, '0')} · $name",
                "cycling_power"
            )
        }
        addSupportedLocationControls(container, configuration.supportedSensorLocations, "cycling_power")
    }

    private fun addProtocolSection(container: LinearLayout, label: String) {
        addSectionLabel(container, label)
    }

    private fun addProtocolDivider(container: LinearLayout) {
        addDivider(container)
    }

    private fun addProtocolSwitch(
        container: LinearLayout,
        label: String,
        checked: Boolean,
        enabled: Boolean = true,
        onChanged: (Boolean) -> Unit
    ): SwitchMaterial {
        return SwitchMaterial(this).apply {
            text = label
            isChecked = checked
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.5f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { params -> params.topMargin = dp(6) }
            styleSimulatorSwitch(this)
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
            container.addView(this)
        }
    }

    private fun addDeclaredFeatureSwitch(
        container: LinearLayout,
        label: String,
        checked: Boolean,
        enabled: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        addProtocolSwitch(container, "Declared · $label", checked, enabled, onChanged)
    }

    private fun addProcedureSwitch(
        container: LinearLayout,
        procedures: MutableSet<Int>,
        opCode: Int,
        label: String,
        serviceKey: String
    ) {
        addProtocolSwitch(container, "Procedure · $label", opCode in procedures) { enabled ->
            if (enabled) procedures.add(opCode) else procedures.remove(opCode)
            onSensorProtocolConfigurationChanged(serviceKey)
        }
    }

    private fun addSensorLocationSpinner(
        container: LinearLayout,
        label: String,
        selection: Int,
        onChanged: (Int) -> Unit
    ) {
        container.addView(TextView(this).apply {
            text = label
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink_muted))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { params -> params.topMargin = dp(10) }
        })
        container.addView(Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                SensorLocationValues.names.mapIndexed { index, name -> "$index · $name" }
            ).also { arrayAdapter ->
                arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(selection.coerceIn(SensorLocationValues.names.indices), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != selection) onChanged(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        })
    }

    private fun addDistributedSystemSpinner(
        container: LinearLayout,
        label: String,
        selection: Int,
        enabled: Boolean,
        onChanged: (Int) -> Unit
    ) {
        container.addView(TextView(this).apply {
            text = label
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink_muted))
            textSize = 13f
            alpha = if (enabled) 1f else 0.5f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { params -> params.topMargin = dp(10) }
        })
        container.addView(Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                listOf("00 · Unspecified legacy", "01 · Not distributed", "10 · Distributed", "11 · RFU")
            ).also { arrayAdapter ->
                arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.5f
            setSelection(selection.coerceIn(0, 3), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (enabled && position != selection) onChanged(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        })
    }

    private fun addSupportedLocationControls(
        container: LinearLayout,
        locations: MutableSet<Int>,
        serviceKey: String
    ) {
        addProtocolDivider(container)
        addProtocolSection(container, "SUPPORTED SENSOR LOCATIONS RETURNED BY CONTROL POINT")
        SensorLocationValues.names.forEachIndexed { value, name ->
            addProtocolSwitch(container, "$value · $name", value in locations) { enabled ->
                if (enabled) locations.add(value) else locations.remove(value)
                onSensorProtocolConfigurationChanged(serviceKey, rebuildGatt = false)
            }
        }
    }

    private fun onSensorProtocolConfigurationChanged(
        serviceKey: String,
        rebuildGatt: Boolean = true
    ) {
        val active = serviceKey in selectedOptionalServiceKeys
        if (active && ::applicationState.isInitialized && rebuildGatt) {
            applySelectedServiceStack(rebuildGatt = true, logChange = false)
        } else {
            updateProfileRowSummaries()
        }
        uiHandler.post { setupSensorProtocolControls() }
    }

    private fun Int.withBit(bit: Int, enabled: Boolean): Int =
        if (enabled) this or bit else this and bit.inv()

    private fun setExclusiveProfileGroupEnabled(group: ExclusiveProfileGroup, enabled: Boolean) {
        if (!::applicationState.isInitialized) {
            initializeApplicationState()
        }

        val groupKeys = group.profiles.map { profile -> profile.key }.toSet()
        val before = selectedOptionalServiceKeys.toSet()
        selectedOptionalServiceKeys.removeAll(groupKeys)
        if (enabled) {
            selectedOptionalServiceKeys.add(selectedProfileForExclusiveGroup(group).key)
        }

        if (selectedOptionalServiceKeys.toSet() != before) {
            applySelectedServiceStack(rebuildGatt = true, logChange = true)
        } else {
            updateProfileButtons()
        }
    }

    private fun selectExclusiveProfileInGroup(group: ExclusiveProfileGroup, profile: GattProfileDefinition) {
        val groupKeys = group.profiles.map { candidate -> candidate.key }.toSet()
        val wasEnabled = selectedOptionalServiceKeys.any { key -> key in groupKeys }
        selectedExclusiveProfileKeys[group.groupKey] = profile.key

        if (!wasEnabled) {
            updateProfileButtons()
            return
        }

        val before = selectedOptionalServiceKeys.toSet()
        selectedOptionalServiceKeys.removeAll(groupKeys)
        selectedOptionalServiceKeys.add(profile.key)
        if (selectedOptionalServiceKeys.toSet() != before) {
            applySelectedServiceStack(rebuildGatt = true, logChange = true)
        } else {
            updateProfileButtons()
        }
    }

    private fun selectedProfileForExclusiveGroup(group: ExclusiveProfileGroup): GattProfileDefinition {
        val groupKeys = group.profiles.map { profile -> profile.key }.toSet()
        val activeKey = selectedOptionalServiceKeys.firstOrNull { key -> key in groupKeys }
        val selectedKey = activeKey ?: selectedExclusiveProfileKeys[group.groupKey]
        return group.profiles.firstOrNull { profile -> profile.key == selectedKey } ?: group.profiles.first()
    }

    private fun exclusiveGroupTitle(groupKey: String): String {
        if (groupKey.contains("uart", ignoreCase = true)) {
            return "UART Consoles"
        }
        val words = groupKey.replace('_', '-')
            .split("-")
            .filter { word -> word.isNotBlank() }
        return words.joinToString(" ") { word ->
            if (word.length <= 3) {
                word.uppercase(Locale.US)
            } else {
                word.replaceFirstChar { char -> char.uppercase(Locale.US) }
            }
        }.ifBlank { "Exclusive Profiles" }
    }

    private fun extensionProfileRow(profile: GattProfileDefinition): ServiceProfileRow {
        val provider = profileProviderByKey[profile.key]?.providerId ?: "extension"
        val isEnabled = {
            when (profile.selectionMode) {
                ProfileSelectionMode.FTMS_MACHINE -> ftmsEnabled && selectedFtmsProfileKey == profile.key
                ProfileSelectionMode.OPTIONAL_SERVICE -> selectedOptionalServiceKeys.contains(profile.key)
            }
        }
        return ServiceProfileRow(
            key = profile.key,
            title = profile.title,
            subtitle = profile.subtitle,
            contentView = prepareServiceContent(createExtensionProfileContent(profile, provider)),
            isEnabled = isEnabled,
            summary = {
                "${if (isEnabled()) "On" else "Off"} | ${profile.serviceUuid}"
            },
            setEnabled = { enabled ->
                when (profile.selectionMode) {
                    ProfileSelectionMode.FTMS_MACHINE -> {
                        if (enabled) {
                            toggleProfile(profile)
                        } else if (selectedFtmsProfileKey == profile.key) {
                            setFtmsServiceEnabled(false)
                        }
                    }

                    ProfileSelectionMode.OPTIONAL_SERVICE -> setOptionalServiceEnabled(profile.key, enabled)
                }
            }
        )
    }

    private fun createExtensionProfileContent(profile: GattProfileDefinition, provider: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(18))
            layoutParams = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                AbsListView.LayoutParams.WRAP_CONTENT
            )

            addSectionLabel(this, "PROFILE DETAILS")
            addView(TextView(this@MainActivity).apply {
                text = profile.subtitle
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink_muted))
                textSize = 14f
            })
            addDivider(this)

            addView(TextView(this@MainActivity).apply {
                text = "Provider: $provider"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { params -> params.topMargin = dp(12) }
            })

            addView(TextView(this@MainActivity).apply {
                text = "Service: ${profile.serviceUuid}"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink_muted))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { params -> params.topMargin = dp(4) }
            })

            addDivider(this)
            addView(TextView(this@MainActivity).apply {
                text = if (profile.exclusiveGroup != null) {
                    "This profile is mutually exclusive with other profiles in the same extension group."
                } else {
                    "This profile can run alongside other active services."
                }
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink_muted))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { params -> params.topMargin = dp(10) }
            })
        }
    }

    private fun refreshServiceProfileList() {
        if (!::serviceProfileAdapter.isInitialized) {
            return
        }
        serviceProfileAdapter.notifyDataSetChanged()
        binding.listServiceProfiles.post { refreshServiceProfileListHeight() }
    }

    private fun refreshServiceProfileListHeight() {
        if (!::serviceProfileAdapter.isInitialized || !::binding.isInitialized) {
            return
        }

        val listView = binding.listServiceProfiles
        val width = (listView.width.takeIf { it > 0 } ?: (resources.displayMetrics.widthPixels - dp(80)))
            .coerceAtLeast(dp(120))
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        var totalHeight = listView.paddingTop + listView.paddingBottom

        for (groupIndex in 0 until serviceProfileAdapter.groupCount) {
            val groupView = serviceProfileAdapter.getGroupView(
                groupIndex,
                listView.isGroupExpanded(groupIndex),
                null,
                listView
            )
            totalHeight += measureListItemHeight(groupView, widthSpec)

            if (listView.isGroupExpanded(groupIndex)) {
                val childView = serviceProfileAdapter.getChildView(groupIndex, 0, true, null, listView)
                totalHeight += measureListItemHeight(childView, widthSpec)
            }
        }

        if (serviceProfileAdapter.groupCount > 1) {
            totalHeight += listView.dividerHeight * (serviceProfileAdapter.groupCount - 1)
        }

        val layoutParams = listView.layoutParams
        if (layoutParams.height != totalHeight) {
            layoutParams.height = totalHeight
            listView.layoutParams = layoutParams
        }
    }

    private fun measureListItemHeight(view: View, widthSpec: Int): Int {
        if (view.layoutParams == null) {
            view.layoutParams = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                AbsListView.LayoutParams.WRAP_CONTENT
            )
        }
        view.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return view.measuredHeight
    }

    private inner class ServiceProfileAdapter(
        private val rows: List<ServiceProfileRow>
    ) : BaseExpandableListAdapter() {
        override fun getGroupCount(): Int = rows.size

        override fun getChildrenCount(groupPosition: Int): Int = 1

        override fun getGroup(groupPosition: Int): ServiceProfileRow = rows[groupPosition]

        override fun getChild(groupPosition: Int, childPosition: Int): View = rows[groupPosition].contentView

        override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

        override fun getChildId(groupPosition: Int, childPosition: Int): Long = groupPosition.toLong()

        override fun hasStableIds(): Boolean = true

        override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = false

        override fun getGroupView(
            groupPosition: Int,
            isExpanded: Boolean,
            convertView: View?,
            parent: ViewGroup?
        ): View {
            val view = if (convertView is LinearLayout && convertView.tag is ServiceGroupHolder) {
                convertView
            } else {
                createServiceGroupView()
            }
            val holder = view.tag as ServiceGroupHolder

            val row = getGroup(groupPosition)
            holder.title.text = row.title
            holder.summary.text = row.summary()
            holder.indicator.setImageResource(
                if (isExpanded) {
                    R.drawable.ic_accordion_expand_more
                } else {
                    R.drawable.ic_accordion_chevron_right
                }
            )
            holder.indicator.contentDescription = if (isExpanded) "Collapse ${row.title}" else "Expand ${row.title}"
            holder.switchView.setOnCheckedChangeListener(null)
            holder.switchView.isChecked = row.isEnabled()
            holder.switchView.visibility = if (row.toggleVisible) View.VISIBLE else View.GONE
            holder.switchView.setOnCheckedChangeListener { _, isChecked -> row.setEnabled(isChecked) }
            val titleColor = ContextCompat.getColor(this@MainActivity, R.color.simulator_ink)
            val summaryColor = ContextCompat.getColor(this@MainActivity, R.color.simulator_ink_muted)
            holder.title.setTextColor(titleColor)
            holder.summary.setTextColor(summaryColor)
            holder.indicator.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.simulator_moss)
            )
            holder.spacer.visibility = if (isExpanded) View.GONE else View.VISIBLE
            view.alpha = if (row.isEnabled()) 1f else 0.72f
            holder.surface.setBackgroundResource(
                if (isExpanded) {
                    R.drawable.simulator_accordion_header_open
                } else {
                    R.drawable.simulator_accordion_item_closed
                }
            )
            return view
        }

        override fun getChildView(
            groupPosition: Int,
            childPosition: Int,
            isLastChild: Boolean,
            convertView: View?,
            parent: ViewGroup?
        ): View {
            val row = getGroup(groupPosition)
            row.contentView.alpha = if (row.isEnabled()) 1f else 0.58f
            row.contentView.layoutParams = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                AbsListView.LayoutParams.WRAP_CONTENT
            )
            return row.contentView
        }
    }

    private data class ServiceGroupHolder(
        val title: TextView,
        val summary: TextView,
        val switchView: SwitchMaterial,
        val indicator: ImageView,
        val surface: LinearLayout,
        val spacer: View
    )

    private fun createServiceGroupView(): LinearLayout {
        val title = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink))
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setSingleLine(false)
        }
        val summary = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink_muted))
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        val switchView = SwitchMaterial(this).apply {
            text = ""
            isFocusable = false
            styleSimulatorSwitch(this)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { params -> params.marginEnd = dp(12) }
        }
        val indicator = ImageView(this).apply {
            setImageResource(R.drawable.ic_accordion_chevron_right)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.simulator_moss)
            )
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).also { params ->
                params.marginStart = dp(6)
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val surface = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(76)
            setPadding(dp(18), dp(13), dp(12), dp(13))
            setBackgroundResource(R.drawable.simulator_accordion_item_closed)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            addView(switchView)
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                addView(title)
                addView(summary)
            })
            addView(indicator)
        }
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(18)
            )
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                AbsListView.LayoutParams.WRAP_CONTENT
            )
            addView(surface)
            addView(spacer)
            tag = ServiceGroupHolder(title, summary, switchView, indicator, surface, spacer)
        }
    }

    private fun setFtmsServiceEnabled(enabled: Boolean) {
        if (!::applicationState.isInitialized) {
            initializeApplicationState()
        }
        if (ftmsEnabled == enabled) {
            updateProfileButtons()
            return
        }
        ftmsEnabled = enabled
        applySelectedServiceStack(rebuildGatt = true, logChange = true)
    }

    private fun setOptionalServiceEnabled(key: String, enabled: Boolean) {
        if (!::applicationState.isInitialized) {
            initializeApplicationState()
        }

        val changed = if (enabled) {
            val profile = profiles[key] ?: return
            profile.exclusiveGroup?.let { exclusiveGroup ->
                val exclusiveKeys = profiles.values
                    .filter { candidate -> candidate.exclusiveGroup == exclusiveGroup }
                    .map { candidate -> candidate.key }
                selectedOptionalServiceKeys.removeAll(exclusiveKeys.toSet())
            }
            selectedOptionalServiceKeys.add(key)
        } else {
            selectedOptionalServiceKeys.remove(key)
        }

        if (changed) {
            applySelectedServiceStack(rebuildGatt = true, logChange = true)
        } else {
            updateProfileButtons()
        }
    }

    private fun setupFtmsMachineDropdown() {
        val labels = ftmsMachineOptions.map { option -> option.label }
        binding.spinnerFtmsMachine.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerFtmsMachine.setSelection(
            ftmsMachineOptions.indexOfFirst { option -> option.profileKey == selectedFtmsProfileKey }
                .coerceAtLeast(0),
            false
        )
        binding.spinnerFtmsMachine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedKey = ftmsMachineOptions.getOrNull(position)?.profileKey ?: return
                if (selectedKey == selectedFtmsProfileKey) {
                    return
                }
                selectedFtmsProfileKey = selectedKey
                renderedMachineFieldKey = null
                if (::applicationState.isInitialized) {
                    applySelectedServiceStack(rebuildGatt = ftmsEnabled, logChange = ftmsEnabled)
                } else {
                    updateDeviceStatePanel()
                    updateSimulationValuesFromControls()
                    renderMachineFieldControls()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupSimulationControls() {
        binding.sliderSpeed.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderCadence.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderPower.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderResistance.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderInclination.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderHeartRate.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderMetabolicEquivalent.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderTargetDuration.addOnChangeListener { _, _, _ ->
            updateSimulationValuesFromControls(updateFtmsDuration = true)
        }
        binding.sliderCscSpeed.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderCscCadence.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderCyclingPower.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderCyclingPowerSpeed.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderCyclingPowerCadence.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderRscSpeed.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.sliderRscCadence.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
        binding.switchFtmsControlPoint.isChecked = viewModel.ftmsControlPointEnabled
        binding.switchFtmsControlPoint.setOnCheckedChangeListener { _, isChecked ->
            viewModel.ftmsControlPointEnabled = isChecked
            if (!isChecked) {
                viewModel.controlHasBeenTakenInControlPoint.value = false
                viewModel.ftmsControlOwnerAddress.value = null
                viewModel.fitnessMachineHasBeenStarted.value = false
                viewModel.ftmsTrainingStatus.value = 0x01
                viewModel.clearFtmsTargetState()
                updateSimulationValuesFromControls(updateFtmsDuration = true)
            }
            renderedMachineFieldKey = null
            if (::applicationState.isInitialized) {
                applySelectedServiceStack(rebuildGatt = true, logChange = false)
            } else {
                renderMachineFieldControls()
            }
            updateDeviceStatePanel()
        }
        binding.switchHeartRate.setOnCheckedChangeListener { _, _ ->
            updateSimulationValuesFromControls()
            if (::applicationState.isInitialized) {
                applySelectedServiceStack(rebuildGatt = true, logChange = false)
            }
        }
    }

    private fun toggleProfile(profile: GattProfileDefinition) {
        if (!::applicationState.isInitialized) {
            initializeApplicationState()
        }

        when (profile.selectionMode) {
            ProfileSelectionMode.FTMS_MACHINE -> {
                selectedFtmsProfileKey = profile.key
                ftmsEnabled = true
            }

            ProfileSelectionMode.OPTIONAL_SERVICE -> {
                if (selectedOptionalServiceKeys.contains(profile.key)) {
                    selectedOptionalServiceKeys.remove(profile.key)
                } else {
                    profile.exclusiveGroup?.let { exclusiveGroup ->
                        val exclusiveKeys = profiles.values
                            .filter { candidate -> candidate.exclusiveGroup == exclusiveGroup }
                            .map { candidate -> candidate.key }
                        selectedOptionalServiceKeys.removeAll(exclusiveKeys.toSet())
                    }
                    selectedOptionalServiceKeys.add(profile.key)
                }
            }
        }
        binding.buttonClearFtmsEvents.setOnClickListener {
            viewModel.clearFtmsProtocolEvents()
        }

        applySelectedServiceStack(rebuildGatt = true, logChange = true)
    }

    private fun applySelectedServiceStack(rebuildGatt: Boolean, logChange: Boolean) {
        if (::applicationState.isInitialized) {
            val ftmsMode = if (ftmsEnabled) {
                profiles.getValue(selectedFtmsProfileKey).ftmsMode
                    ?: error("Selected FTMS profile is missing its FTMS mode")
            } else {
                null
            }
            applicationState.configureProfileStack(
                profiles = createSelectedGattProfiles(),
                selectedFitnessMachineMode = ftmsMode,
                rebuildGatt = rebuildGatt
            )
        }

        updateDeviceStatePanel()
        updateProfileButtons()
        updateSimulationValuesFromControls()
        renderMachineFieldControls()
        updateRuntimeState()

        if (logChange) {
            val configuredProfiles = selectedProfiles()
            appendLog(
                if (configuredProfiles.isEmpty()) {
                    "Configured no active GATT services"
                } else {
                    "Configured ${configuredProfiles.joinToString(" + ") { it.title }}"
                }
            )
        }
    }

    private fun selectedProfiles(): List<GattProfileDefinition> {
        return buildList {
            if (ftmsEnabled) {
                add(profiles.getValue(selectedFtmsProfileKey))
            }
            selectedOptionalServiceKeys.forEach { key ->
                profiles[key]?.let { add(it) }
            }
        }
    }

    private fun createSelectedGattProfiles(): List<AbstractGattProfile> {
        val dependencies = ProfileDependencies(viewModel)
        return selectedProfiles().map { profile ->
            val provider = profileProviderByKey[profile.key]
                ?: error("No GATT profile provider registered for ${profile.key}")
            provider.createProfile(profile.key, dependencies)
                ?: error("Provider ${provider.providerId} did not create ${profile.key}")
        }
    }

    private fun selectedMachineName(): String {
        if (!ftmsEnabled) {
            return "FTMS disabled"
        }
        return when (profiles.getValue(selectedFtmsProfileKey).ftmsMode) {
            "indoor_bike" -> "Indoor Bike"
            "rower" -> "Rower"
            "treadmill" -> "Treadmill"
            "cross_trainer" -> "Cross Trainer"
            "stair_climber" -> "Stair Climber"
            "step_climber" -> "Step Climber"
            else -> "Fitness Machine"
        }
    }

    private fun ftmsCadenceFieldLabel(): String {
        return when (selectedFtmsProfileKey) {
            "ftms_rower" -> "Stroke rate"
            "ftms_cross" -> "Step rate"
            "ftms_step", "ftms_stair" -> "Steps per minute"
            else -> "Cadence"
        }
    }

    private fun ftmsResistanceFieldLabel(): String {
        return if (selectedFtmsProfileKey == "ftms_bike") {
            "Current resistance"
        } else {
            "Resistance"
        }
    }

    private fun setupDeviceStateObservers() {
        viewModel.controlHasBeenTakenInControlPoint.observe(this) { updateDeviceStatePanel() }
        viewModel.ftmsControlOwnerAddress.observe(this) { updateDeviceStatePanel() }
        viewModel.ftmsControlProcedureState.observe(this) { updateDeviceStatePanel() }
        viewModel.fitnessMachineHasBeenStarted.observe(this) { updateDeviceStatePanel() }
        viewModel.selectedFitnessMachineMode.observe(this) { updateDeviceStatePanel() }
        viewModel.ftmsTargetState.observe(this) { updateDeviceStatePanel() }
        viewModel.ftmsTrainingStatus.observe(this) { updateDeviceStatePanel() }
        viewModel.ftmsSessionDurationMinutes.observe(this) { minutes ->
            if (binding.sliderTargetDuration.value != minutes) {
                binding.sliderTargetDuration.value = minutes
            }
        }
        viewModel.ftmsMeasurementSnapshot.observe(this, ::renderFtmsMeasurementSnapshot)
        viewModel.ftmsProtocolEvents.observe(this, ::renderFtmsProtocolEvents)
        viewModel.sensorMeasurementSnapshots.observe(this, ::renderSensorMeasurementSnapshots)
        viewModel.sensorProtocolEvents.observe(this, ::renderSensorProtocolEvents)
    }

    private fun updateDeviceStatePanel() {
        val controlTaken = viewModel.controlHasBeenTakenInControlPoint.value == true
        val workoutStarted = viewModel.fitnessMachineHasBeenStarted.value == true
        val controlPointEnabled = viewModel.ftmsControlPointEnabled
        val machineName = selectedMachineName()

        binding.textDeviceStateTitle.text = when {
            !ftmsEnabled -> "FTMS disabled"
            workoutStarted -> "Workout running"
            else -> "Workout idle"
        }
        binding.textDeviceStateSubtitle.text = when {
            !ftmsEnabled -> "Fitness Machine Service is not in the active GATT stack. Optional services can still advertise independently."
            !controlPointEnabled -> "FTMS Control Point is disabled. Clients can still read features and subscribe to measurement data."
            controlTaken && workoutStarted -> "A client owns the FTMS Control Point and the machine is publishing workout data."
            controlTaken -> "A client owns the FTMS Control Point. Waiting for Start or Resume."
            else -> "Control Point is available. No client owns the machine yet."
        }
        binding.textControlPointState.text = when {
            !controlPointEnabled -> "Disabled"
            controlTaken -> "Taken · ${viewModel.ftmsControlOwnerAddress.value ?: "client"}"
            else -> "Available"
        }
        binding.textWorkoutState.text = if (workoutStarted) "Started" else "Idle"
        binding.textTrainingStatusState.text = when (viewModel.ftmsTrainingStatus.value) {
            0x0D -> "Manual Mode"
            0x01 -> "Idle"
            else -> "0x${viewModel.ftmsTrainingStatus.value?.toString(16)?.uppercase() ?: "??"}"
        }
        binding.textMachineModeState.text = machineName
        val targets = viewModel.ftmsTargetState.value ?: FtmsTargetState()
        binding.textProcedureState.text = targets.summary()
        binding.textControlProcedureState.text = when {
            !controlPointEnabled -> "Control procedure: disabled"
            else -> "Control procedure: ${viewModel.ftmsControlProcedureState.value ?: "Idle"}"
        }
        binding.textIndoorBikeSimulationState.text = targets.indoorBikeSimulation?.summary()
            ?: "No bike simulation target received"
        updateFtmsDurationLabel()
        binding.switchFtmsControlPoint.text = if (controlPointEnabled) {
            "Control Point characteristic enabled"
        } else {
            "Control Point characteristic disabled"
        }
    }

    private fun updateProfileButtons() {
        binding.spinnerFtmsMachine.alpha = if (ftmsEnabled) 1f else 0.55f
        binding.layoutFtmsLiveData.alpha = if (ftmsEnabled) 1f else 0.58f
        binding.layoutCscLiveData.alpha = if (selectedOptionalServiceKeys.contains("csc")) 1f else 0.58f
        binding.layoutCyclingPowerLiveData.alpha =
            if (selectedOptionalServiceKeys.contains("cycling_power")) 1f else 0.58f
        binding.layoutRscLiveData.alpha = if (selectedOptionalServiceKeys.contains("rsc")) 1f else 0.58f
        updateProfileRowSummaries()
    }

    private fun updateProfileRowSummaries() {
        refreshServiceProfileList()
    }

    private fun serviceStateLabel(key: String): String {
        return if (selectedOptionalServiceKeys.contains(key)) "On" else "Off"
    }

    private fun ftmsSummary(): String {
        return "${if (ftmsEnabled) "On" else "Off"} | ${selectedFtmsMachineListLabel()} | " +
            "${binding.sliderPower.value.toInt()} W | ${formatOne(binding.sliderSpeed.value)} km/h"
    }

    private fun cscSummary(): String {
        val configuration = viewModel.cscConfiguration
        return "${serviceStateLabel("csc")} | ${formatOne(binding.sliderCscSpeed.value)} km/h | " +
            "${binding.sliderCscCadence.value.toInt()} rpm | declared 0x${configuration.featureBitsForGatt().toString(16).uppercase()} / actual 0x${configuration.actualFeatureBits().toString(16).uppercase()}"
    }

    private fun cyclingPowerSummary(): String {
        val configuration = viewModel.cyclingPowerConfiguration
        return "${serviceStateLabel("cycling_power")} | ${binding.sliderCyclingPower.value.toInt()} W | " +
            "${formatOne(binding.sliderCyclingPowerSpeed.value)} km/h | " +
            "${binding.sliderCyclingPowerCadence.value.toInt()} rpm | declared 0x${configuration.featureBitsForGatt().toUInt().toString(16).uppercase()} / actual 0x${configuration.actualFeatureBits().toUInt().toString(16).uppercase()}"
    }

    private fun rscSummary(): String {
        val configuration = viewModel.rscConfiguration
        return "${serviceStateLabel("rsc")} | ${formatOne(binding.sliderRscSpeed.value)} m/s | " +
            "${binding.sliderRscCadence.value.toInt()} spm | declared 0x${configuration.featureBitsForGatt().toString(16).uppercase()} / actual 0x${configuration.actualFeatureBits().toString(16).uppercase()}"
    }

    private fun heartRateSummary(): String {
        val configuration = viewModel.heartRateConfiguration
        return "${serviceStateLabel("heart_rate")} | ${configuration.heartRateBpm} bpm | " +
            "${if (configuration.includeEnergyExpended) "energy" else "no energy"} | " +
            "${if (configuration.includeRrIntervals) "RR" else "no RR"}"
    }

    private fun selectedFtmsMachineLabel(): String {
        return ftmsMachineOptions.firstOrNull { option -> option.profileKey == selectedFtmsProfileKey }?.label
            ?: selectedFtmsProfileKey
    }

    private fun selectedFtmsMachineListLabel(): String {
        return when (selectedFtmsProfileKey) {
            "ftms_bike" -> "Bike"
            "ftms_rower" -> "Rower"
            "ftms_treadmill" -> "Treadmill"
            "ftms_cross" -> "Cross"
            "ftms_step" -> "Step"
            "ftms_stair" -> "Stair"
            else -> selectedFtmsMachineLabel()
        }
    }

    private fun renderFtmsMeasurementSnapshot(snapshot: FtmsMeasurementSnapshot) {
        updateIndoorBikeComputedSummary()
        val recordBytes = if (snapshot.logicalRecordHex.isBlank()) {
            0
        } else {
            snapshot.logicalRecordHex.split(' ').size
        }
        binding.textFtmsMeasurementPacketTitle.text =
            "MEASUREMENT PACKET PREVIEW · $recordBytes B RECORD · ${snapshot.notificationPacketHex.size} ATT PACKET(S)"
        val shortUuid = snapshot.characteristicUuid.substringBefore('-').uppercase()
        val packetLines = snapshot.notificationPacketHex.mapIndexed { index, packet ->
            "P${index + 1}/${snapshot.notificationPacketHex.size}  $packet"
        }
        binding.textFtmsMeasurementPackets.text = buildString {
            append("$shortUuid · ${snapshot.previewPayloadBytes}-byte ATT payload preview\n")
            append("LOGICAL  ${snapshot.logicalRecordHex}\n")
            append(packetLines.joinToString("\n"))
        }
    }

    private fun renderFtmsProtocolEvents(events: List<FtmsProtocolEvent>) {
        if (events.isEmpty()) {
            binding.textFtmsProtocolEvents.text =
                "No Control Point, Fitness Machine Status, or Training Status traffic yet."
            return
        }
        val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        binding.textFtmsProtocolEvents.text = events.take(12).joinToString("\n\n") { event ->
            val timestamp = formatter.format(Date(event.timestampMillis))
            "$timestamp  ${event.direction} ${event.channel}  ${event.title}\n${event.payloadHex}"
        }
    }

    private fun renderSensorMeasurementSnapshots(
        snapshots: Map<String, SensorMeasurementSnapshot>
    ) {
        binding.textRscMeasurementPreview.text = snapshots["rsc"]
            ?.let(::formatSensorMeasurementSnapshot)
            ?: "Waiting for RSC Measurement…"
        binding.textCscMeasurementPreview.text = snapshots["csc"]
            ?.let(::formatSensorMeasurementSnapshot)
            ?: "Waiting for CSC Measurement…"

        val cyclingPowerMeasurement = snapshots["cycling_power"]
        val cyclingPowerVector = snapshots["cycling_power_vector"]
        binding.textCyclingPowerMeasurementPreview.text = buildString {
            append(
                cyclingPowerMeasurement?.let(::formatSensorMeasurementSnapshot)
                    ?: "Waiting for Cycling Power Measurement…"
            )
            if (cyclingPowerVector != null) {
                append("\n\nVECTOR\n")
                append(formatSensorMeasurementSnapshot(cyclingPowerVector))
            }
        }
    }

    private fun formatSensorMeasurementSnapshot(snapshot: SensorMeasurementSnapshot): String {
        val shortUuid = snapshot.characteristicUuid.substringBefore('-').uppercase()
        return buildString {
            append("$shortUuid · ${snapshot.logicalRecordHex.byteCountFromHex()} B logical · ")
            append("${snapshot.notificationPacketHex.size} packet(s) · ${snapshot.payloadBytes} B payload\n")
            append("LOGICAL  ${snapshot.logicalRecordHex}")
            snapshot.notificationPacketHex.forEachIndexed { index, packet ->
                append("\nP${index + 1}/${snapshot.notificationPacketHex.size}     $packet")
            }
        }
    }

    private fun renderSensorProtocolEvents(
        eventsByService: Map<String, List<SensorProtocolEvent>>
    ) {
        binding.textRscProtocolEvents.text = formatSensorProtocolEvents(
            eventsByService["rsc"].orEmpty(),
            "No RSC Control Point traffic yet."
        )
        binding.textCscProtocolEvents.text = formatSensorProtocolEvents(
            eventsByService["csc"].orEmpty(),
            "No CSC Control Point traffic yet."
        )
        binding.textCyclingPowerProtocolEvents.text = formatSensorProtocolEvents(
            eventsByService["cycling_power"].orEmpty(),
            "No Cycling Power Control Point traffic yet."
        )
    }

    private fun formatSensorProtocolEvents(
        events: List<SensorProtocolEvent>,
        emptyMessage: String
    ): String {
        if (events.isEmpty()) return emptyMessage
        val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return events.take(10).joinToString("\n\n") { event ->
            val timestamp = formatter.format(Date(event.timestampMillis))
            "$timestamp  ${event.direction}  ${event.title}\n${event.payloadHex}"
        }
    }

    private fun String.byteCountFromHex(): Int = if (isBlank()) 0 else split(' ').size

    private fun renderMachineFieldControls() {
        val renderKey = "$ftmsEnabled:$selectedFtmsProfileKey:${viewModel.ftmsControlPointEnabled}:${viewModel.ftmsMirrorTargetFeatureDeclarations}"
        if (renderedMachineFieldKey == renderKey) {
            return
        }

        renderedMachineFieldKey = renderKey
        binding.layoutMachineFieldControls.visibility = View.GONE
        renderedFieldToggles.clear()
        binding.layoutMachineFieldToggleList.removeAllViews()
        binding.layoutControlCapabilityToggleList.removeAllViews()
        binding.layoutFtmsControlCapability.visibility = if (ftmsEnabled) View.VISIBLE else View.GONE

        if (!ftmsEnabled) {
            refreshServiceProfileList()
            return
        }

        val toggles = machineFieldToggles(selectedFtmsProfileKey)
        val measurementToggles = toggles.filter { toggle -> toggle.labelPrefix == "Feature/data" }
        val controlToggles = toggles.filterNot { toggle -> toggle.labelPrefix == "Feature/data" }

        if (measurementToggles.isNotEmpty()) {
            binding.layoutMachineFieldControls.visibility = View.VISIBLE
            renderFieldToggleGroups(binding.layoutMachineFieldToggleList, measurementToggles)
        }
        renderFieldToggleGroups(binding.layoutControlCapabilityToggleList, controlToggles)
        refreshServiceProfileList()
    }

    private fun renderFieldToggleGroups(container: LinearLayout, toggles: List<FieldToggle>) {
        val orderedToggles = toggles.filter { toggle -> toggle.isCapabilityMode() } +
            toggles.filterNot { toggle -> toggle.isCapabilityMode() }
        val clusters = linkedMapOf<String, MutableList<FieldToggle>>()

        orderedToggles.forEachIndexed { index, toggle ->
            val key = if (toggle.isCapabilityPairCandidate()) {
                "capability:${toggle.label}"
            } else {
                "single:$index:${toggle.labelPrefix}:${toggle.label}"
            }
            clusters.getOrPut(key) { mutableListOf() }.add(toggle)
        }

        clusters.values.forEach { relatedToggles ->
            val sortedToggles = relatedToggles.sortedBy { toggle -> toggle.visualOrder() }
            if (sortedToggles.size > 1 && sortedToggles.any { toggle -> toggle.isCapabilityPairCandidate() }) {
                container.addView(createFieldToggleCluster(sortedToggles))
            } else {
                sortedToggles.forEach { toggle ->
                    container.addView(createFieldSwitch(toggle))
                }
            }
        }
    }

    private fun createFieldToggleCluster(toggles: List<FieldToggle>): LinearLayout {
        val headingToggle = toggles.firstOrNull { toggle -> toggle.labelPrefix == "Actual control" } ?: toggles.first()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { params ->
                params.topMargin = dp(4)
            }

            addDivider(this)

            addView(TextView(this@MainActivity).apply {
                text = headingToggle.label
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink_muted))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.04f
                isAllCaps = true
            })

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { params ->
                    params.topMargin = dp(4)
                }

                toggles.forEachIndexed { index, toggle ->
                    addView(createFieldSwitch(toggle, labelOverride = toggle.labelPrefix, topMarginDp = 0).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).also { params ->
                            if (index > 0) {
                                params.marginStart = dp(10)
                            }
                        }
                    })
                }
            })
        }
    }

    private fun createFieldSwitch(
        toggle: FieldToggle,
        labelOverride: String? = null,
        topMarginDp: Int = 4
    ): SwitchMaterial {
        return SwitchMaterial(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { params ->
                params.topMargin = dp(topMarginDp)
            }
            text = labelOverride ?: "${toggle.labelPrefix}: ${toggle.label}"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink))
            styleSimulatorSwitch(this)
            isEnabled = toggle.enabled()
            alpha = if (isEnabled) 1f else 0.55f
            isChecked = toggle.checked()
            setOnCheckedChangeListener { _, isChecked ->
                if (!suppressFieldToggleCallback) {
                    handleFieldToggleChanged(toggle, isChecked)
                }
            }
            renderedFieldToggles.add(RenderedFieldToggle(this, toggle))
        }
    }

    private fun handleFieldToggleChanged(toggle: FieldToggle, isChecked: Boolean) {
        toggle.update(isChecked)
        if (viewModel.ftmsMirrorTargetFeatureDeclarations) {
            viewModel.syncFtmsDeclaredTargetFeaturesFromActual()
        }
        refreshRenderedFieldToggles()
        updateSimulationValuesFromControls()
        if (toggle.requiresGattRebuild && ::applicationState.isInitialized) {
            renderedMachineFieldKey = null
            applySelectedServiceStack(rebuildGatt = true, logChange = false)
            renderMachineFieldControls()
        }
    }

    private fun refreshRenderedFieldToggles() {
        suppressFieldToggleCallback = true
        try {
            renderedFieldToggles.forEach { renderedToggle ->
                val enabled = renderedToggle.toggle.enabled()
                renderedToggle.switchView.isEnabled = enabled
                renderedToggle.switchView.alpha = if (enabled) 1f else 0.55f
                renderedToggle.switchView.isChecked = renderedToggle.toggle.checked()
            }
        } finally {
            suppressFieldToggleCallback = false
        }
    }

    private fun styleSimulatorSwitch(switch: SwitchMaterial) {
        switch.textSize = 14f
        switch.minHeight = dp(44)
        switch.minimumHeight = dp(44)
        switch.setPadding(0, dp(3), 0, dp(3))
        switch.setSwitchMinWidth(dp(54))
    }

    private fun FieldToggle.isCapabilityMode(): Boolean = labelPrefix == "Capability mode"

    private fun FieldToggle.isCapabilityPairCandidate(): Boolean {
        return labelPrefix == "Actual control" || labelPrefix == "Declared bit"
    }

    private fun FieldToggle.visualOrder(): Int {
        return when (labelPrefix) {
            "Capability mode" -> 0
            "Feature/data" -> 1
            "Actual control" -> 2
            "Declared bit" -> 3
            else -> 4
        }
    }

    private fun toggle(
        group: FieldToggleGroup,
        label: String,
        checked: () -> Boolean,
        update: (Boolean) -> Unit
    ): FieldToggle {
        return FieldToggle(
            group = group,
            label = label,
            checked = checked,
            update = update,
            requiresGattRebuild = true,
            labelPrefix = "Feature/data"
        )
    }

    private fun targetToggle(
        group: FieldToggleGroup,
        label: String,
        checked: () -> Boolean,
        update: (Boolean) -> Unit
    ): FieldToggle {
        return FieldToggle(
            group = group,
            label = label,
            checked = checked,
            update = update,
            requiresGattRebuild = true,
            labelPrefix = "Actual control",
            enabled = { viewModel.ftmsControlPointEnabled }
        )
    }

    private fun declaredTargetToggle(
        group: FieldToggleGroup,
        label: String,
        checked: () -> Boolean,
        update: (Boolean) -> Unit
    ): FieldToggle {
        return FieldToggle(
            group = group,
            label = label,
            checked = checked,
            update = update,
            requiresGattRebuild = true,
            labelPrefix = "Declared bit",
            enabled = { viewModel.ftmsControlPointEnabled && !viewModel.ftmsMirrorTargetFeatureDeclarations }
        )
    }

    private fun declarationMirrorToggle(): FieldToggle {
        return FieldToggle(
            group = FieldToggleGroup.SPEED,
            label = "Mirror declared capability bits from actual controls",
            checked = { viewModel.ftmsMirrorTargetFeatureDeclarations },
            update = { enabled ->
                viewModel.ftmsMirrorTargetFeatureDeclarations = enabled
                if (enabled) {
                    viewModel.syncFtmsDeclaredTargetFeaturesFromActual()
                }
            },
            requiresGattRebuild = true,
            labelPrefix = "Capability mode",
            enabled = { viewModel.ftmsControlPointEnabled }
        )
    }

    private fun machineFieldToggles(profileKey: String): List<FieldToggle> {
        val machineToggles = when (profileKey) {
            "ftms_bike" -> indoorBikeFieldToggles()
            "ftms_treadmill" -> treadmillFieldToggles()
            "ftms_rower" -> rowerFieldToggles()
            "ftms_cross" -> crossTrainerFieldToggles()
            "ftms_step" -> stepClimberFieldToggles()
            "ftms_stair" -> stairClimberFieldToggles()
            else -> emptyList()
        }
        val actualToggleGroups = machineToggles
            .filter { toggle -> toggle.labelPrefix == "Actual control" }
            .associate { toggle -> toggle.label to toggle.group }
        val declaredToggles = declaredCapabilityBitToggles(profileKey).map { toggle ->
            val matchingGroup = actualToggleGroups[toggle.label]
            if (toggle.labelPrefix == "Declared bit" && matchingGroup != null) {
                toggle.copy(group = matchingGroup)
            } else {
                toggle
            }
        }
        return machineToggles + declaredToggles
    }

    private fun declaredCapabilityBitToggles(profileKey: String): List<FieldToggle> {
        val declared = viewModel.ftmsDeclaredTargetFeatures
        val allProfiles = setOf("ftms_bike", "ftms_treadmill", "ftms_rower", "ftms_cross", "ftms_step", "ftms_stair")
        val bikeProfiles = setOf("ftms_bike")
        val speedProfiles = setOf("ftms_treadmill", "ftms_cross")
        val distanceProfiles = setOf("ftms_bike", "ftms_treadmill", "ftms_rower", "ftms_cross")
        val cadenceProfiles = setOf("ftms_bike", "ftms_rower", "ftms_cross")
        val stepProfiles = setOf("ftms_cross", "ftms_step")
        val strideProfiles = setOf("ftms_cross", "ftms_stair")
        val powerProfiles = setOf("ftms_bike", "ftms_treadmill", "ftms_rower", "ftms_cross")
        val resistanceProfiles = setOf("ftms_bike", "ftms_rower", "ftms_cross")
        val inclinationProfiles = setOf("ftms_treadmill", "ftms_cross")
        val cadenceTargetLabel = if (profileKey == "ftms_rower") "Target Stroke Rate" else "Target Cadence"
        val declaredBits = listOf(
            speedProfiles to declaredTargetToggle(FieldToggleGroup.SPEED, "Target Speed", { declared.targetSpeed }, { declared.targetSpeed = it }),
            distanceProfiles to declaredTargetToggle(FieldToggleGroup.SPEED, "Target Distance", { declared.targetedDistance }, { declared.targetedDistance = it }),
            bikeProfiles to declaredTargetToggle(FieldToggleGroup.SPEED, "Wheel Circumference", { declared.wheelCircumference }, { declared.wheelCircumference = it }),
            bikeProfiles to declaredTargetToggle(FieldToggleGroup.SPEED, "Spin Down Control", { declared.spinDownControl }, { declared.spinDownControl = it }),
            cadenceProfiles to declaredTargetToggle(FieldToggleGroup.CADENCE, cadenceTargetLabel, { declared.targetedCadence }, { declared.targetedCadence = it }),
            stepProfiles to declaredTargetToggle(FieldToggleGroup.CADENCE, "Target Steps", { declared.targetedSteps }, { declared.targetedSteps = it }),
            strideProfiles to declaredTargetToggle(FieldToggleGroup.CADENCE, "Target Strides", { declared.targetedStrides }, { declared.targetedStrides = it }),
            powerProfiles to declaredTargetToggle(FieldToggleGroup.POWER, "Target Power", { declared.targetPower }, { declared.targetPower = it }),
            allProfiles to declaredTargetToggle(FieldToggleGroup.POWER, "Target Expended Energy", { declared.targetedExpendedEnergy }, { declared.targetedExpendedEnergy = it }),
            resistanceProfiles to declaredTargetToggle(FieldToggleGroup.RESISTANCE, "Target Resistance", { declared.targetResistanceLevel }, { declared.targetResistanceLevel = it }),
            inclinationProfiles to declaredTargetToggle(FieldToggleGroup.INCLINATION, "Target Inclination", { declared.targetInclination }, { declared.targetInclination = it }),
            bikeProfiles to declaredTargetToggle(FieldToggleGroup.INCLINATION, "Indoor Bike Simulation Parameters", { declared.indoorBikeSimulationParameters }, { declared.indoorBikeSimulationParameters = it }),
            allProfiles to declaredTargetToggle(FieldToggleGroup.HEART_RATE, "Target Heart Rate", { declared.targetHeartRate }, { declared.targetHeartRate = it }),
            allProfiles to declaredTargetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (2)", { declared.targetedTimeInTwoHeartRateZones }, { declared.targetedTimeInTwoHeartRateZones = it }),
            allProfiles to declaredTargetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (3)", { declared.targetedTimeInThreeHeartRateZones }, { declared.targetedTimeInThreeHeartRateZones = it }),
            allProfiles to declaredTargetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (5)", { declared.targetedTimeInFiveHeartRateZones }, { declared.targetedTimeInFiveHeartRateZones = it }),
            allProfiles to declaredTargetToggle(FieldToggleGroup.DURATION, "Target Training Time", { declared.targetedTrainingTime }, { declared.targetedTrainingTime = it })
        )
        val visibleDeclaredBits = declaredBits
            .filter { (profiles, _) ->
                profiles.contains(profileKey)
            }
            .map { (_, toggle) -> toggle }
        return listOf(declarationMirrorToggle()) + visibleDeclaredBits
    }

    private fun indoorBikeFieldToggles(): List<FieldToggle> {
        val data = viewModel.ftmsIndoorBikeData
        val targets = viewModel.ftmsTargetFeatures
        return listOf(
            toggle(FieldToggleGroup.SPEED, "Average Speed", { data.includeAverageSpeed }, { data.includeAverageSpeed = it }),
            toggle(FieldToggleGroup.SPEED, "Total Distance", { data.includeTotalDistance }, { data.includeTotalDistance = it }),
            toggle(FieldToggleGroup.CADENCE, "Instantaneous Cadence", { data.includeInstantaneousCadence }, { data.includeInstantaneousCadence = it }),
            toggle(FieldToggleGroup.CADENCE, "Average Cadence", { data.includeAverageCadence }, { data.includeAverageCadence = it }),
            targetToggle(FieldToggleGroup.CADENCE, "Target Cadence", { targets.targetedCadence }, { targets.targetedCadence = it }),
            toggle(FieldToggleGroup.POWER, "Instantaneous Power", { data.includeInstantaneousPower }, { data.includeInstantaneousPower = it }),
            toggle(FieldToggleGroup.POWER, "Average Power", { data.includeAveragePower }, { data.includeAveragePower = it }),
            toggle(FieldToggleGroup.POWER, "Expended Energy", { data.includeExpendedEnergy }, { data.includeExpendedEnergy = it }),
            targetToggle(FieldToggleGroup.POWER, "Target Power", { targets.targetPower }, { targets.targetPower = it }),
            targetToggle(FieldToggleGroup.POWER, "Target Expended Energy", { targets.targetedExpendedEnergy }, { targets.targetedExpendedEnergy = it }),
            toggle(FieldToggleGroup.RESISTANCE, "Resistance Level", { data.includeResistanceLevel }, { data.includeResistanceLevel = it }),
            targetToggle(FieldToggleGroup.RESISTANCE, "Target Resistance", { targets.targetResistanceLevel }, { targets.targetResistanceLevel = it }),
            targetToggle(FieldToggleGroup.INCLINATION, "Indoor Bike Simulation Parameters", { targets.indoorBikeSimulationParameters }, { targets.indoorBikeSimulationParameters = it }),
            targetToggle(FieldToggleGroup.SPEED, "Wheel Circumference", { targets.wheelCircumference }, { targets.wheelCircumference = it }),
            targetToggle(FieldToggleGroup.SPEED, "Spin Down Control", { targets.spinDownControl }, { targets.spinDownControl = it }),
            toggle(FieldToggleGroup.METABOLIC, "Metabolic Equivalent", { data.includeMetabolicEquivalent }, { data.includeMetabolicEquivalent = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target Heart Rate", { targets.targetHeartRate }, { targets.targetHeartRate = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (2)", { targets.targetedTimeInTwoHeartRateZones }, { targets.targetedTimeInTwoHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (3)", { targets.targetedTimeInThreeHeartRateZones }, { targets.targetedTimeInThreeHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (5)", { targets.targetedTimeInFiveHeartRateZones }, { targets.targetedTimeInFiveHeartRateZones = it }),
            targetToggle(FieldToggleGroup.SPEED, "Target Distance", { targets.targetedDistance }, { targets.targetedDistance = it }),
            toggle(FieldToggleGroup.DURATION, "Elapsed Time", { data.includeElapsedTime }, { data.includeElapsedTime = it }),
            toggle(FieldToggleGroup.DURATION, "Remaining Time", { data.includeRemainingTime }, { data.includeRemainingTime = it }),
            targetToggle(FieldToggleGroup.DURATION, "Target Training Time", { targets.targetedTrainingTime }, { targets.targetedTrainingTime = it })
        )
    }

    private fun treadmillFieldToggles(): List<FieldToggle> {
        val data = viewModel.ftmsTreadmillData
        val targets = viewModel.ftmsTargetFeatures
        return listOf(
            toggle(FieldToggleGroup.SPEED, "Average Speed", { data.includeAverageSpeed }, { data.includeAverageSpeed = it }),
            toggle(FieldToggleGroup.SPEED, "Total Distance", { data.includeTotalDistance }, { data.includeTotalDistance = it }),
            toggle(FieldToggleGroup.SPEED, "Instantaneous Pace", { data.includeInstantaneousPace }, { data.includeInstantaneousPace = it }),
            toggle(FieldToggleGroup.SPEED, "Average Pace", { data.includeAveragePace }, { data.includeAveragePace = it }),
            targetToggle(FieldToggleGroup.SPEED, "Target Speed", { targets.targetSpeed }, { targets.targetSpeed = it }),
            targetToggle(FieldToggleGroup.SPEED, "Target Distance", { targets.targetedDistance }, { targets.targetedDistance = it }),
            toggle(FieldToggleGroup.INCLINATION, "Inclination + Ramp Angle", { data.includeInclination }, { data.includeInclination = it }),
            toggle(FieldToggleGroup.INCLINATION, "Elevation Gain", { data.includeElevationGain }, { data.includeElevationGain = it }),
            targetToggle(FieldToggleGroup.INCLINATION, "Target Inclination", { targets.targetInclination }, { targets.targetInclination = it }),
            toggle(FieldToggleGroup.POWER, "Expended Energy", { data.includeExpendedEnergy }, { data.includeExpendedEnergy = it }),
            toggle(FieldToggleGroup.POWER, "Force on Belt + Power Output", { data.includeForceOnBeltAndPowerOutput }, { data.includeForceOnBeltAndPowerOutput = it }),
            targetToggle(FieldToggleGroup.POWER, "Target Power", { targets.targetPower }, { targets.targetPower = it }),
            targetToggle(FieldToggleGroup.POWER, "Target Expended Energy", { targets.targetedExpendedEnergy }, { targets.targetedExpendedEnergy = it }),
            toggle(FieldToggleGroup.METABOLIC, "Metabolic Equivalent", { data.includeMetabolicEquivalent }, { data.includeMetabolicEquivalent = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target Heart Rate", { targets.targetHeartRate }, { targets.targetHeartRate = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (2)", { targets.targetedTimeInTwoHeartRateZones }, { targets.targetedTimeInTwoHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (3)", { targets.targetedTimeInThreeHeartRateZones }, { targets.targetedTimeInThreeHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (5)", { targets.targetedTimeInFiveHeartRateZones }, { targets.targetedTimeInFiveHeartRateZones = it }),
            toggle(FieldToggleGroup.DURATION, "Elapsed Time", { data.includeElapsedTime }, { data.includeElapsedTime = it }),
            toggle(FieldToggleGroup.DURATION, "Remaining Time", { data.includeRemainingTime }, { data.includeRemainingTime = it }),
            targetToggle(FieldToggleGroup.DURATION, "Target Training Time", { targets.targetedTrainingTime }, { targets.targetedTrainingTime = it })
        )
    }

    private fun rowerFieldToggles(): List<FieldToggle> {
        val data = viewModel.ftmsRowerData
        val targets = viewModel.ftmsTargetFeatures
        return listOf(
            toggle(FieldToggleGroup.CADENCE, "Average Stroke Rate", { data.includeAverageStrokeRate }, { data.includeAverageStrokeRate = it }),
            toggle(FieldToggleGroup.CADENCE, "Total Distance", { data.includeTotalDistance }, { data.includeTotalDistance = it }),
            toggle(FieldToggleGroup.CADENCE, "Instantaneous Pace", { data.includeInstantaneousPace }, { data.includeInstantaneousPace = it }),
            toggle(FieldToggleGroup.CADENCE, "Average Pace", { data.includeAveragePace }, { data.includeAveragePace = it }),
            targetToggle(FieldToggleGroup.CADENCE, "Target Stroke Rate", { targets.targetedCadence }, { targets.targetedCadence = it }),
            targetToggle(FieldToggleGroup.CADENCE, "Target Distance", { targets.targetedDistance }, { targets.targetedDistance = it }),
            toggle(FieldToggleGroup.POWER, "Instantaneous Power", { data.includeInstantaneousPower }, { data.includeInstantaneousPower = it }),
            toggle(FieldToggleGroup.POWER, "Average Power", { data.includeAveragePower }, { data.includeAveragePower = it }),
            toggle(FieldToggleGroup.POWER, "Expended Energy", { data.includeExpendedEnergy }, { data.includeExpendedEnergy = it }),
            targetToggle(FieldToggleGroup.POWER, "Target Power", { targets.targetPower }, { targets.targetPower = it }),
            targetToggle(FieldToggleGroup.POWER, "Target Expended Energy", { targets.targetedExpendedEnergy }, { targets.targetedExpendedEnergy = it }),
            toggle(FieldToggleGroup.RESISTANCE, "Resistance Level", { data.includeResistanceLevel }, { data.includeResistanceLevel = it }),
            targetToggle(FieldToggleGroup.RESISTANCE, "Target Resistance", { targets.targetResistanceLevel }, { targets.targetResistanceLevel = it }),
            toggle(FieldToggleGroup.METABOLIC, "Metabolic Equivalent", { data.includeMetabolicEquivalent }, { data.includeMetabolicEquivalent = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target Heart Rate", { targets.targetHeartRate }, { targets.targetHeartRate = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (2)", { targets.targetedTimeInTwoHeartRateZones }, { targets.targetedTimeInTwoHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (3)", { targets.targetedTimeInThreeHeartRateZones }, { targets.targetedTimeInThreeHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (5)", { targets.targetedTimeInFiveHeartRateZones }, { targets.targetedTimeInFiveHeartRateZones = it }),
            toggle(FieldToggleGroup.DURATION, "Elapsed Time", { data.includeElapsedTime }, { data.includeElapsedTime = it }),
            toggle(FieldToggleGroup.DURATION, "Remaining Time", { data.includeRemainingTime }, { data.includeRemainingTime = it }),
            targetToggle(FieldToggleGroup.DURATION, "Target Training Time", { targets.targetedTrainingTime }, { targets.targetedTrainingTime = it })
        )
    }

    private fun crossTrainerFieldToggles(): List<FieldToggle> {
        val data = viewModel.ftmsCrossTrainerData
        val targets = viewModel.ftmsTargetFeatures
        return listOf(
            toggle(FieldToggleGroup.SPEED, "Average Speed", { data.includeAverageSpeed }, { data.includeAverageSpeed = it }),
            toggle(FieldToggleGroup.SPEED, "Total Distance", { data.includeTotalDistance }, { data.includeTotalDistance = it }),
            targetToggle(FieldToggleGroup.SPEED, "Target Speed", { targets.targetSpeed }, { targets.targetSpeed = it }),
            targetToggle(FieldToggleGroup.SPEED, "Target Distance", { targets.targetedDistance }, { targets.targetedDistance = it }),
            toggle(FieldToggleGroup.CADENCE, "Step Rate + Average Step Rate", { data.includeStepCount }, { data.includeStepCount = it }),
            toggle(FieldToggleGroup.CADENCE, "Stride Count", { data.includeStrideCount }, { data.includeStrideCount = it }),
            toggle(FieldToggleGroup.CADENCE, "Movement Direction Backward", { data.movementDirectionBackward }, { data.movementDirectionBackward = it }),
            targetToggle(FieldToggleGroup.CADENCE, "Target Cadence", { targets.targetedCadence }, { targets.targetedCadence = it }),
            targetToggle(FieldToggleGroup.CADENCE, "Target Steps", { targets.targetedSteps }, { targets.targetedSteps = it }),
            targetToggle(FieldToggleGroup.CADENCE, "Target Strides", { targets.targetedStrides }, { targets.targetedStrides = it }),
            toggle(FieldToggleGroup.INCLINATION, "Inclination + Ramp Angle", { data.includeInclination }, { data.includeInclination = it }),
            toggle(FieldToggleGroup.INCLINATION, "Elevation Gain", { data.includeElevationGain }, { data.includeElevationGain = it }),
            targetToggle(FieldToggleGroup.INCLINATION, "Target Inclination", { targets.targetInclination }, { targets.targetInclination = it }),
            toggle(FieldToggleGroup.RESISTANCE, "Resistance Level", { data.includeResistanceLevel }, { data.includeResistanceLevel = it }),
            targetToggle(FieldToggleGroup.RESISTANCE, "Target Resistance", { targets.targetResistanceLevel }, { targets.targetResistanceLevel = it }),
            toggle(FieldToggleGroup.POWER, "Instantaneous Power", { data.includeInstantaneousPower }, { data.includeInstantaneousPower = it }),
            toggle(FieldToggleGroup.POWER, "Average Power", { data.includeAveragePower }, { data.includeAveragePower = it }),
            toggle(FieldToggleGroup.POWER, "Expended Energy", { data.includeExpendedEnergy }, { data.includeExpendedEnergy = it }),
            targetToggle(FieldToggleGroup.POWER, "Target Power", { targets.targetPower }, { targets.targetPower = it }),
            targetToggle(FieldToggleGroup.POWER, "Target Expended Energy", { targets.targetedExpendedEnergy }, { targets.targetedExpendedEnergy = it }),
            toggle(FieldToggleGroup.METABOLIC, "Metabolic Equivalent", { data.includeMetabolicEquivalent }, { data.includeMetabolicEquivalent = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target Heart Rate", { targets.targetHeartRate }, { targets.targetHeartRate = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (2)", { targets.targetedTimeInTwoHeartRateZones }, { targets.targetedTimeInTwoHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (3)", { targets.targetedTimeInThreeHeartRateZones }, { targets.targetedTimeInThreeHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (5)", { targets.targetedTimeInFiveHeartRateZones }, { targets.targetedTimeInFiveHeartRateZones = it }),
            toggle(FieldToggleGroup.DURATION, "Elapsed Time", { data.includeElapsedTime }, { data.includeElapsedTime = it }),
            toggle(FieldToggleGroup.DURATION, "Remaining Time", { data.includeRemainingTime }, { data.includeRemainingTime = it }),
            targetToggle(FieldToggleGroup.DURATION, "Target Training Time", { targets.targetedTrainingTime }, { targets.targetedTrainingTime = it })
        )
    }

    private fun stepClimberFieldToggles(): List<FieldToggle> {
        val data = viewModel.ftmsStepClimberData
        val targets = viewModel.ftmsTargetFeatures
        return listOf(
            toggle(FieldToggleGroup.CADENCE, "Steps per Minute", { data.includeStepsPerMinute }, { data.includeStepsPerMinute = it }),
            toggle(FieldToggleGroup.CADENCE, "Average Step Rate", { data.includeAverageStepRate }, { data.includeAverageStepRate = it }),
            toggle(FieldToggleGroup.CADENCE, "Positive Elevation Gain", { data.includePositiveElevationGain }, { data.includePositiveElevationGain = it }),
            targetToggle(FieldToggleGroup.CADENCE, "Target Steps", { targets.targetedSteps }, { targets.targetedSteps = it }),
            toggle(FieldToggleGroup.DURATION, "Expended Energy", { data.includeExpendedEnergy }, { data.includeExpendedEnergy = it }),
            targetToggle(FieldToggleGroup.DURATION, "Target Expended Energy", { targets.targetedExpendedEnergy }, { targets.targetedExpendedEnergy = it }),
            toggle(FieldToggleGroup.METABOLIC, "Metabolic Equivalent", { data.includeMetabolicEquivalent }, { data.includeMetabolicEquivalent = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target Heart Rate", { targets.targetHeartRate }, { targets.targetHeartRate = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (2)", { targets.targetedTimeInTwoHeartRateZones }, { targets.targetedTimeInTwoHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (3)", { targets.targetedTimeInThreeHeartRateZones }, { targets.targetedTimeInThreeHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (5)", { targets.targetedTimeInFiveHeartRateZones }, { targets.targetedTimeInFiveHeartRateZones = it }),
            toggle(FieldToggleGroup.DURATION, "Elapsed Time", { data.includeElapsedTime }, { data.includeElapsedTime = it }),
            toggle(FieldToggleGroup.DURATION, "Remaining Time", { data.includeRemainingTime }, { data.includeRemainingTime = it }),
            targetToggle(FieldToggleGroup.DURATION, "Target Training Time", { targets.targetedTrainingTime }, { targets.targetedTrainingTime = it })
        )
    }

    private fun stairClimberFieldToggles(): List<FieldToggle> {
        val data = viewModel.ftmsStairClimberData
        val targets = viewModel.ftmsTargetFeatures
        return listOf(
            toggle(FieldToggleGroup.CADENCE, "Steps per Minute", { data.includeStepsPerMinute }, { data.includeStepsPerMinute = it }),
            toggle(FieldToggleGroup.CADENCE, "Average Step Rate", { data.includeAverageStepRate }, { data.includeAverageStepRate = it }),
            toggle(FieldToggleGroup.CADENCE, "Positive Elevation Gain", { data.includePositiveElevationGain }, { data.includePositiveElevationGain = it }),
            toggle(FieldToggleGroup.CADENCE, "Stride Count", { data.includeStrideCount }, { data.includeStrideCount = it }),
            targetToggle(FieldToggleGroup.CADENCE, "Target Strides", { targets.targetedStrides }, { targets.targetedStrides = it }),
            toggle(FieldToggleGroup.DURATION, "Expended Energy", { data.includeExpendedEnergy }, { data.includeExpendedEnergy = it }),
            targetToggle(FieldToggleGroup.DURATION, "Target Expended Energy", { targets.targetedExpendedEnergy }, { targets.targetedExpendedEnergy = it }),
            toggle(FieldToggleGroup.METABOLIC, "Metabolic Equivalent", { data.includeMetabolicEquivalent }, { data.includeMetabolicEquivalent = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target Heart Rate", { targets.targetHeartRate }, { targets.targetHeartRate = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (2)", { targets.targetedTimeInTwoHeartRateZones }, { targets.targetedTimeInTwoHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (3)", { targets.targetedTimeInThreeHeartRateZones }, { targets.targetedTimeInThreeHeartRateZones = it }),
            targetToggle(FieldToggleGroup.HEART_RATE, "Target HR Zones (5)", { targets.targetedTimeInFiveHeartRateZones }, { targets.targetedTimeInFiveHeartRateZones = it }),
            toggle(FieldToggleGroup.DURATION, "Elapsed Time", { data.includeElapsedTime }, { data.includeElapsedTime = it }),
            toggle(FieldToggleGroup.DURATION, "Remaining Time", { data.includeRemainingTime }, { data.includeRemainingTime = it }),
            targetToggle(FieldToggleGroup.DURATION, "Target Training Time", { targets.targetedTrainingTime }, { targets.targetedTrainingTime = it })
        )
    }

    private fun updateSimulationValuesFromControls(updateFtmsDuration: Boolean = false) {
        val cadenceMaximum = if (selectedFtmsProfileKey == "ftms_rower") 127f else 180f
        if (binding.sliderCadence.value > cadenceMaximum) {
            binding.sliderCadence.value = cadenceMaximum
        }
        binding.sliderCadence.valueTo = cadenceMaximum

        val ftmsSpeed = binding.sliderSpeed.value
        val ftmsCadence = binding.sliderCadence.value.toInt()
        val ftmsPower = binding.sliderPower.value.toInt()
        val ftmsResistance = binding.sliderResistance.value.toInt()
        val ftmsInclination = binding.sliderInclination.value
        val ftmsHeartRate = binding.sliderHeartRate.value.toInt()
        val ftmsMetabolicEquivalent = binding.sliderMetabolicEquivalent.value
        val ftmsTargetDurationMinutes = binding.sliderTargetDuration.value.toInt()
        val ftmsIncludeHeartRate = binding.switchHeartRate.isChecked
        if (updateFtmsDuration && viewModel.ftmsSessionDurationMinutes.value != binding.sliderTargetDuration.value) {
            viewModel.ftmsSessionDurationMinutes.value = binding.sliderTargetDuration.value
        }

        val cscSpeed = binding.sliderCscSpeed.value
        val cscCadence = binding.sliderCscCadence.value.toInt()
        val cyclingPower = binding.sliderCyclingPower.value.toInt()
        val cyclingPowerSpeed = binding.sliderCyclingPowerSpeed.value
        val cyclingPowerCadence = binding.sliderCyclingPowerCadence.value.toInt()
        val rscSpeed = binding.sliderRscSpeed.value
        val rscCadence = binding.sliderRscCadence.value.toInt()

        binding.labelSpeedValue.text = "Speed ${formatOne(ftmsSpeed)} km/h"
        binding.labelCadenceValue.text = "${ftmsCadenceFieldLabel()} $ftmsCadence rpm"
        binding.labelPowerValue.text = "Power $ftmsPower W"
        binding.labelResistanceValue.text = "${ftmsResistanceFieldLabel()} $ftmsResistance"
        binding.labelInclinationValue.text = "Inclination ${formatOne(ftmsInclination)}%"
        binding.labelHeartRateValue.text = "Heart rate $ftmsHeartRate bpm"
        binding.labelMetabolicEquivalentValue.text = "MET ${formatOne(ftmsMetabolicEquivalent)}"
        updateFtmsDurationLabel()
        binding.labelCscSpeedValue.text = "CSC speed ${formatOne(cscSpeed)} km/h"
        binding.labelCscCadenceValue.text = "CSC cadence $cscCadence rpm"
        binding.labelCyclingPowerValue.text = "Cycling Power $cyclingPower W"
        binding.labelCyclingPowerSpeedValue.text = "Cycling Power wheel speed ${formatOne(cyclingPowerSpeed)} km/h"
        binding.labelCyclingPowerCadenceValue.text = "Cycling Power cadence $cyclingPowerCadence rpm"
        binding.labelRscSpeedValue.text = "RSC speed ${formatOne(rscSpeed)} m/s"
        binding.labelRscCadenceValue.text = "RSC cadence $rscCadence spm"
        updateProfileRowSummaries()

        val resistanceVisible = selectedFtmsProfileKey in setOf("ftms_bike", "ftms_rower", "ftms_cross")
        binding.layoutFtmsMeasurementResistance.visibility = if (resistanceVisible) View.VISIBLE else View.GONE
        val inclinationVisible = selectedFtmsProfileKey in setOf("ftms_treadmill", "ftms_cross")
        binding.layoutFtmsMeasurementInclination.visibility = if (inclinationVisible) View.VISIBLE else View.GONE
        val speedVisible = selectedFtmsProfileKey in setOf("ftms_bike", "ftms_treadmill", "ftms_cross")
        binding.labelSpeedValue.visibility = if (speedVisible) View.VISIBLE else View.GONE
        binding.sliderSpeed.visibility = if (speedVisible) View.VISIBLE else View.GONE
        val cadenceVisible = selectedFtmsProfileKey != "ftms_treadmill"
        binding.labelCadenceValue.visibility = if (cadenceVisible) View.VISIBLE else View.GONE
        binding.sliderCadence.visibility = if (cadenceVisible) View.VISIBLE else View.GONE
        val powerVisible = selectedFtmsProfileKey in setOf("ftms_bike", "ftms_treadmill", "ftms_rower", "ftms_cross")
        binding.labelPowerValue.visibility = if (powerVisible) View.VISIBLE else View.GONE
        binding.sliderPower.visibility = if (powerVisible) View.VISIBLE else View.GONE
        val indoorBikeSimulationVisible = selectedFtmsProfileKey == "ftms_bike"
        binding.layoutIndoorBikeSimulationParameters.visibility =
            if (indoorBikeSimulationVisible) View.VISIBLE else View.GONE

        viewModel.ftmsIndoorBikeData.instantaneousSpeed = ftmsSpeed
        viewModel.ftmsIndoorBikeData.instantaneousCadence = ftmsCadence
        viewModel.ftmsIndoorBikeData.resistanceLevel = ftmsResistance
        viewModel.ftmsIndoorBikeData.instantaneousPower = ftmsPower
        viewModel.ftmsIndoorBikeData.heartRate = ftmsHeartRate
        viewModel.ftmsIndoorBikeData.metabolicEquivalent = ftmsMetabolicEquivalent
        if (updateFtmsDuration) {
            viewModel.ftmsIndoorBikeData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
        }
        viewModel.ftmsIndoorBikeData.includeHeartRate = ftmsIncludeHeartRate
        updateIndoorBikeComputedSummary()

        viewModel.ftmsRowerData.strokeRate = ftmsCadence
        viewModel.ftmsRowerData.resistanceLevel = ftmsResistance
        viewModel.ftmsRowerData.instantaneousPower = ftmsPower
        viewModel.ftmsRowerData.heartRate = ftmsHeartRate
        viewModel.ftmsRowerData.metabolicEquivalent = ftmsMetabolicEquivalent
        if (updateFtmsDuration) {
            viewModel.ftmsRowerData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
        }
        viewModel.ftmsRowerData.includeHeartRate = ftmsIncludeHeartRate

        viewModel.ftmsTreadmillData.instantaneousSpeed = ftmsSpeed
        viewModel.ftmsTreadmillData.inclinationPercent = ftmsInclination
        viewModel.ftmsTreadmillData.instantaneousPower = ftmsPower
        viewModel.ftmsTreadmillData.heartRate = ftmsHeartRate
        viewModel.ftmsTreadmillData.metabolicEquivalent = ftmsMetabolicEquivalent
        if (updateFtmsDuration) {
            viewModel.ftmsTreadmillData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
        }
        viewModel.ftmsTreadmillData.includeHeartRate = ftmsIncludeHeartRate

        viewModel.ftmsCrossTrainerData.instantaneousSpeed = ftmsSpeed
        viewModel.ftmsCrossTrainerData.instantaneousStepsPerMinute = ftmsCadence
        viewModel.ftmsCrossTrainerData.resistanceLevel = ftmsResistance
        viewModel.ftmsCrossTrainerData.inclinationPercent = ftmsInclination
        viewModel.ftmsCrossTrainerData.instantaneousPower = ftmsPower
        viewModel.ftmsCrossTrainerData.heartRate = ftmsHeartRate
        viewModel.ftmsCrossTrainerData.metabolicEquivalent = ftmsMetabolicEquivalent
        if (updateFtmsDuration) {
            viewModel.ftmsCrossTrainerData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
        }
        viewModel.ftmsCrossTrainerData.includeHeartRate = ftmsIncludeHeartRate

        viewModel.ftmsStairClimberData.stepsPerMinute = ftmsCadence
        viewModel.ftmsStairClimberData.heartRate = ftmsHeartRate
        viewModel.ftmsStairClimberData.metabolicEquivalent = ftmsMetabolicEquivalent
        if (updateFtmsDuration) {
            viewModel.ftmsStairClimberData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
        }
        viewModel.ftmsStairClimberData.includeHeartRate = ftmsIncludeHeartRate
        viewModel.ftmsStepClimberData.stepsPerMinute = ftmsCadence
        viewModel.ftmsStepClimberData.heartRate = ftmsHeartRate
        viewModel.ftmsStepClimberData.metabolicEquivalent = ftmsMetabolicEquivalent
        if (updateFtmsDuration) {
            viewModel.ftmsStepClimberData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
        }
        viewModel.ftmsStepClimberData.includeHeartRate = ftmsIncludeHeartRate

        // Keep legacy generic values aligned with FTMS for extension providers that have not defined their own controls yet.
        viewModel.instantaneousSpeed = ftmsSpeed
        viewModel.instantaneousCadence = ftmsCadence
        viewModel.instantaneousPower = ftmsPower

        viewModel.cscSpeedKmH.value = cscSpeed.toDouble()
        viewModel.cscCadence.value = cscCadence

        viewModel.cyclingPowerSpeedKmH = cyclingPowerSpeed.toDouble()
        viewModel.cyclingPowerCadence = cyclingPowerCadence
        viewModel.cyclingPowerWatts = cyclingPower

        viewModel.runningSpeedAndCadenceData.instantaneousSpeed = rscSpeed.toDouble()
        viewModel.runningSpeedAndCadenceData.instantaneousCadence = rscCadence
    }

    private fun updateIndoorBikeComputedSummary() {
        if (!ftmsEnabled) {
            binding.textIndoorBikeComputed.text = "FTMS disabled; FTMS values are retained for the next activation."
            return
        }
        binding.textIndoorBikeComputed.text = when (selectedFtmsProfileKey) {
            "ftms_rower" -> viewModel.ftmsRowerData.getComputedSummary()
            "ftms_treadmill" -> viewModel.ftmsTreadmillData.getComputedSummary()
            "ftms_cross" -> viewModel.ftmsCrossTrainerData.getComputedSummary()
            "ftms_step" -> viewModel.ftmsStepClimberData.getComputedSummary()
            "ftms_stair" -> viewModel.ftmsStairClimberData.getComputedSummary()
            else -> viewModel.ftmsIndoorBikeData.getComputedSummary()
        }
    }

    private fun updateRuntimeState() {
        val bluetoothEnabled = isBluetoothEnabled()
        val requestedRunning = prefs.getBoolean(GATT_SERVER_ACTIVATED, true)

        suppressRuntimeSwitchCallback = true
        binding.switchGattServer.isChecked = requestedRunning
        binding.switchGattServer.text = if (requestedRunning) "GATT server enabled" else "GATT server disabled"
        suppressRuntimeSwitchCallback = false

        binding.textBluetoothState.text = if (bluetoothEnabled) "On" else "Off"
        binding.textServerState.text = when {
            !requestedRunning -> "Disabled"
            bluetoothEnabled -> gattRuntimeStatus.label
            else -> "Waiting for Bluetooth"
        }
        binding.textServerState.setTextColor(
            ContextCompat.getColor(
                this,
                if (gattRuntimeStatus.isError && requestedRunning) R.color.simulator_rust else R.color.simulator_ink
            )
        )
        binding.progressRuntimeUpdating.visibility =
            if (requestedRunning && bluetoothEnabled && gattRuntimeStatus.isBusy) View.VISIBLE else View.GONE
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter?.isEnabled == true
    }

    private fun appendLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logLines.addFirst("$timestamp  $message")
        while (logLines.size > 8) {
            logLines.removeLast()
        }
        binding.textEventLog.text = logLines.joinToString(separator = "\n")
    }

    private fun allPermissionsGranted() = BLUETOOTH_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun showTermsAndConditions() {
        val builder = AlertDialog.Builder(this)
        val dialogLayout = layoutInflater.inflate(R.layout.toc_layout, null)

        var localeName = Locale.getDefault().toString()
        if (localeName != "de_DE" && localeName != "en_EN") {
            localeName = "en_EN"
        }

        val webView: WebView = dialogLayout.findViewById(R.id.tocWebView)
        webView.loadUrl("file:///android_asset/terms$localeName.html")

        with(builder) {
            setCancelable(false)
            setPositiveButton(R.string.ok) { _, _ ->
                prefs.edit()
                    .putInt(TERMS_AND_CONDITIONS_READ_VERSION, CURRENT_VERSION_TERMS_AND_CONDITIONS)
                    .apply()
            }
            setView(dialogLayout)
            show()
        }
    }

    private fun hasReadTermsAndConditions(): Boolean {
        return prefs.getInt(TERMS_AND_CONDITIONS_READ_VERSION, 0) >= CURRENT_VERSION_TERMS_AND_CONDITIONS
    }

    private fun formatOne(value: Float): String {
        return String.format(Locale.US, "%.1f", value)
    }

    private fun updateFtmsDurationLabel() {
        val localMinutes = binding.sliderTargetDuration.value.toInt()
        val remoteDurationSeconds = viewModel.ftmsTargetState.value?.trainingTimeSeconds
        binding.labelTargetDurationValue.text = if (remoteDurationSeconds != null) {
            "Remaining-time basis ${remoteDurationSeconds}s · remote Control Point target (local default $localMinutes min)"
        } else {
            "Session duration / remaining-time basis $localMinutes min"
        }
    }

    private fun formatTwo(value: Float): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun formatFour(value: Float): String {
        return String.format(Locale.US, "%.4f", value)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private val BLUETOOTH_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        private val REQUESTED_PERMISSIONS = BLUETOOTH_PERMISSIONS + Manifest.permission.POST_NOTIFICATIONS
    }
}
