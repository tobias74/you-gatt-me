package de.tobiga.yougattme.gatt.cyclingpower

interface CyclingPowerBroadcastSource {
    fun setCyclingPowerBroadcastListener(listener: ((enabled: Boolean, payload: ByteArray) -> Unit)?)
}
