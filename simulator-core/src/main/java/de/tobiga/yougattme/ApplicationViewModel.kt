package de.tobiga.yougattme

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.tobiga.yougattme.gatt.fitnessmachine.CrossTrainerData
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsSupportedRanges
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsTargetFeatures
import de.tobiga.yougattme.gatt.fitnessmachine.FtmsTargetState
import de.tobiga.yougattme.gatt.fitnessmachine.IndoorBikeData
import de.tobiga.yougattme.gatt.fitnessmachine.RowerData
import de.tobiga.yougattme.gatt.fitnessmachine.IndoorCyclingSimulationParameters
import de.tobiga.yougattme.gatt.fitnessmachine.StairClimberData
import de.tobiga.yougattme.gatt.fitnessmachine.StepClimberData
import de.tobiga.yougattme.gatt.fitnessmachine.TreadmillData
import de.tobiga.yougattme.gatt.rsc.RunningSpeedAndCadenceProfileData
import de.tobiga.yougattme.tools.CrankRevolutionSimulator
import de.tobiga.yougattme.tools.CyclingMeasurementSimulator
import de.tobiga.yougattme.tools.WheelRevolutionSimulator
import java.time.Instant

class ApplicationViewModel : ViewModel() {

    val runningSpeedAndCadenceData = RunningSpeedAndCadenceProfileData()
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
    val ftmsSupportedRanges = FtmsSupportedRanges()
    val ftmsTargetState = MutableLiveData(FtmsTargetState())

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
    var cscSpeedKmH = MutableLiveData<Double>(4.0)
    var cscCadence = MutableLiveData<Int>(90)
    var cscCumulativeWheelRevolutions = 0
    var cscCumulativeCrankRevolutions = 0
    var cscLastWheelEventTime = 0L
    var cscLastCrankEventTime = 0L
    var wheelRevolutionSimulator = WheelRevolutionSimulator()
    var crankRevolutionSimulator = CrankRevolutionSimulator()



    // The simulator is the single source of truth
    private val simulator = CyclingMeasurementSimulator()

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
    val cumulativeWheelRevolutions: Int
        get() = simulator.getCumulativeWheelRevolutions()

    val cumulativeCrankRevolutions: Int
        get() = simulator.getCumulativeCrankRevolutions()

    val lastWheelEventTime: Long
        get() = simulator.getLastWheelEventTimestampMillis().toLong()

    val lastCrankEventTime: Long
        get() = simulator.getLastCrankEventTimestampMillis().toLong()

    fun advanceTime(timePassedMillis: Long, lastMovedTimestamp: Long) {
        val speedMps = instantaneousSpeed / 3.6

        simulator.simulateCycling(speedMps, instantaneousCadence.toDouble(), timePassedMillis, lastMovedTimestamp)

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

        cscLastWheelEventTime = (wheelRevolutionSimulator.getLastWheelEventTimestamp() * (1024 / 1000))
        cscCumulativeWheelRevolutions = (wheelRevolutionSimulator.getCumulativeWheelRevolutions())

        cscLastCrankEventTime = (crankRevolutionSimulator.getLastCrankEventTimestamp() * (1024 / 1000))
        cscCumulativeCrankRevolutions = (crankRevolutionSimulator.getCumulativeCrankRevolutions())

        lastMovedTimestamp = Instant.now()
    }



}

