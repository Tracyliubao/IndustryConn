package com.tracy.industry.util

/**
 * Des:
 * Author:LiuBao
 * Time:2026/4/11 17:02
 */
object Crc16Helper {
    // 预先计算好的 CRC16 查找表 (256个值)
    private val table = IntArray(256) {
        var crc = it
        for (j in 0..7) {
            crc = if (crc and 1 != 0) {
                (crc ushr 1) xor 0xA001
            } else {
                crc ushr 1
            }
        }
        crc and 0xFFFF
    }

    fun calculate(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            val index = (crc xor (b.toInt() and 0xFF)) and 0xFF
            crc = (crc ushr 8) xor table[index]
        }
        return crc and 0xFFFF
    }
}