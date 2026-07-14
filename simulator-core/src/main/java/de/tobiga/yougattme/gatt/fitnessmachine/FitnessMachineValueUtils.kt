package de.tobiga.yougattme.gatt.fitnessmachine

import kotlin.math.roundToInt

internal const val FTMS_UINT8_MAX = 0xFF
internal const val FTMS_UINT16_MAX = 0xFFFF
internal const val FTMS_UINT24_MAX = 0xFFFFFF
internal const val FTMS_SINT16_MIN = -0x8000
internal const val FTMS_SINT16_MAX = 0x7FFF
internal const val FTMS_JOULES_PER_KILOCALORIE = 4184.0

internal fun Int.ftmsUInt8(): Int = coerceIn(0, FTMS_UINT8_MAX)
internal fun Int.ftmsUInt16(): Int = coerceIn(0, FTMS_UINT16_MAX)
internal fun Int.ftmsUInt24(): Int = coerceIn(0, FTMS_UINT24_MAX)
internal fun Int.ftmsSInt16(): Int = coerceIn(FTMS_SINT16_MIN, FTMS_SINT16_MAX)
internal fun Double.roundToFtmsUInt8(): Int = roundToInt().ftmsUInt8()
internal fun Double.roundToFtmsUInt16(): Int = roundToInt().ftmsUInt16()
internal fun Double.roundToFtmsUInt24(): Int = roundToInt().ftmsUInt24()
internal fun Double.roundToFtmsSInt16(): Int = roundToInt().ftmsSInt16()
internal fun Float.roundToFtmsUInt8(): Int = roundToInt().ftmsUInt8()
internal fun Float.roundToFtmsUInt16(): Int = roundToInt().ftmsUInt16()
internal fun Float.roundToFtmsSInt16(): Int = roundToInt().ftmsSInt16()
