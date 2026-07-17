package de.tobiga.yougattme

import androidx.lifecycle.ViewModel

/**
 * Compatibility facade for the XML UI and existing profile providers.
 * New long-lived code should depend on [stateStore] or [SimulationStateStore] directly.
 */
class ApplicationViewModel(
    val stateStore: SimulationStateStore = SimulationStateStore.shared
) : ViewModel() {
    val bridgeState get() = stateStore.bridgeState
    val runningSpeedAndCadenceData get() = stateStore.runningSpeedAndCadenceData
    val rscConfiguration get() = stateStore.rscConfiguration
    val cscConfiguration get() = stateStore.cscConfiguration
    val cyclingPowerConfiguration get() = stateStore.cyclingPowerConfiguration
    val heartRateConfiguration get() = stateStore.heartRateConfiguration
    val ftmsCrossTrainerData get() = stateStore.ftmsCrossTrainerData
    val ftmsTreadmillData get() = stateStore.ftmsTreadmillData
    val ftmsIndoorBikeData get() = stateStore.ftmsIndoorBikeData
    val ftmsRowerData get() = stateStore.ftmsRowerData
    val ftmsStairClimberData get() = stateStore.ftmsStairClimberData
    val ftmsStepClimberData get() = stateStore.ftmsStepClimberData
    val ftmsTargetFeatures get() = stateStore.ftmsTargetFeatures
    val ftmsDeclaredTargetFeatures get() = stateStore.ftmsDeclaredTargetFeatures
    var ftmsControlPointEnabled: Boolean
        get() = stateStore.ftmsControlPointEnabled
        set(value) { stateStore.ftmsControlPointEnabled = value }
    var ftmsMirrorTargetFeatureDeclarations: Boolean
        get() = stateStore.ftmsMirrorTargetFeatureDeclarations
        set(value) { stateStore.ftmsMirrorTargetFeatureDeclarations = value }
    val ftmsSupportedRanges get() = stateStore.ftmsSupportedRanges
    val ftmsTargetState get() = stateStore.ftmsTargetState
    val ftmsTrainingStatus get() = stateStore.ftmsTrainingStatus
    val ftmsControlOwnerAddress get() = stateStore.ftmsControlOwnerAddress
    val ftmsControlProcedureState get() = stateStore.ftmsControlProcedureState
    val ftmsSessionDurationMinutes get() = stateStore.ftmsSessionDurationMinutes
    val ftmsMeasurementSnapshot get() = stateStore.ftmsMeasurementSnapshot
    val ftmsProtocolEvents get() = stateStore.ftmsProtocolEvents
    val sensorMeasurementSnapshots get() = stateStore.sensorMeasurementSnapshots
    val sensorProtocolEvents get() = stateStore.sensorProtocolEvents
    val indoorCyclingSimulationParameters get() = stateStore.indoorCyclingSimulationParameters
    val ftmsReceivedTargetInclination get() = stateStore.ftmsReceivedTargetInclination
    var isCyclingPowerProfileActive
        get() = stateStore.isCyclingPowerProfileActive
        set(value) { stateStore.isCyclingPowerProfileActive = value }
    val selectedItem get() = stateStore.selectedItem
    val fitnessMachineHasBeenStarted get() = stateStore.fitnessMachineHasBeenStarted
    val selectedFitnessMachineMode get() = stateStore.selectedFitnessMachineMode
    val receivedTargetPowerInControlPoint get() = stateStore.receivedTargetPowerInControlPoint
    val controlHasBeenTakenInControlPoint get() = stateStore.controlHasBeenTakenInControlPoint
    val receivedIndoorBikeGrade get() = stateStore.receivedIndoorBikeGrade
    val fitnessMachineRowerStrokesPerMinute get() = stateStore.fitnessMachineRowerStrokesPerMinute
    val fitnessMachineRowerStrokesTotal get() = stateStore.fitnessMachineRowerStrokesTotal
    val cscIsProfileActive get() = stateStore.cscIsProfileActive
    val cscSpeedKmH get() = stateStore.cscSpeedKmH
    val cscCadence get() = stateStore.cscCadence
    var cscCumulativeWheelRevolutions: Long
        get() = stateStore.cscCumulativeWheelRevolutions
        set(value) { stateStore.cscCumulativeWheelRevolutions = value }
    var cscCumulativeCrankRevolutions: Int
        get() = stateStore.cscCumulativeCrankRevolutions
        set(value) { stateStore.cscCumulativeCrankRevolutions = value }
    var cscLastWheelEventTime: Long
        get() = stateStore.cscLastWheelEventTime
        set(value) { stateStore.cscLastWheelEventTime = value }
    var cscLastCrankEventTime: Long
        get() = stateStore.cscLastCrankEventTime
        set(value) { stateStore.cscLastCrankEventTime = value }
    var cyclingPowerSpeedKmH: Double
        get() = stateStore.cyclingPowerSpeedKmH
        set(value) { stateStore.cyclingPowerSpeedKmH = value }
    var cyclingPowerCadence: Int
        get() = stateStore.cyclingPowerCadence
        set(value) { stateStore.cyclingPowerCadence = value }
    var cyclingPowerWatts: Int
        get() = stateStore.cyclingPowerWatts
        set(value) { stateStore.cyclingPowerWatts = value }
    var instantaneousSpeed: Float
        get() = stateStore.instantaneousSpeed
        set(value) { stateStore.instantaneousSpeed = value }
    var instantaneousCadence: Int
        get() = stateStore.instantaneousCadence
        set(value) { stateStore.instantaneousCadence = value }
    var totalDistance: Int
        get() = stateStore.totalDistance
        set(value) { stateStore.totalDistance = value }
    var instantaneousPower: Int
        get() = stateStore.instantaneousPower
        set(value) { stateStore.instantaneousPower = value }
    val instantaneousPowerLiveData get() = stateStore.instantaneousPowerLiveData
    val cumulativeWheelRevolutions get() = stateStore.cumulativeWheelRevolutions
    val cumulativeCrankRevolutions get() = stateStore.cumulativeCrankRevolutions
    val lastWheelEventTime get() = stateStore.lastWheelEventTime
    val lastCrankEventTime get() = stateStore.lastCrankEventTime
    val cyclingPowerCrankRevolutionSequence get() = stateStore.cyclingPowerCrankRevolutionSequence

    fun ftmsTargetFeatureDeclarationsForGatt() = stateStore.ftmsTargetFeatureDeclarationsForGatt()
    fun syncFtmsDeclaredTargetFeaturesFromActual() = stateStore.syncFtmsDeclaredTargetFeaturesFromActual()
    fun updateSimulationParameters(windSpeed: Float, grade: Double, crr: Float, cw: Float) =
        stateStore.updateSimulationParameters(windSpeed, grade, crr, cw)
    fun selectItem(item: Int) = stateStore.selectItem(item)
    fun postInstantaneousSpeed(value: Float) = stateStore.postInstantaneousSpeed(value)
    fun postInstantaneousCadence(value: Int) = stateStore.postInstantaneousCadence(value)
    fun postWheelCircumference(value: Float) = stateStore.postWheelCircumference(value)
    fun postInstantaneousPower(value: Int) = stateStore.postInstantaneousPower(value)
    fun setCyclingPowerCumulativeWheelRevolutions(value: Long) =
        stateStore.setCyclingPowerCumulativeWheelRevolutions(value)
    fun applyCscCumulativeWheelRevolutions(value: Long) =
        stateStore.applyCscCumulativeWheelRevolutions(value)
    fun advanceTime(timePassedMillis: Long, lastMovedTimestamp: Long) =
        stateStore.advanceTime(timePassedMillis, lastMovedTimestamp)
    fun resetCyclingPowerConnectionAccumulators() = stateStore.resetCyclingPowerConnectionAccumulators()
    fun recordFtmsMeasurement(machineType: String, characteristicUuid: String, logicalRecord: ByteArray,
        packets: List<ByteArray>, previewPayloadBytes: Int) =
        stateStore.recordFtmsMeasurement(machineType, characteristicUuid, logicalRecord, packets, previewPayloadBytes)
    fun recordFtmsProtocolEvent(direction: String, channel: String, title: String, payload: ByteArray) =
        stateStore.recordFtmsProtocolEvent(direction, channel, title, payload)
    fun clearFtmsProtocolEvents() = stateStore.clearFtmsProtocolEvents()
    fun recordSensorMeasurement(serviceKey: String, characteristicUuid: String, logicalRecord: ByteArray,
        packets: List<ByteArray>, payloadBytes: Int) =
        stateStore.recordSensorMeasurement(serviceKey, characteristicUuid, logicalRecord, packets, payloadBytes)
    fun recordSensorProtocolEvent(serviceKey: String, direction: String, title: String, payload: ByteArray) =
        stateStore.recordSensorProtocolEvent(serviceKey, direction, title, payload)
    fun clearSensorProtocolEvents(serviceKey: String) = stateStore.clearSensorProtocolEvents(serviceKey)
    fun updateFtmsTargetState(update: (de.tobiga.yougattme.gatt.fitnessmachine.FtmsTargetState) ->
        de.tobiga.yougattme.gatt.fitnessmachine.FtmsTargetState) = stateStore.updateFtmsTargetState(update)
    fun clearFtmsTargetState() = stateStore.clearFtmsTargetState()
    fun resetFtmsControlState() = stateStore.resetFtmsControlState()
    fun reset() = stateStore.reset()
    fun moveForwardInTimeAndEvents() = stateStore.moveForwardInTimeAndEvents()
}
