package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

/**
 * One optional FTMS measurement field (or inseparable group of fields) and the
 * flag bit that announces it in the corresponding machine data characteristic.
 */
internal data class FtmsOptionalField(
    val flagBit: Int,
    val value: ByteArray
)

/**
 * Encodes one logical FTMS Data Record. If it exceeds the ATT notification
 * payload, the record is split according to FTMS 4.19: preliminary packets set
 * More Data (bit 0), while mandatory fields are present only in the final packet.
 */
internal data class FtmsDataRecord(
    val flagsWidth: Int,
    val mandatoryFields: ByteArray,
    val optionalFields: List<FtmsOptionalField>,
    val fixedFlags: Int = 0
) {
    init {
        require(flagsWidth in 1..3) { "FTMS flags must occupy 1, 2, or 3 octets" }
        optionalFields.forEach { field ->
            require(field.flagBit in 1 until flagsWidth * 8) {
                "Optional field flag ${field.flagBit} does not fit in $flagsWidth octets"
            }
        }
    }

    fun encode(maxPacketSize: Int = Int.MAX_VALUE): List<ByteArray> {
        require(maxPacketSize >= flagsWidth + mandatoryFields.size) {
            "ATT payload is too small for mandatory FTMS fields"
        }

        val complete = packet(
            fields = optionalFields,
            moreData = false,
            includeMandatory = true
        )
        if (complete.size <= maxPacketSize) {
            return listOf(complete)
        }

        val finalCapacity = maxPacketSize - flagsWidth - mandatoryFields.size
        var finalFieldStart = optionalFields.size
        var finalBytes = 0
        while (finalFieldStart > 0) {
            val candidate = optionalFields[finalFieldStart - 1]
            if (finalBytes + candidate.value.size > finalCapacity) {
                break
            }
            finalBytes += candidate.value.size
            finalFieldStart -= 1
        }

        val result = mutableListOf<ByteArray>()
        val preliminary = optionalFields.subList(0, finalFieldStart)
        var packetFields = mutableListOf<FtmsOptionalField>()
        var packetBytes = flagsWidth

        preliminary.forEach { field ->
            require(flagsWidth + field.value.size <= maxPacketSize) {
                "FTMS field ${field.flagBit} cannot fit in one ATT notification"
            }
            if (packetFields.isNotEmpty() && packetBytes + field.value.size > maxPacketSize) {
                result += packet(packetFields, moreData = true, includeMandatory = false)
                packetFields = mutableListOf()
                packetBytes = flagsWidth
            }
            packetFields += field
            packetBytes += field.value.size
        }
        if (packetFields.isNotEmpty()) {
            result += packet(packetFields, moreData = true, includeMandatory = false)
        }

        result += packet(
            fields = optionalFields.subList(finalFieldStart, optionalFields.size),
            moreData = false,
            includeMandatory = true
        )
        return result
    }

    private fun packet(
        fields: List<FtmsOptionalField>,
        moreData: Boolean,
        includeMandatory: Boolean
    ): ByteArray {
        var flags = fixedFlags
        if (moreData) {
            flags = flags or 0x01
        }
        fields.forEach { field -> flags = flags or (1 shl field.flagBit) }

        val writer = FtmsPacketWriter()
        when (flagsWidth) {
            1 -> writer.u8(flags)
            2 -> writer.u16(flags)
            3 -> writer.u24(flags)
        }
        if (includeMandatory) {
            writer.raw(mandatoryFields)
        }
        fields.forEach { field -> writer.raw(field.value) }
        return writer.bytes()
    }
}

internal fun ftmsField(flagBit: Int, write: FtmsPacketWriter.() -> Unit): FtmsOptionalField {
    return FtmsOptionalField(flagBit, FtmsPacketWriter().apply(write).bytes())
}

internal fun ftmsMandatory(write: FtmsPacketWriter.() -> Unit): ByteArray {
    return FtmsPacketWriter().apply(write).bytes()
}
