package de.tobiga.yougattme

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.tobiga.yougattme.gatt.fitnessmachine.CrossTrainerData
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsSupportedRanges
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsMeasurementSnapshot
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsProtocolEvent
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsTargetFeatures
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsTargetState
import de.tobiga.yougattme.gatt.fitnessmachine.IndoorBikeData
import de.tobiga.yougattme.gatt.fitnessmachine.RowerData
import de.tobiga.yougattme.gatt.fitnessmachine.IndoorCyclingSimulationParameters
import de.tobiga.yougattme.gatt.fitnessmachine.StairClimberData
import de.tobiga.yougattme.gatt.fitnessmachine.StepClimberData
import de.tobiga.yougattme.gatt.fitnessmachine.TreadmillData
import de.tobiga.yougattme.gatt.common.SensorMeasurementSnapshot
import de.tobiga.yougattme.gatt.common.SensorProtocolEvent
import de.tobiga.yougattme.gatt.csc.CscSimulatorConfiguration
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerSimulatorConfiguration
import de.tobiga.yougattme.gatt.rsc.RscSimulatorConfiguration
import de.tobiga.yougattme.gatt.rsc.RunningSpeedAndCadenceProfileData
import de.tobiga.yougattme.gatt.heartrate.HeartRateSimulatorConfiguration
import de.tobiga.yougattme.tools.CrankRevolutionSimulator
import de.tobiga.yougattme.tools.CyclingMeasurementSimulator
import de.tobiga.yougattme.tools.WheelRevolutionSimulator
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI

/**
 * Application-lifetime source of truth for all simulated protocol state.
 *
 * It deliberately has no Activity or Service dependency. [ApplicationViewModel] exposes this
 * store to the legacy UI and profile APIs, while the BLE bridge can use it directly.
 */
class SimulationStateStore private constructor() {

    companion object {
        @JvmStatic
        val shared: SimulationStateStore by lazy { SimulationStateStore() }
    }

    val bridgeState = de.tobiga.yougattme.bridge.BridgeStateStore.shared

    val runningSpeedAndCadenceData = RunningSpeedAndCadenceProfileData()
    val rscConfiguration = RscSimulatorConfiguration()
    val cscConfiguration = CscSimulatorConfiguration()
    val cyclingPowerConfiguration = CyclingPowerSimulatorConfiguration()
    val heartRateConfiguration = HeartRateSimulatorConfiguration()
    val ftmsCrossTrainerData = CrossTrainerData()
    val ftmsTreadmillData = TreadmillData()
    val ftmsIndoorBikeData = IndoorBikeData()
    val ftmsRowerData = RowerData()

    val ftmsStairClimberData = StairClimberData()
    val ftmsStepClimberData = StepClimberData()
    val ftmsTargetFeatures = FtmsTargetFeatures()
    val ftmsDeclaredTargetFeatures = FtmsTargetFeatures()
    var ftmsControlPointEnabled: Boolean = true
    var ftmsMirrorTargetFeatureDeclarations: Boolean = true
    var ftmsSupportedRanges = FtmsSupportedRanges()
    private var ftmsTargetStateBacking = FtmsTargetState()
    val ftmsTargetState = MutableLiveData(ftmsTargetStateBacking)
    val ftmsTrainingStatus = MutableLiveData(0x01)
    val ftmsControlOwnerAddress = MutableLiveData<String?>(null)
    val ftmsControlProcedureState = MutableLiveData("Idle")
    val ftmsSessionDurationMinutes = MutableLiveData(30f)
    private val _ftmsMeasurementSnapshot = MutableLiveData<FtmsMeasurementSnapshot>()
    val ftmsMeasurementSnapshot: LiveData<FtmsMeasurementSnapshot> = _ftmsMeasurementSnapshot
    private val _ftmsProtocolEvents = MutableLiveData<List<FtmsProtocolEvent>>(emptyList())
    val ftmsProtocolEvents: LiveData<List<FtmsProtocolEvent>> = _ftmsProtocolEvents
    private val ftmsEventSequence = AtomicLong(0)
    private val ftmsProtocolEventBuffer = ArrayDeque<FtmsProtocolEvent>()
    private val sensorEventSequence = AtomicLong(0)
    private val sensorProtocolEventBuffers = mutableMapOf<String, ArrayDeque<SensorProtocolEvent>>()
    private val _sensorMeasurementSnapshots =
        MutableLiveData<Map<String, SensorMeasurementSnapshot>>(emptyMap())
    val sensorMeasurementSnapshots: LiveData<Map<String, SensorMeasurementSnapshot>> =
        _sensorMeasurementSnapshots
    private val _sensorProtocolEvents =
        MutableLiveData<Map<String, List<SensorProtocolEvent>>>(emptyMap())
    val sensorProtocolEvents: LiveData<Map<String, List<SensorProtocolEvent>>> =
        _sensorProtocolEvents

