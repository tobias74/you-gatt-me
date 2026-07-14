package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import kotlin.math.roundToInt

internal class FtmsPacketWriter {
    private val data = mutableListOf<Byte>()

    fun u8(value: Int) {
        data.add((value and 0xFF).toByte())
    }

    fun u16(value: Int) {
        data.add((value and 0xFF).toByte())
        data.add(((value shr 8) and 0xFF).toByte())
    }

    fun u24(value: Int) {
        data.add((value and 0xFF).toByte())
        data.add(((value shr 8) and 0xFF).toByte())
        data.add(((value shr 16) and 0xFF).toByte())
    }

    fun s16(value: Int) {
        u16(value)
    }

    fun bytes(): ByteArray = data.toByteArray()
}

internal fun Int.u8(): Int = coerceIn(0, 0xFF)
internal fun Int.u16(): Int = coerceIn(0, 0xFFFF)
internal fun Int.u24(): Int = coerceIn(0, 0xFFFFFF)
internal fun Int.s16(): Int = coerceIn(-0x8000, 0x7FFF)

internal fun Float.kmhToHundredths(): Int = (this * 100f).roundToInt().u16()
internal fun Double.metersToUInt24(): Int = roundToInt().u24()
internal fun Double.secondsToUInt16(): Int = roundToInt().u16()
internal fun Float.tenths(): Int = (this * 10f).roundToInt()
internal fun Double.tenths(): Int = (this * 10.0).roundToInt()

internal const val JOULES_PER_KILOCALORIE = 4184.0
