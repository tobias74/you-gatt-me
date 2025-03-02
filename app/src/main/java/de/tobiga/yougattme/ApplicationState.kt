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
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import de.tobiga.yougattme.Constants.GATT_SERVER_ACTIVATED
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.csc.CyclingSpeedAndCadenceProfile
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerProfile
import de.tobiga.yougattme.gatt.fitnessmachine.FitnessMachineProfile
import de.tobiga.yougattme.gatt.rsc.RunningSpeedAndCadenceProfile
import java.time.Instant
import java.util.*


private const val TAG = "YouGattMeApplicationState"

@SuppressLint("MissingPermission")
class ApplicationState private constructor(private val context: Context) {
    private lateinit var updateTimer: Timer
    private var isServerRunning = false
    private var prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    private val activeProfiles = mutableSetOf<GattProfile>()

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

    fun startYouGattMeServer() {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            Toast.makeText(context.applicationContext, R.string.no_bluetooth_support, Toast.LENGTH_SHORT).show()
        }

        // Register for system Bluetooth events
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothReceiver, filter)
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(context.applicationContext, R.string.bluetooth_will_be_enabled, Toast.LENGTH_SHORT).show()
            bluetoothAdapter.enable()
        } else {
            startAdvertisingAndServing()
        }

        isServerRunning = true

    }

    fun stopYouGattMeServer() {
        isServerRunning = false

        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter.isEnabled) {
            stopAdvertisingAndServing()
        }
        context.unregisterReceiver(bluetoothReceiver)
    }

    /* Bluetooth API */
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothGattServer: BluetoothGattServer? = null


    private fun startAdvertisingAndServing() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, skipping advertising")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_ADVERTISE permission not granted, skipping advertising")
            return
        }

        Toast.makeText(context.applicationContext, R.string.starting_services, Toast.LENGTH_SHORT).show()

        startAdvertising()
        startServer()
        updateTimeOnEachSecond()
    }

    private fun stopAdvertisingAndServing() {
        Toast.makeText(context.applicationContext, R.string.stopping_services, Toast.LENGTH_SHORT).show()
        stopUpdatingTimer()
        stopServer()
        stopAdvertising()
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
                    stopAdvertisingAndServing()
                }
            }
        }
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            //Log.i(TAG, "LE Advertise Started.")
        }

        override fun onStartFailure(errorCode: Int) {
            //Log.w(TAG, "LE Advertise Failed: $errorCode")
        }
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                //Log.i(TAG, "BluetoothDevice CONNECTED: $device")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                //Log.i(TAG, "BluetoothDevice DISCONNECTED: $device")
                //Remove device from any active subscriptions
                activeProfiles.forEach {
                    it.onDeviceDisconnected(device)
                }
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            val hasBeenHandled = activeProfiles.map {
                it.onCharacteristicReadRequest(bluetoothGattServer,device, requestId, offset, characteristic)
            }.contains(true)

            if (!hasBeenHandled) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE,0,null)
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            val hasBeenHandled = activeProfiles.map {
                it.onCharacteristicWriteRequest(bluetoothGattServer,device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            }.contains(true)

            if (!hasBeenHandled) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE,0,null)
            }

        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            val hasBeenHandled = activeProfiles.map {
                it.onDescriptorReadRequest(bluetoothGattServer,device, requestId, offset, descriptor)
            }.contains(true)


            if (!hasBeenHandled) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE,0, null)
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            val hasBeenHandled = activeProfiles.map {
                it.onDescriptorWriteRequest(bluetoothGattServer,device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            }.contains(true)

            if (!hasBeenHandled) {
                //Unknown descriptor write request
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE,0, null)
                }
            }
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
        Log.w(TAG,"in start advertising")
        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
            bluetoothManager.adapter.bluetoothLeAdvertiser

        bluetoothLeAdvertiser?.let {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

            val dataBuilder = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(false)

                activeProfiles.forEach {
                    it.addMyServices(dataBuilder)
                }

            val data = dataBuilder.build()

            val advScanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

            it.startAdvertising(settings, data, advScanResponse, advertiseCallback)
        }
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private fun stopAdvertising() {
        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
            bluetoothManager.adapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser?.let {
            it.stopAdvertising(advertiseCallback)
        }
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private fun startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        activeProfiles.forEach {
            it.bluetoothGattServer = bluetoothGattServer
            bluetoothGattServer?.addService(it.createService())
        }

        // Initialize the local UI
        // updateLocalUi(System.currentTimeMillis())
    }

    /**
     * Shut down the GATT server.
     */
    private fun stopServer() {
        activeProfiles.forEach {
            it.clearAllDevices()
        }
        bluetoothGattServer?.close()
    }

    private var lastMovedTimestamp = Instant.now()

    fun updateTimeOnEachSecond() {
        var eventCount = 1
        val publishInterval = 1000L
        updateTimer = Timer()
        updateTimer.schedule(object : TimerTask() {
            override fun run() {
                // Capture the current time and calculate the delta from last timestamp.
                val now = Instant.now()
                val timePassedMillis = now.toEpochMilli() - lastMovedTimestamp.toEpochMilli()
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
                        mainViewModel.cyclingSpeedAndCadenceData.advanceTime(timePassedMillis, lastMovedTimestamp.toEpochMilli())
                        mainViewModel.ftmsCrossTrainerData.advanceTime(timePassedMillis)
                        mainViewModel.ftmsTreadmillData.advanceTime(timePassedMillis)
                        mainViewModel.ftmsIndoorBikeData.advanceTime(timePassedMillis)
                        mainViewModel.ftmsRowerData.advanceTime(timePassedMillis)

                    } else {

                    }

                    eventCount += 1
                    activeProfiles.forEach {
                        it.publishData()
                    }
                }
            }
        }, 0, publishInterval)
    }

    fun stopUpdatingTimer() {
        if (::updateTimer.isInitialized) {
            updateTimer.cancel()
            updateTimer.purge()
        }
    }

    private val listener =
        OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (arrayOf("cycling_power_activated", "fitness_machine_activated", "selected_fitness_machine").contains(key)) {
                //stopAdvertisingAndServing()
                //initializeProfiles()
                //startAdvertisingAndServing()
            }

        }

    fun unregisterListener() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun initializeProfiles() {
        activeProfiles.clear()

        if (mainViewModel.isCyclingPowerProfileActive.value!!) {
            activeProfiles.add(CyclingPowerProfile(mainViewModel.cyclingPowerData))
        }

        if (mainViewModel.isFitnessMachineActive.value!!) {
            activeProfiles.add(FitnessMachineProfile(mainViewModel.selectedFitnessMachineMode.value!!, mainViewModel))
        }

        if (mainViewModel.isRunningSpeedAndCadenceActive.value!!) {
            activeProfiles.add(RunningSpeedAndCadenceProfile(mainViewModel.runningSpeedAndCadenceData))
        }

        if (mainViewModel.cscIsProfileActive.value!!) {
            activeProfiles.add(CyclingSpeedAndCadenceProfile(mainViewModel.cyclingSpeedAndCadenceData))
        }

    }

    fun setFitnessMachineMode(mode: String) {
        mainViewModel.selectedFitnessMachineMode.value = mode
        mainViewModel.isFitnessMachineRower.value = (mode == "rower")
        restartGattServices()
    }

    fun activateFitnessMachine() {
        mainViewModel.isFitnessMachineActive.value = true
        restartGattServices()

    }

    fun deactivateFitnessMachine() {
        mainViewModel.isFitnessMachineActive.value = false
        restartGattServices()
    }

    fun activateRunningSpeedAndCadence() {
        mainViewModel.isRunningSpeedAndCadenceActive.value = true
        restartGattServices()
    }

    fun deactivateRunningSpeedAndCadence() {
        mainViewModel.isRunningSpeedAndCadenceActive.value = false
        restartGattServices()
    }


    fun activateCyclingPower() {
        mainViewModel.isCyclingPowerProfileActive.value = true
        restartGattServices()
    }

    fun deactivateCyclingPower() {
        mainViewModel.isCyclingPowerProfileActive.value = false
        restartGattServices()
    }

    fun activateCyclingSpeedAndCadence() {
        mainViewModel.cscIsProfileActive.value = true
        restartGattServices()
    }

    fun deactivateCyclingSpeedAndCadence() {
        mainViewModel.cscIsProfileActive.value = false
        restartGattServices()
    }


    fun restartGattServices() {
        stopAdvertisingAndServing()
        initializeProfiles()
        startAdvertisingAndServing()
    }


    init {
        // initializeProfiles()

        prefs.registerOnSharedPreferenceChangeListener(listener);

        if (prefs.getBoolean(GATT_SERVER_ACTIVATED, true)) {
            ensureYouGattMeServerIsRunning()
        }
    }


}