    fun ftmsTargetFeatureDeclarationsForGatt(): FtmsTargetFeatures {
        if (!ftmsControlPointEnabled) {
            return FtmsTargetFeatures()
        }
        return if (ftmsMirrorTargetFeatureDeclarations) {
            ftmsTargetFeatures
        } else {
            ftmsDeclaredTargetFeatures
        }
    }

    fun syncFtmsDeclaredTargetFeaturesFromActual() {
        ftmsDeclaredTargetFeatures.copyFrom(ftmsTargetFeatures)
    }

    // --- With a single LiveData for the simulation state ---
    private val _indoorCycling_simulationParameters = MutableLiveData<IndoorCyclingSimulationParameters>(IndoorCyclingSimulationParameters())
    val indoorCyclingSimulationParameters: LiveData<IndoorCyclingSimulationParameters> = _indoorCycling_simulationParameters

    fun updateSimulationParameters(windSpeed: Float, grade: Double, crr: Float, cw: Float) {
        _indoorCycling_simulationParameters.postValue(IndoorCyclingSimulationParameters(windSpeed, grade, crr, cw))
    }


    val ftmsReceivedTargetInclination = MutableLiveData<Double>(0.0)

    var isCyclingPowerProfileActive = MutableLiveData<Boolean>(false)

    private val mutableSelectedItem = MutableLiveData<Int>()
    val selectedItem: LiveData<Int> get() = mutableSelectedItem

    fun selectItem(item: Int) {
        mutableSelectedItem.value = item
    }

    var fitnessMachineHasBeenStarted = MutableLiveData<Boolean>(false)

    var selectedFitnessMachineMode = MutableLiveData<String>("tobias_ding_dong")

    var receivedTargetPowerInControlPoint = MutableLiveData<Int>(0)
    var controlHasBeenTakenInControlPoint = MutableLiveData<Boolean>(false)
    var receivedIndoorBikeGrade = MutableLiveData<Double>(0.0)



    private var lastMovedTimestamp = Instant.now()

    var fitnessMachineRowerStrokesPerMinute = MutableLiveData<Int>(20)
    var fitnessMachineRowerStrokesTotal = MutableLiveData<Double>(0.0)


    var cscIsProfileActive = MutableLiveData<Boolean>(false)
    var cscSpeedKmH = MutableLiveData<Double>(25.0)
    var cscCadence = MutableLiveData<Int>(80)
    var cscCumulativeWheelRevolutions = 0L
    var cscCumulativeCrankRevolutions = 0
    var cscLastWheelEventTime = 0L
    var cscLastCrankEventTime = 0L
    var wheelRevolutionSimulator = WheelRevolutionSimulator()
    var crankRevolutionSimulator = CrankRevolutionSimulator()

    var cyclingPowerSpeedKmH: Double = 25.0
    var cyclingPowerCadence: Int = 80
    var cyclingPowerWatts: Int = 152



    // The simulator is the single source of truth
    private val simulator = CyclingMeasurementSimulator()
    private var cyclingPowerAccumulatedEnergyJoules = 0.0
    private val cyclingPowerCrankRevolutionCounter = AtomicLong(0)
    val cyclingPowerCrankRevolutionSequence: Long
        get() = cyclingPowerCrankRevolutionCounter.get()

    // Only keep LiveData for user-set values that aren't derived from simulator
    private val _instantaneousSpeed = MutableLiveData<Float>(25.0f)
    private val _instantaneousCadence = MutableLiveData<Int>(90)
    private val _wheelCircumference = MutableLiveData<Float>(210.0f) // Default in cm
    private val _totalDistance = MutableLiveData<Int>(100)

    init {
        // Apply wheel circumference to simulator
        simulator.setWheelCircumference(210.0f) // Convert cm to meters
    }

    // Speed is stored in the class, but updates simulator when changed
    var instantaneousSpeed: Float
        get() = _instantaneousSpeed.value ?: 0.0f
        set(value) {
            _instantaneousSpeed.value = value
        }

    fun postInstantaneousSpeed(value: Float) {
        _instantaneousSpeed.postValue(value)
    }

    var instantaneousCadence: Int
        get() = _instantaneousCadence.value ?: 0
        set(value) {
            _instantaneousCadence.value = value
        }

