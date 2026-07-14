package de.tobiga.yougattme.gatt.fitnessmachine

/**
 * A data class to hold all FTMS simulation parameters as a single, atomic unit.
 */
data class IndoorCyclingSimulationParameters(
    val windSpeedMps: Float = 0.0f,
    val gradePercent: Double = 0.0,
    val crr: Float = 0.004f, // Coefficient of Rolling Resistance
    val cw: Float = 0.5f     // Wind Resistance Coefficient
)
