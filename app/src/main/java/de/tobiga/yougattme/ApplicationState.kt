package de.tobiga.yougattme

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import de.tobiga.yougattme.Constants.GATT_SERVER_ACTIVATED
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.SafeGattServiceManager
import de.tobiga.yougattme.gatt.csc.CyclingSpeedAndCadenceProfile
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerProfile
import de.tobiga.yougattme.gatt.fitnessmachine.FitnessMachineProfile
import de.tobiga.yougattme.gatt.rsc.RunningSpeedAndCadenceProfile
import de.tobiga.yougattme.tools.toHex
import java.util.*


private const val TAG = "YouGattMeApplicationState"

data class GattRuntimeStatus(
    val label: String,
    val isBusy: Boolean = false,
    val isError: Boolean = false
)

@SuppressLint("MissingPermission")
class ApplicationState private constructor(private val context: Context) {
    private lateinit var updateTimer: Timer
    private var isServerRunning = false
    private var bluetoothReceiverRegistered = false
    private var prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    private var safeServiceMgr: SafeGattServiceManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingGattRestart: Runnable? = null
    private var runtimeStatus = GattRuntimeStatus("Disabled")
    private var runtimeStatusListener: ((GattRuntimeStatus) -> Unit)? = null

    private val activeProfiles = mutableSetOf<AbstractGattProfile>()
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    private var isAdvertising = false

    lateinit var mainViewModel : ApplicationViewModel

    companion object {
        private lateinit var instance: ApplicationState
        fun getInstance(context: Context): ApplicationState {
            if (!this::instance.isInitialized) {
                instance = ApplicationState(context)
            }
            return instance
        }
    }

    fun ensureYouGattMeServerIsRunning() {
        if (!isServerRunning) {
            this.startYouGattMeServer()
        }
    }

    fun setRuntimeStatusListener(listener: ((GattRuntimeStatus) -> Unit)?) {
        runtimeStatusListener = listener
        listener?.invoke(runtimeStatus)
    }

    private fun publishRuntimeStatus(label: String, isBusy: Boolean = false, isError: Boolean = false) {
        runtimeStatus = GattRuntimeStatus(label, isBusy, isError)
        mainHandler.post {
            runtimeStatusListener?.invoke(runtimeStatus)
        }
    }

    fun startYouGattMeServer() {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            publishRuntimeStatus("No Bluetooth support", isError = true)
            return
        }

        isServerRunning = true
        publishRuntimeStatus("Starting...", isBusy = true)

