package de.tobiga.yougattme.gatt.heartrate

data class HeartRateSimulatorConfiguration(
    var heartRateBpm: Int = 120,
    var useUInt16Format: Boolean = false,
    var sensorContactSupported: Boolean = true,
    var sensorContactDetected: Boolean = true,
    var includeEnergyExpended: Boolean = true,
    var energyExpendedKj: Int = 0,
    var includeRrIntervals: Boolean = true,
    var rrIntervalsSeconds: List<Double> = listOf(0.8),
    var bodySensorLocationCharacteristicEnabled: Boolean = true,
    var bodySensorLocation: Int = 1,
    var controlPointCharacteristicEnabled: Boolean = true
)
