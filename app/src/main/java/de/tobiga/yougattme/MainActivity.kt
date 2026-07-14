package de.tobiga.yougattme

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import de.tobiga.yougattme.Constants.GATT_SERVER_ACTIVATED
import de.tobiga.yougattme.databinding.ActivityMainBinding
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsTargetState
import de.tobiga.yougattme.gatt.provider.GattProfileDefinition
import de.tobiga.yougattme.gatt.provider.GattProfileProvider
import de.tobiga.yougattme.gatt.provider.GattProfileProviderLoader
import de.tobiga.yougattme.gatt.provider.ProfileDependencies
import de.tobiga.yougattme.gatt.provider.ProfileSelectionMode
import de.tobiga.yougattme.gatt.provider.StandardGattProfileProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    private lateinit var prefs: SharedPreferences
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var applicationState: ApplicationState
    private val viewModel: ApplicationViewModel by viewModels()
    private val logLines = ArrayDeque<String>()
    private val renderedFieldToggles = mutableListOf<RenderedFieldToggle>()
    private var ftmsEnabled = true
    private var selectedFtmsProfileKey = "ftms_bike"
    private val selectedOptionalServiceKeys = linkedSetOf("csc", "cycling_power")
    private var suppressRuntimeSwitchCallback = false
    private var suppressFieldToggleCallback = false
    private var gattRuntimeStatus = GattRuntimeStatus("Disabled")
    private var renderedMachineFieldKey: String? = null
    private val extensionProfileButtons = linkedMapOf<String, MaterialButton>()
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

    private val profileButtons by lazy {
        mapOf(
            "ftms" to binding.buttonFtms,
            "csc" to binding.buttonCsc,
            "cycling_power" to binding.buttonCyclingPower,
            "rsc" to binding.buttonRsc
        )
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) {
                initializeApplicationState()
                appendLog("Runtime permissions granted")
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
        setupProfileActions()
        setupSimulationControls()
        setupDeviceStateObservers()
        updateDeviceStatePanel()
        updateProfileButtons()
        updateSimulationValuesFromControls()
        renderMachineFieldControls()
        updateRuntimeState()
        appendLog("Console initialized")
        uiHandler.post(indoorBikeSummaryRefresh)

        if (allPermissionsGranted()) {
            initializeApplicationState()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
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
    }

    private fun initializeApplicationState() {
        val wasInitialized = ::applicationState.isInitialized
        applicationState = ApplicationState.getInstance(this)
        applicationState.mainViewModel = viewModel
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

    private fun setupProfileActions() {
        profileButtons.forEach { (key, button) ->
            button.setOnClickListener {
                if (key == "ftms") {
                    ftmsEnabled = !ftmsEnabled
                    applySelectedServiceStack(rebuildGatt = true, logChange = true)
                } else {
                    profiles[key]?.let { toggleProfile(it) }
                }
            }
        }
        setupFtmsMachineDropdown()
        renderExtensionProfileButtons()
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
        binding.sliderTargetDuration.addOnChangeListener { _, _, _ -> updateSimulationValuesFromControls() }
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
                viewModel.fitnessMachineHasBeenStarted.value = false
                viewModel.ftmsTargetState.value = FtmsTargetState()
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

    private fun setupDeviceStateObservers() {
        viewModel.controlHasBeenTakenInControlPoint.observe(this) { updateDeviceStatePanel() }
        viewModel.fitnessMachineHasBeenStarted.observe(this) { updateDeviceStatePanel() }
        viewModel.selectedFitnessMachineMode.observe(this) { updateDeviceStatePanel() }
        viewModel.ftmsTargetState.observe(this) { updateDeviceStatePanel() }
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
            controlTaken -> "Taken"
            else -> "Available"
        }
        binding.textWorkoutState.text = if (workoutStarted) "Started" else "Idle"
        binding.textTrainingStatusState.text = if (workoutStarted) "Manual Mode" else "Idle"
        binding.textMachineModeState.text = machineName
        binding.textProcedureState.text = viewModel.ftmsTargetState.value?.summary() ?: "Targets: none"
        binding.switchFtmsControlPoint.text = if (controlPointEnabled) {
            "Control Point characteristic enabled"
        } else {
            "Control Point characteristic disabled"
        }
    }

    private fun updateProfileButtons() {
        val selectedKeys = selectedProfiles().map { it.key }.toSet()
        allProfileButtons().forEach { (key, button) ->
            val selected = if (key == "ftms") ftmsEnabled else selectedKeys.contains(key)
            button.strokeWidth = if (selected) dp(2) else dp(1)
            button.setTextColor(ContextCompat.getColor(this, if (selected) R.color.white else R.color.simulator_ink))
            button.backgroundTintList = ContextCompat.getColorStateList(
                this,
                if (selected) R.color.simulator_moss else R.color.simulator_panel
            )
        }
        binding.spinnerFtmsMachine.alpha = if (ftmsEnabled) 1f else 0.55f
    }

    private fun renderExtensionProfileButtons() {
        val extensionProfiles = profiles.values
            .filter { profile -> !profileButtons.containsKey(profile.key) }
            .filter { profile -> profile.selectionMode != ProfileSelectionMode.FTMS_MACHINE }
            .sortedBy { profile -> profile.title }

        extensionProfileButtons.clear()
        binding.gridExtensionProfiles.removeAllViews()
        binding.layoutExtensionProfileSection.visibility =
            if (extensionProfiles.isEmpty()) View.GONE else View.VISIBLE

        extensionProfiles.forEach { profile ->
            val button = MaterialButton(this).apply {
                text = profile.title
                contentDescription = "${profile.title}. ${profile.subtitle}"
                isAllCaps = false
                minHeight = dp(64)
                cornerRadius = dp(18)
                strokeWidth = dp(1)
                strokeColor = ContextCompat.getColorStateList(this@MainActivity, R.color.simulator_line)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.simulator_ink))
                backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.simulator_panel)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(64)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                setOnClickListener { toggleProfile(profile) }
            }
            binding.gridExtensionProfiles.addView(button)
            extensionProfileButtons[profile.key] = button
        }
    }

    private fun allProfileButtons(): Map<String, MaterialButton> {
        return profileButtons + extensionProfileButtons
    }

    private fun renderMachineFieldControls() {
        val renderKey = "$ftmsEnabled:$selectedFtmsProfileKey:${viewModel.ftmsControlPointEnabled}:${viewModel.ftmsMirrorTargetFeatureDeclarations}"
        if (renderedMachineFieldKey == renderKey) {
            return
        }

        renderedMachineFieldKey = renderKey
        binding.layoutIndoorBikeFieldControls.visibility = View.GONE
        binding.layoutMachineFieldControls.visibility = View.GONE
        renderedFieldToggles.clear()
        clearFieldToggleContainers()
        binding.layoutControlCapabilityToggleList.removeAllViews()
        binding.layoutFtmsControlCapability.visibility = if (ftmsEnabled) View.VISIBLE else View.GONE

        if (!ftmsEnabled) {
            return
        }

        val toggles = machineFieldToggles(selectedFtmsProfileKey)
        val measurementToggles = toggles.filter { toggle -> toggle.labelPrefix == "Feature/data" }
        val controlToggles = toggles.filterNot { toggle -> toggle.labelPrefix == "Feature/data" }

        measurementToggles.groupBy { toggle -> toggle.group }.forEach { (group, togglesForGroup) ->
            val container = containerFor(group)
            container.visibility = View.VISIBLE
            renderFieldToggleGroups(container, togglesForGroup)
        }
        renderFieldToggleGroups(binding.layoutControlCapabilityToggleList, controlToggles)
    }

    private fun clearFieldToggleContainers() {
        fieldToggleContainers().forEach { container ->
            container.removeAllViews()
            container.visibility = View.GONE
        }
    }

    private fun fieldToggleContainers(): List<LinearLayout> {
        return listOf(
            binding.layoutSpeedFieldToggles,
            binding.layoutCadenceFieldToggles,
            binding.layoutPowerFieldToggles,
            binding.layoutResistanceFieldToggles,
            binding.layoutInclinationFieldToggles,
            binding.layoutHeartRateFieldToggles,
            binding.layoutMetabolicFieldToggles,
            binding.layoutDurationFieldToggles
        )
    }

    private fun containerFor(group: FieldToggleGroup): LinearLayout {
        return when (group) {
            FieldToggleGroup.SPEED -> binding.layoutSpeedFieldToggles
            FieldToggleGroup.CADENCE -> binding.layoutCadenceFieldToggles
            FieldToggleGroup.POWER -> binding.layoutPowerFieldToggles
            FieldToggleGroup.RESISTANCE -> binding.layoutResistanceFieldToggles
            FieldToggleGroup.INCLINATION -> binding.layoutInclinationFieldToggles
            FieldToggleGroup.HEART_RATE -> binding.layoutHeartRateFieldToggles
            FieldToggleGroup.METABOLIC -> binding.layoutMetabolicFieldToggles
            FieldToggleGroup.DURATION -> binding.layoutDurationFieldToggles
        }
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
            setBackgroundResource(R.drawable.simulator_toggle_group)
            setPadding(dp(12), dp(9), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { params ->
                params.topMargin = dp(8)
            }

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
            requiresGattRebuild = false,
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
            requiresGattRebuild = false,
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
            requiresGattRebuild = false,
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

    private fun updateSimulationValuesFromControls() {
        val ftmsSpeed = binding.sliderSpeed.value
        val ftmsCadence = binding.sliderCadence.value.toInt()
        val ftmsPower = binding.sliderPower.value.toInt()
        val ftmsResistance = binding.sliderResistance.value.toInt()
        val ftmsInclination = binding.sliderInclination.value
        val ftmsHeartRate = binding.sliderHeartRate.value.toInt()
        val ftmsMetabolicEquivalent = binding.sliderMetabolicEquivalent.value
        val ftmsTargetDurationMinutes = binding.sliderTargetDuration.value.toInt()
        val ftmsIncludeHeartRate = binding.switchHeartRate.isChecked

        val cscSpeed = binding.sliderCscSpeed.value
        val cscCadence = binding.sliderCscCadence.value.toInt()
        val cyclingPower = binding.sliderCyclingPower.value.toInt()
        val cyclingPowerSpeed = binding.sliderCyclingPowerSpeed.value
        val cyclingPowerCadence = binding.sliderCyclingPowerCadence.value.toInt()
        val rscSpeed = binding.sliderRscSpeed.value
        val rscCadence = binding.sliderRscCadence.value.toInt()

        binding.labelSpeedValue.text = "FTMS speed ${formatOne(ftmsSpeed)} km/h"
        binding.labelCadenceValue.text = "FTMS cadence $ftmsCadence rpm"
        binding.labelPowerValue.text = "FTMS power $ftmsPower W"
        binding.labelResistanceValue.text = "FTMS resistance $ftmsResistance"
        val inclineLabel = if (selectedFtmsProfileKey == "ftms_bike") "grade" else "inclination"
        binding.labelInclinationValue.text = "FTMS $inclineLabel ${formatOne(ftmsInclination)}%"
        binding.labelHeartRateValue.text = "FTMS heart rate $ftmsHeartRate bpm"
        binding.labelMetabolicEquivalentValue.text = "FTMS metabolic equivalent ${formatOne(ftmsMetabolicEquivalent)} MET"
        binding.labelTargetDurationValue.text = "FTMS target duration $ftmsTargetDurationMinutes min"
        binding.labelCscSpeedValue.text = "CSC speed ${formatOne(cscSpeed)} km/h"
        binding.labelCscCadenceValue.text = "CSC cadence $cscCadence rpm"
        binding.labelCyclingPowerValue.text = "Cycling Power $cyclingPower W"
        binding.labelCyclingPowerSpeedValue.text = "Cycling Power wheel speed ${formatOne(cyclingPowerSpeed)} km/h"
        binding.labelCyclingPowerCadenceValue.text = "Cycling Power cadence $cyclingPowerCadence rpm"
        binding.labelRscSpeedValue.text = "RSC speed ${formatOne(rscSpeed)} m/s"
        binding.labelRscCadenceValue.text = "RSC cadence $rscCadence spm"

        binding.layoutIndoorBikeFieldControls.visibility = View.GONE
        binding.layoutMachineFieldControls.visibility = View.GONE
        val resistanceVisible = selectedFtmsProfileKey in setOf("ftms_bike", "ftms_rower", "ftms_cross")
        binding.labelResistanceValue.visibility = if (resistanceVisible) View.VISIBLE else View.GONE
        binding.sliderResistance.visibility = if (resistanceVisible) View.VISIBLE else View.GONE
        val inclinationVisible = selectedFtmsProfileKey in setOf("ftms_bike", "ftms_treadmill", "ftms_cross")
        binding.labelInclinationValue.visibility = if (inclinationVisible) View.VISIBLE else View.GONE
        binding.sliderInclination.visibility = if (inclinationVisible) View.VISIBLE else View.GONE

        viewModel.ftmsIndoorBikeData.instantaneousSpeed = ftmsSpeed
        viewModel.ftmsIndoorBikeData.instantaneousCadence = ftmsCadence
        viewModel.ftmsIndoorBikeData.resistanceLevel = ftmsResistance
        viewModel.ftmsIndoorBikeData.instantaneousPower = ftmsPower
        viewModel.ftmsIndoorBikeData.heartRate = ftmsHeartRate
        viewModel.ftmsIndoorBikeData.metabolicEquivalent = ftmsMetabolicEquivalent
        viewModel.ftmsIndoorBikeData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
        viewModel.ftmsIndoorBikeData.includeHeartRate = ftmsIncludeHeartRate
        if (selectedFtmsProfileKey == "ftms_bike") {
            val currentSimulation = viewModel.indoorCyclingSimulationParameters.value
            viewModel.updateSimulationParameters(
                currentSimulation?.windSpeedMps ?: 0.0f,
                ftmsInclination.toDouble(),
                currentSimulation?.crr ?: 0.004f,
                currentSimulation?.cw ?: 0.5f
            )
        }
        updateIndoorBikeComputedSummary()

        viewModel.ftmsRowerData.strokeRate = ftmsCadence
        viewModel.ftmsRowerData.resistanceLevel = ftmsResistance
        viewModel.ftmsRowerData.instantaneousPower = ftmsPower
        viewModel.ftmsRowerData.heartRate = ftmsHeartRate
        viewModel.ftmsRowerData.metabolicEquivalent = ftmsMetabolicEquivalent
        viewModel.ftmsRowerData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
        viewModel.ftmsRowerData.includeHeartRate = ftmsIncludeHeartRate

        viewModel.ftmsTreadmillData.instantaneousSpeed = ftmsSpeed.coerceAtMost(25f)
        viewModel.ftmsTreadmillData.inclinationPercent = ftmsInclination
        viewModel.ftmsTreadmillData.instantaneousPower = ftmsPower
        viewModel.ftmsTreadmillData.heartRate = ftmsHeartRate
        viewModel.ftmsTreadmillData.metabolicEquivalent = ftmsMetabolicEquivalent
        viewModel.ftmsTreadmillData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
        viewModel.ftmsTreadmillData.includeHeartRate = ftmsIncludeHeartRate

        viewModel.ftmsCrossTrainerData.instantaneousSpeed = ftmsSpeed
        viewModel.ftmsCrossTrainerData.instantaneousStepsPerMinute = ftmsCadence
        viewModel.ftmsCrossTrainerData.resistanceLevel = ftmsResistance
        viewModel.ftmsCrossTrainerData.inclinationPercent = ftmsInclination
        viewModel.ftmsCrossTrainerData.instantaneousPower = ftmsPower
        viewModel.ftmsCrossTrainerData.heartRate = ftmsHeartRate
        viewModel.ftmsCrossTrainerData.metabolicEquivalent = ftmsMetabolicEquivalent
        viewModel.ftmsCrossTrainerData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
        viewModel.ftmsCrossTrainerData.includeHeartRate = ftmsIncludeHeartRate

        viewModel.ftmsStairClimberData.stepsPerMinute = ftmsCadence
        viewModel.ftmsStairClimberData.heartRate = ftmsHeartRate
        viewModel.ftmsStairClimberData.metabolicEquivalent = ftmsMetabolicEquivalent
        viewModel.ftmsStairClimberData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
        viewModel.ftmsStairClimberData.includeHeartRate = ftmsIncludeHeartRate
        viewModel.ftmsStepClimberData.stepsPerMinute = ftmsCadence
        viewModel.ftmsStepClimberData.heartRate = ftmsHeartRate
        viewModel.ftmsStepClimberData.metabolicEquivalent = ftmsMetabolicEquivalent
        viewModel.ftmsStepClimberData.targetDurationSeconds = ftmsTargetDurationMinutes * 60
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

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ).toTypedArray()
    }
}
