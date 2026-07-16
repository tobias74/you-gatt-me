package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FtmsDataRecordEncoderTest {

    @Test
    fun oversizedRecordUsesMoreDataAndKeepsMandatoryFieldsForFinalPacket() {
        val record = FtmsDataRecord(
            flagsWidth = 2,
            mandatoryFields = byteArrayOf(0x55, 0x66),
            optionalFields = listOf(
                FtmsOptionalField(1, byteArrayOf(0x11, 0x12)),
                FtmsOptionalField(2, byteArrayOf(0x21, 0x22, 0x23)),
                FtmsOptionalField(3, byteArrayOf(0x31, 0x32, 0x33, 0x34))
            )
        )

        val packets = record.encode(maxPacketSize = 8)

        assertEquals(2, packets.size)
        assertTrue(packets.all { packet -> packet.size <= 8 })
        assertArrayEquals(
            byteArrayOf(0x07, 0x00, 0x11, 0x12, 0x21, 0x22, 0x23),
            packets[0]
        )
        assertArrayEquals(
            byteArrayOf(0x08, 0x00, 0x55, 0x66, 0x31, 0x32, 0x33, 0x34),
            packets[1]
        )
    }

    @Test
    fun fittingRecordIsEncodedAsOneNotificationWithoutMoreData() {
        val record = FtmsDataRecord(
            flagsWidth = 2,
            mandatoryFields = byteArrayOf(0x55, 0x66),
            optionalFields = listOf(FtmsOptionalField(4, byteArrayOf(0x01, 0x02, 0x03)))
        )

        val packets = record.encode(maxPacketSize = 20)

        assertEquals(1, packets.size)
        assertArrayEquals(
            byteArrayOf(0x10, 0x00, 0x55, 0x66, 0x01, 0x02, 0x03),
            packets.single()
        )
    }
}
