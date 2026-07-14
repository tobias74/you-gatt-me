package de.tobiga.yougattme.tools

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun byteToInt(bytes: ByteArray): Int {
    var result = 0
    var shift = 0
    for (byte in bytes) {
        result = result or (byte.toInt() shl shift)
        shift += 8
    }
    return result
}


fun bytesToInt16(bytes: ByteArray): Short {
    require(bytes.size == 2) { "ByteArray must contain exactly 2 bytes" }
    val int16 = (bytes[1].toInt() shl 8) or (bytes[0].toInt() and 0xFF)
    return int16.toShort()
}
