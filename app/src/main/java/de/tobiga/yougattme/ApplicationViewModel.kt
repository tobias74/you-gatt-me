package de.tobiga.yougattme

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.tobiga.yougattme.gatt.csc.CyclingSpeedAndCadenceProfileData
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerProfileData
import de.tobiga.yougattme.gatt.fitnessmachine.CrossTrainerData
import de.tobiga.yougattme.gatt.fitnessmachine.IndoorBikeData
import de.tobiga.yougattme.gatt.fitnessmachine.RowerData
import de.tobiga.yougattme.gatt.fitnessmachine.TreadmillData
import de.tobiga.yougattme.gatt.rsc.RunningSpeedAndCadenceProfileData
import de.tobiga.yougattme.tools.CrankRevolutionSimulator
import de.tobiga.yougattme.tools.WheelRevolutionSimulator
import java.time.Instant

class ApplicationViewModel : ViewModel() {

    val cyclingPowerData = CyclingPowerProfileData()
    val runningSpeedAndCadenceData = RunningSpeedAndCadenceProfileData()
    val cyclingSpeedAndCadenceData = CyclingSpeedAndCadenceProfileData()
    val ftmsCrossTrainerData = CrossTrainerData()
    val ftmsTreadmillData = TreadmillData()
    val ftmsIndoorBikeData = IndoorBikeData()
    val ftmsRowerData = RowerData()

    val ftmsReceivedTargetInclination = MutableLiveData<Double>(0.0)

    var isCyclingPowerProfileActive = MutableLiveData<Boolean>(false)

    private val mutableSelectedItem = MutableLiveData<Int>()
    val selectedItem: LiveData<Int> get() = mutableSelectedItem

    fun selectItem(item: Int) {
        mutableSelectedItem.value = item
    }

    var fitnessMachineHasBeenStarted = MutableLiveData<Boolean>(false)

    var selectedFitnessMachineMode = MutableLiveData<String>("tobias_ding_dong")
    var isFitnessMachineActive = MutableLiveData<Boolean>(false)
    var isFitnessMachineRower = MutableLiveData<Boolean>(false)

    var receivedTargetPowerInControlPoint = MutableLiveData<Int>(0)
    var controlHasBeenTakenInControlPoint = MutableLiveData<Boolean>(false)
    var receivedIndoorBikeGrade = MutableLiveData<Double>(0.0)


    var isRunningSpeedAndCadenceActive = MutableLiveData<Boolean>(false)


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



    fun moveForwardInTimeAndEvents() {
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