    fun postInstantaneousCadence(value: Int) {
        _instantaneousCadence.postValue(value)
    }

    fun postWheelCircumference(value: Float) {
        _wheelCircumference.postValue(value)
        // Update the simulator when wheel circumference changes
        simulator.setWheelCircumference(value / 100.0f) // Convert cm to meters
    }

    var totalDistance: Int
        get() = _totalDistance.value ?: 0
        set(value) { _totalDistance.value = value }


    private val _instantaneousPower = MutableLiveData<Int>(240)

    var instantaneousPower: Int
        get() = _instantaneousPower.value ?: 0
        set(value) { _instantaneousPower.value = value }

    val instantaneousPowerLiveData: LiveData<Int>
        get() = _instantaneousPower

    fun postInstantaneousPower(value: Int) {
        _instantaneousPower.postValue(value)
    }







    // Direct getters for BLE characteristics to use
    val cumulativeWheelRevolutions: Long
        get() = simulator.getCumulativeWheelRevolutions()

    val cumulativeCrankRevolutions: Int
        get() = simulator.getCumulativeCrankRevolutions()

    val lastWheelEventTime: Long
        get() = simulator.getLastWheelEventTimestampMillis().toLong()

    val lastCrankEventTime: Long
        get() = simulator.getLastCrankEventTimestampMillis().toLong()

    fun setCyclingPowerCumulativeWheelRevolutions(value: Long) {
        simulator.setCumulativeWheelRevolutions(value)
    }

    fun applyCscCumulativeWheelRevolutions(value: Long) {
        wheelRevolutionSimulator.setCumulativeWheelRevolutions(value)
        cscCumulativeWheelRevolutions = value.coerceIn(0, 0xFFFF_FFFFL)
    }

    fun advanceTime(timePassedMillis: Long, lastMovedTimestamp: Long) {
        val speedMps = cyclingPowerSpeedKmH / 3.6

        val (_, newCrankRevolutions) = simulator.simulateCycling(
            speedMps,
            cyclingPowerCadence.toDouble(),
            timePassedMillis,
            lastMovedTimestamp
        )
        cyclingPowerAccumulatedEnergyJoules +=
            cyclingPowerWatts.coerceAtLeast(0) * (timePassedMillis / 1000.0)
        cyclingPowerConfiguration.accumulatedEnergyKj =
            (cyclingPowerAccumulatedEnergyJoules / 1000.0).toInt().coerceIn(0, 0xFFFF)
        if (newCrankRevolutions > 0 && cyclingPowerCadence > 0) {
            cyclingPowerCrankRevolutionCounter.addAndGet(newCrankRevolutions.toLong())
            val radiansPerSecond = 2.0 * PI * cyclingPowerCadence / 60.0
            val averageTorqueNm = cyclingPowerWatts / radiansPerSecond
            cyclingPowerConfiguration.accumulatedTorqueNm =
                (cyclingPowerConfiguration.accumulatedTorqueNm + averageTorqueNm * newCrankRevolutions)
                    .coerceIn(0.0, 0xFFFF / 32.0)
        }

    }

    fun resetCyclingPowerConnectionAccumulators() {
        cyclingPowerAccumulatedEnergyJoules = 0.0
        cyclingPowerConfiguration.accumulatedEnergyKj = 0
        cyclingPowerConfiguration.accumulatedTorqueNm = 0.0
        cyclingPowerCrankRevolutionCounter.set(0)
    }

