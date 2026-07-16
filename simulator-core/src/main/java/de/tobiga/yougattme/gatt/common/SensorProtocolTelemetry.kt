package de.tobiga.yougattme.gatt.common

data class SensorMeasurementSnapshot(
    val serviceKey: String,
    val characteristicUuid: String,
    val logicalRecordHex: String,
    val notificationPacketHex: List<String>,
    val payloadBytes: Int,
    val timestampMillis: Long
)

data class SensorProtocolEvent(
    val sequence: Long,
    val timestampMillis: Long,
    val direction: String,
    val serviceKey: String,
    val title: String,
    val payloadHex: String
)