        // Register for system Bluetooth events
        if (!bluetoothReceiverRegistered) {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(bluetoothReceiver, filter)
            bluetoothReceiverRegistered = true
        }
        if (!bluetoothAdapter.isEnabled) {
            publishRuntimeStatus("Waiting for Bluetooth")
            bluetoothAdapter.enable()
        } else {
            startAdvertisingAndServing()
        }

    }

    fun stopYouGattMeServer() {
        isServerRunning = false
        pendingGattRestart?.let(mainHandler::removeCallbacks)
        pendingGattRestart = null
        if (::mainViewModel.isInitialized) {
            mainViewModel.controlHasBeenTakenInControlPoint.postValue(false)
            mainViewModel.fitnessMachineHasBeenStarted.postValue(false)
        }

        if (::bluetoothManager.isInitialized && bluetoothManager.adapter?.isEnabled == true) {
            stopAdvertisingAndServing(updateStatus = false)
        }
        if (bluetoothReceiverRegistered) {
            context.unregisterReceiver(bluetoothReceiver)
            bluetoothReceiverRegistered = false
        }
        publishRuntimeStatus("Disabled")
    }

    /* Bluetooth API */
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothGattServer: BluetoothGattServer? = null


    private fun startAdvertisingAndServing() {

        if (!::bluetoothManager.isInitialized) {
            Log.w(TAG, "BluetoothManager is not initialized, skipping GATT server startup")
            publishRuntimeStatus("Bluetooth manager unavailable", isError = true)
            return
        }

        val bluetoothAdapter = bluetoothManager.adapter
        if (!bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter is not enabled, skipping GATT server startup")
            publishRuntimeStatus("Waiting for Bluetooth")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, skipping advertising")
            publishRuntimeStatus("Bluetooth permission missing", isError = true)
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_ADVERTISE permission not granted, skipping advertising")
            publishRuntimeStatus("Advertise permission missing", isError = true)
            return
        }

        publishRuntimeStatus("Registering services...", isBusy = true)

        if (bluetoothGattServer != null || safeServiceMgr != null) {
            Log.i(TAG, "GATT server is already active or registering; skipping duplicate start")
            publishRuntimeStatus(if (isAdvertising) "Advertising" else "GATT server active")
            return
        }

        startServer()
    }

    private fun stopAdvertisingAndServing(updateStatus: Boolean = true) {
        stopAdvertising()
        stopUpdatingTimer()
        stopServer()
        if (updateStatus) {
            publishRuntimeStatus(if (isServerRunning) "Stopped" else "Disabled")
        }
    }
    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    if (prefs.getBoolean(GATT_SERVER_ACTIVATED,true)) {
                        startAdvertisingAndServing()
                    }
                }
                BluetoothAdapter.STATE_OFF -> {
                    stopAdvertisingAndServing(updateStatus = false)
                    publishRuntimeStatus(if (isServerRunning) "Waiting for Bluetooth" else "Disabled")
                }
            }
        }
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "LE Advertise started")
            isAdvertising = true
            publishRuntimeStatus("Advertising")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                Log.e(TAG, "Primary advertise packet too large; move UUIDs or data into scan response")
                publishRuntimeStatus("Advertising data too large", isError = true)
            } else {
                Log.e(TAG, "LE Advertise failed: code=$errorCode")
                publishRuntimeStatus("Advertising failed ($errorCode)", isError = true)
            }
        }
    }


    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "CONNECTED: ${device.address}")
                        connectedDevices.add(device)
                        activeProfiles.forEach { it.onDeviceConnected(device) }
                        publishRuntimeStatus("Connected")
                    } else {
                        Log.w(TAG, "Connection failed for ${device.address}: status=$status")
                        cleanupDisconnectedDevice(device)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "DISCONNECTED: ${device.address} status=$status")
                    cleanupDisconnectedDevice(device)
                }
                else -> {
                    Log.i(TAG, "Connection state changed: ${device.address} -> state=$newState status=$status")
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            Log.i(TAG, "âž• Service added: ${service.uuid} (status=$status)")
            safeServiceMgr?.onServiceAdded(status, service)
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val svcUuid = characteristic.service.uuid
            Log.d(TAG, "ðŸ“– Read request for char ${characteristic.uuid} on service $svcUuid from ${device.address} (offset=$offset)")

            val profile = activeProfiles.find { it.getServiceUUIDs().contains(svcUuid) }

            if (profile != null) {
                val handled = profile.onCharacteristicReadRequest(
                    bluetoothGattServer, device, requestId, offset, characteristic
                )
                if (!handled) {
                    Log.w(TAG, "âš ï¸ Read not handled by profile for service $svcUuid")
                    bluetoothGattServer?.sendResponse(device, requestId,
                        BluetoothGatt.GATT_FAILURE, 0, null)
                }
            } else {
                Log.e(TAG, "âŒ Read for unknown service $svcUuid")
                bluetoothGattServer?.sendResponse(device, requestId,
                    BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val svcUuid = characteristic.service.uuid
            Log.d(TAG, "âœï¸ Write request for char ${characteristic.uuid} on service $svcUuid from ${device.address} (offset=$offset) value=${value.toHex()} respNeeded=$responseNeeded")

            val profile = activeProfiles.find { it.getServiceUUIDs().contains(svcUuid) }

            if (profile != null) {
                val handled = profile.onCharacteristicWriteRequest(
                    bluetoothGattServer, device, requestId,
                    characteristic, preparedWrite, responseNeeded, offset, value
                )
                if (!handled && responseNeeded) {
                    Log.w(TAG, "âš ï¸ Write not handled by profile for service $svcUuid")
                    bluetoothGattServer?.sendResponse(device, requestId,
                        BluetoothGatt.GATT_FAILURE, 0, null)
                }
            } else if (responseNeeded) {
                Log.e(TAG, "âŒ Write for unknown service $svcUuid")
                bluetoothGattServer?.sendResponse(device, requestId,
                    BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            val svcUuid = descriptor.characteristic.service.uuid
            Log.d(TAG, "ðŸ“– Descriptor read ${descriptor.uuid} on char ${descriptor.characteristic.uuid} (service $svcUuid) from ${device.address}")

            val profile = activeProfiles.find { it.getServiceUUIDs().contains(svcUuid) }

            if (profile != null) {
                val handled = profile.onDescriptorReadRequest(
                    bluetoothGattServer, device, requestId, offset, descriptor
                )
                if (!handled) {
                    Log.w(TAG, "âš ï¸ Descriptor read not handled by profile for service $svcUuid")
                    bluetoothGattServer?.sendResponse(device, requestId,
                        BluetoothGatt.GATT_FAILURE, 0, null)
                }
            } else {
                Log.e(TAG, "âŒ Descriptor read for unknown service $svcUuid")
                bluetoothGattServer?.sendResponse(device, requestId,
                    BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val svcUuid = descriptor.characteristic.service.uuid
            Log.d(TAG, "âœï¸ Descriptor write ${descriptor.uuid} on char ${descriptor.characteristic.uuid} (service $svcUuid) from ${device.address} value=${value.toHex()} respNeeded=$responseNeeded")

            val profile = activeProfiles.find { it.getServiceUUIDs().contains(svcUuid) }

            if (profile != null) {
                val handled = profile.onDescriptorWriteRequest(
                    bluetoothGattServer, device, requestId,
                    descriptor, preparedWrite, responseNeeded, offset, value
                )
                if (!handled && responseNeeded) {
                    Log.w(TAG, "âš ï¸ Descriptor write not handled by profile for service $svcUuid")
                    bluetoothGattServer?.sendResponse(device, requestId,
                        BluetoothGatt.GATT_FAILURE, 0, null)
                }
            } else if (responseNeeded) {
                Log.e(TAG, "âŒ Descriptor write for unknown service $svcUuid")
                bluetoothGattServer?.sendResponse(device, requestId,
                    BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
    }

    private fun cleanupDisconnectedDevice(device: BluetoothDevice) {
        connectedDevices.removeAll { it.address == device.address }
        activeProfiles.forEach { it.onDeviceDisconnected(device) }
        if (::mainViewModel.isInitialized) {
            mainViewModel.controlHasBeenTakenInControlPoint.postValue(false)
            mainViewModel.fitnessMachineHasBeenStarted.postValue(false)
        }
        if (isServerRunning) {
            mainHandler.post {
                if (isServerRunning && bluetoothGattServer != null) {
                    startAdvertising()
                }
            }
            publishRuntimeStatus("Advertising")
        }
    }

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System [BluetoothAdapter].
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private fun checkBluetoothSupport(bluetoothAdapter: BluetoothAdapter?): Boolean {

        if (bluetoothAdapter == null) {
            return false
        }

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false
        }

        return true
    }

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Current Time Service.
     */
    private fun startAdvertising() {
        Log.w(TAG, "in start advertising")
        if (isAdvertising) {
            stopAdvertising()
        }

        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
            bluetoothManager.adapter.bluetoothLeAdvertiser

        if (bluetoothLeAdvertiser == null) {
            publishRuntimeStatus("BLE advertiser unavailable", isError = true)
            return
        }

        bluetoothLeAdvertiser.let {
            bluetoothManager.adapter.name = "YouGattMe"

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()

            val primaryBuilder = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(false)
                .setIncludeDeviceName(true)
                .addManufacturerData(0x0E00, byteArrayOf(0xDD.toByte(), 0x12, 0x34))

            val scanResponseBuilder = AdvertiseData.Builder()

            activeProfiles.forEach { profile ->
                profile.addMyFirstServiceToAdvertisement(scanResponseBuilder)
            }


            val advertiseData = primaryBuilder.build()
            val scanResponseData = scanResponseBuilder.build()

            try {
                it.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
            } catch (exception: IllegalArgumentException) {
                Log.e(TAG, "Invalid advertise data", exception)
                isAdvertising = false
                publishRuntimeStatus("Advertising data invalid", isError = true)
            } catch (exception: IllegalStateException) {
                Log.e(TAG, "Advertiser is not in a usable state", exception)
                isAdvertising = false
                publishRuntimeStatus("Advertising unavailable", isError = true)
            }
        }
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private fun stopAdvertising() {
        if (!::bluetoothManager.isInitialized) {
            isAdvertising = false
            return
        }
        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
            bluetoothManager.adapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser?.let {
            try {
                it.stopAdvertising(advertiseCallback)
            } catch (exception: IllegalStateException) {
                Log.w(TAG, "Ignoring advertiser stop failure", exception)
            }
        }
        isAdvertising = false
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private fun startServer() {
        val gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        if (gattServer == null) {
            Log.e(TAG, "BluetoothManager.openGattServer returned null; GATT server not started")
            publishRuntimeStatus("GATT server unavailable", isError = true)
            return
        }

        // 2) Instantiate and configure the SafeGattServiceManager
        bluetoothGattServer = gattServer
        safeServiceMgr = SafeGattServiceManager(gattServer).apply {
            // Build all your BluetoothGattService objects up-front

            val services = activeProfiles.flatMap { it.createServices(gattServer) }

            enqueueServices(services)

            // Optional: get notified when everythingâ€™s added
            onAllServicesAdded = {
                startAdvertising()

                Log.i("Timer", "âœ… All GATT services registered, now starting timer")
                updateTimeOnEachSecond()
            }

            // Kick off the oneâ€‘byâ€‘one addService() loop
            startAdding()
        }
    }

    /**
     * Shut down the GATT server.
     */
    private fun stopServer() {
        val server = bluetoothGattServer

        if (server != null) {
            connectedDevices.toList().forEach { device ->
                try {
                    server.cancelConnection(device)
                } catch (exception: Exception) {
                    Log.w(TAG, "Error cancelling connection for ${device.address}", exception)
                }
            }
        }

        // 4) Tear down the SafeGattServiceManager (abort pending, remove added)
        safeServiceMgr?.destroy()
        safeServiceMgr = null

        // 5) Let each profile clear its devices
        activeProfiles.forEach { it.clearAllDevices() }
        connectedDevices.clear()

        // 6) Close the GATT server
        server?.let {
            try {
                it.clearServices()
            } catch (exception: Exception) {
                Log.w(TAG, "Error clearing GATT services", exception)
            }
            it.close()
        }
        bluetoothGattServer = null
    }

    private var lastMovedTimestamp = SystemClock.elapsedRealtime()

    fun updateTimeOnEachSecond() {
        Log.d("Timer","starting update timer on each second...")
        var eventCount = 1
        val publishInterval = 500L
        updateTimer = Timer()
        updateTimer.schedule(object : TimerTask() {
            override fun run() {
                // Capture the current time and calculate the delta from last timestamp.
                val now = SystemClock.elapsedRealtime()
                val timePassedMillis = now - lastMovedTimestamp
                // Update lastMovedTimestamp for the next tick.
                lastMovedTimestamp = now

                val timePassedSeconds = timePassedMillis.toDouble() / 1000
                val timePassedMinutes = timePassedSeconds / 60

                if (!isServerRunning) {
                    return
                } else {
                    if (::mainViewModel.isInitialized) {
                        mainViewModel.moveForwardInTimeAndEvents()
                        mainViewModel.runningSpeedAndCadenceData.advanceTime(timePassedMillis)
                        mainViewModel.advanceTime(timePassedMillis, lastMovedTimestamp)
                        val workoutRunning = mainViewModel.fitnessMachineHasBeenStarted.value == true
                        mainViewModel.ftmsCrossTrainerData.advanceTime(timePassedMillis, workoutRunning)
                        mainViewModel.ftmsTreadmillData.advanceTime(timePassedMillis, workoutRunning)
                        mainViewModel.ftmsIndoorBikeData.advanceTime(
                            timePassedMillis,
                            workoutRunning
                        )
                        mainViewModel.ftmsRowerData.advanceTime(timePassedMillis, workoutRunning)
                        mainViewModel.ftmsStairClimberData.advanceTime(timePassedMillis, workoutRunning)
                        mainViewModel.ftmsStepClimberData.advanceTime(timePassedMillis, workoutRunning)

                    } else {

                    }

                    eventCount += 1
                    activeProfiles.forEach {
                        it.triggerPeriodicTimer()
                    }
                }
            }
        }, 0, publishInterval)
    }

    fun stopUpdatingTimer() {
        Log.d("Timer","in stop updating timer before check")
        if (::updateTimer.isInitialized) {
            Log.d("Timer","actually canceling timer")
            updateTimer.cancel()
            updateTimer.purge()
        }
    }

    private val listener =
        OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (arrayOf("cycling_power_activated", "fitness_machine_activated", "selected_fitness_machine").contains(key)) {
            }

        }

    fun unregisterListener() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }


    fun activateCyclingPowerProfile() {
        replaceActiveProfiles(listOf(CyclingPowerProfile(mainViewModel)), rebuildGatt = true)
    }

    fun activateCyclingSpeedAndCadenceProfile() {
        replaceActiveProfiles(listOf(CyclingSpeedAndCadenceProfile(mainViewModel)), rebuildGatt = true)
    }

    fun activateRunningSpeedAndCadenceProfile() {
        replaceActiveProfiles(listOf(RunningSpeedAndCadenceProfile(mainViewModel.runningSpeedAndCadenceData)), rebuildGatt = true)
    }


    fun configureStandardServiceStack(
        ftmsMode: String,
        includeCyclingSpeedAndCadence: Boolean,
        includeCyclingPower: Boolean,
        includeRunningSpeedAndCadence: Boolean,
        rebuildGatt: Boolean = true
    ) {
        mainViewModel.selectedFitnessMachineMode.value = ftmsMode

        val profiles = mutableListOf<AbstractGattProfile>(
            FitnessMachineProfile(ftmsMode, mainViewModel)
        )
        if (includeCyclingSpeedAndCadence) {
            profiles.add(CyclingSpeedAndCadenceProfile(mainViewModel))
        }
        if (includeCyclingPower) {
            profiles.add(CyclingPowerProfile(mainViewModel))
        }
        if (includeRunningSpeedAndCadence) {
            profiles.add(RunningSpeedAndCadenceProfile(mainViewModel.runningSpeedAndCadenceData))
        }

        configureProfileStack(
            profiles = profiles,
            selectedFitnessMachineMode = ftmsMode,
            rebuildGatt = rebuildGatt
        )
    }

    fun configureProfileStack(
        profiles: List<AbstractGattProfile>,
        selectedFitnessMachineMode: String? = null,
        rebuildGatt: Boolean = true
    ) {
        selectedFitnessMachineMode?.let { mode ->
            mainViewModel.selectedFitnessMachineMode.value = mode
        }
        replaceActiveProfiles(profiles, rebuildGatt = rebuildGatt)
    }

    fun activateFitnessMachineProfile(mode: String) {
        mainViewModel.selectedFitnessMachineMode.value = mode
        replaceActiveProfiles(listOf(FitnessMachineProfile(mode, mainViewModel)), rebuildGatt = true)
    }


    fun restartGattServices() {
        if (!isServerRunning) {
            return
        }
        publishRuntimeStatus("Updating services...", isBusy = true)
        pendingGattRestart?.let(mainHandler::removeCallbacks)
        stopAdvertisingAndServing(updateStatus = false)

        val restart = Runnable {
            pendingGattRestart = null
            if (isServerRunning) {
                startAdvertisingAndServing()
            }
        }
        pendingGattRestart = restart
        mainHandler.postDelayed(restart, 1000L)
    }


    fun initializeFromMainActivity() {

        prefs.registerOnSharedPreferenceChangeListener(listener);

        if (prefs.getBoolean(GATT_SERVER_ACTIVATED, true)) {
            ensureYouGattMeServerIsRunning()
        }
    }

    fun destroyAndClearCurrentProfiles() {
        activeProfiles.forEach { it.destroy() }
        activeProfiles.clear()
    }

    private fun replaceActiveProfiles(profiles: List<AbstractGattProfile>, rebuildGatt: Boolean) {
        val shouldRestartGatt = rebuildGatt && isServerRunning
        pendingGattRestart?.let(mainHandler::removeCallbacks)
        pendingGattRestart = null

        if (shouldRestartGatt) {
            publishRuntimeStatus("Updating services...", isBusy = true)
            stopAdvertisingAndServing(updateStatus = false)
        }

        mainViewModel.controlHasBeenTakenInControlPoint.postValue(false)
        mainViewModel.fitnessMachineHasBeenStarted.postValue(false)
        destroyAndClearCurrentProfiles()
        activeProfiles.addAll(profiles)

        if (shouldRestartGatt) {
            val restart = Runnable {
                pendingGattRestart = null
                if (isServerRunning) {
                    startAdvertisingAndServing()
                }
            }
            pendingGattRestart = restart
            mainHandler.postDelayed(restart, 1000L)
        }
    }


}