    fun recordFtmsMeasurement(
        machineType: String,
        characteristicUuid: String,
        logicalRecord: ByteArray,
        packets: List<ByteArray>,
        previewPayloadBytes: Int
    ) {
        _ftmsMeasurementSnapshot.postValue(
            FtmsMeasurementSnapshot(
                machineType = machineType,
                characteristicUuid = characteristicUuid,
                logicalRecordHex = logicalRecord.toFtmsHex(),
                notificationPacketHex = packets.map(ByteArray::toFtmsHex),
                previewPayloadBytes = previewPayloadBytes,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    @Synchronized
    fun recordFtmsProtocolEvent(
        direction: String,
        channel: String,
        title: String,
        payload: ByteArray
    ) {
        val event = FtmsProtocolEvent(
            sequence = ftmsEventSequence.incrementAndGet(),
            timestampMillis = System.currentTimeMillis(),
            direction = direction,
            channel = channel,
            title = title,
            payloadHex = payload.toFtmsHex()
        )
        ftmsProtocolEventBuffer.addFirst(event)
        while (ftmsProtocolEventBuffer.size > 40) {
            ftmsProtocolEventBuffer.removeLast()
        }
        _ftmsProtocolEvents.postValue(ftmsProtocolEventBuffer.toList())
    }

    @Synchronized
    fun clearFtmsProtocolEvents() {
        ftmsProtocolEventBuffer.clear()
        _ftmsProtocolEvents.value = emptyList()
    }

    @Synchronized
    fun recordSensorMeasurement(
        serviceKey: String,
        characteristicUuid: String,
        logicalRecord: ByteArray,
        packets: List<ByteArray>,
        payloadBytes: Int
    ) {
        val current = _sensorMeasurementSnapshots.value.orEmpty().toMutableMap()
        current[serviceKey] = SensorMeasurementSnapshot(
            serviceKey = serviceKey,
            characteristicUuid = characteristicUuid,
            logicalRecordHex = logicalRecord.toProtocolHex(),
            notificationPacketHex = packets.map(ByteArray::toProtocolHex),
            payloadBytes = payloadBytes,
            timestampMillis = System.currentTimeMillis()
        )
        _sensorMeasurementSnapshots.postValue(current)
    }

    @Synchronized
    fun recordSensorProtocolEvent(
        serviceKey: String,
        direction: String,
        title: String,
        payload: ByteArray
    ) {
        val event = SensorProtocolEvent(
            sequence = sensorEventSequence.incrementAndGet(),
            timestampMillis = System.currentTimeMillis(),
            direction = direction,
            serviceKey = serviceKey,
            title = title,
            payloadHex = payload.toProtocolHex()
        )
        val buffer = sensorProtocolEventBuffers.getOrPut(serviceKey) { ArrayDeque() }
        buffer.addFirst(event)
        while (buffer.size > 30) buffer.removeLast()
        _sensorProtocolEvents.postValue(
            sensorProtocolEventBuffers.mapValues { (_, events) -> events.toList() }
        )
    }

    @Synchronized
    fun clearSensorProtocolEvents(serviceKey: String) {
        sensorProtocolEventBuffers.remove(serviceKey)
        _sensorProtocolEvents.value =
            sensorProtocolEventBuffers.mapValues { (_, events) -> events.toList() }
    }

    @Synchronized
    fun updateFtmsTargetState(update: (FtmsTargetState) -> FtmsTargetState) {
        ftmsTargetStateBacking = update(ftmsTargetStateBacking)
        ftmsTargetState.postValue(ftmsTargetStateBacking)
    }

    @Synchronized
    fun clearFtmsTargetState() {
        ftmsTargetStateBacking = FtmsTargetState()
        ftmsTargetState.postValue(ftmsTargetStateBacking)
    }

    fun resetFtmsControlState() {
        clearFtmsTargetState()
        ftmsTrainingStatus.postValue(0x01)
        updateSimulationParameters(0f, 0.0, 0.004f, 0.5f)
        ftmsReceivedTargetInclination.postValue(0.0)
        receivedTargetPowerInControlPoint.postValue(0)
        receivedIndoorBikeGrade.postValue(0.0)
        postWheelCircumference(210.0f)
        ftmsSessionDurationMinutes.postValue(0f)
    }

    // Reset the simulator
    fun reset() {
        simulator.reset()
    }
    fun moveForwardInTimeAndEvents() {
        //Log.d("ApplicationViewModel", "moveForwardInTimeAndEvents() called");

        val timePassedMillis = Instant.now().toEpochMilli() - lastMovedTimestamp.toEpochMilli()
        val timePassedSeconds = timePassedMillis.toDouble() / 1000
        val timePassedMinutes = timePassedSeconds / 60

        fitnessMachineRowerStrokesTotal.postValue(fitnessMachineRowerStrokesTotal.value?.plus(
            timePassedMinutes * fitnessMachineRowerStrokesPerMinute.value!!
        ))


        val speed = cscSpeedKmH.value!! / 3.6
        wheelRevolutionSimulator.simulate(speed, timePassedMillis)

        crankRevolutionSimulator.simulate(cscCadence.value!!)

        cscLastWheelEventTime = wheelRevolutionSimulator.getLastWheelEventTimestamp()
        cscCumulativeWheelRevolutions = (wheelRevolutionSimulator.getCumulativeWheelRevolutions())

        cscLastCrankEventTime = crankRevolutionSimulator.getLastCrankEventTimestamp()
        cscCumulativeCrankRevolutions = (crankRevolutionSimulator.getCumulativeCrankRevolutions())

        lastMovedTimestamp = Instant.now()
    }



}

private fun ByteArray.toFtmsHex(): String {
    return joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
}

private fun ByteArray.toProtocolHex(): String =
    joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }

