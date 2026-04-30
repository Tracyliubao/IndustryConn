package com.tracy.industry.util

/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/4 20:16
 */
object ModbusUtils {

    private fun calculateCrc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            for (i in 0 until 8) {
                if (crc and 1 == 1) {
                    crc = (crc shr 1) xor 0xA001
                } else {
                    crc = crc shr 1
                }
            }
        }
        return crc
    }

    /**
     * 构造读保持寄存器指令（功能码 03）
     *
     * 完整帧格式：地址(1B) + 功能码(1B) + 起始地址(2B) + 寄存器数量(2B) + CRC(2B小端)
     *
     * @param deviceAddr 设备地址（1-247）
     * @param startReg 起始寄存器地址（0-65535）
     * @param regCount 读取寄存器数量（1-125）
     * @return 8字节完整指令
     */
    fun buildReadHoldingRegistersCmd(deviceAddr: Int = 1, startReg: Int = 0, regCount: Int = 1): ByteArray {

        // 参数合法性校验
        require(deviceAddr in 1..247) { "设备地址必须在1-247之间，当前值: $deviceAddr" }
        require(startReg in 0..65535) { "起始地址必须在0-65535之间" }
        require(regCount in 1..125) { "寄存器数量必须在1-125之间" }

        val cmd = ByteArray(8)

        cmd[0] = deviceAddr.toByte()           // 设备地址
        cmd[1] = 0x03.toByte()                 // 功能码：读保持寄存器
        cmd[2] = (startReg shr 8).toByte()     // 起始地址高字节
        cmd[3] = (startReg and 0xFF).toByte()  // 起始地址低字节
        cmd[4] = (regCount shr 8).toByte()     // 数量高字节
        cmd[5] = (regCount and 0xFF).toByte()  // 数量低字节

        val crc = calculateCrc16(cmd.sliceArray(0..5))
        // Modbus 规定 CRC 小端序：低字节在前
        cmd[6] = (crc and 0xFF).toByte()            // CRC 低字节
        cmd[7] = ((crc shr 8) and 0xFF).toByte()    // CRC 高字节
        return cmd
    }

    fun parseReadHoldingRegistersResponse(resp: ByteArray, regCount: Int): List<Int> {
        if (resp.size < 5) return emptyList()
        // 获取除CRC部分
        val dataPart = resp.copyOfRange(0, resp.size - 2)
        // 本地计算CRC
        val expectedCrc = calculateCrc16(dataPart)

        val receivedCrc = (resp[resp.size - 2].toInt() and 0xFF) or
                ((resp[resp.size - 1].toInt() and 0xFF) shl 8)
        if (expectedCrc != receivedCrc) {
            DebugLog.e("Modbus CRC校验失败，期望: ${expectedCrc.toHexString()}, 实际: ${receivedCrc.toHexString()}")
            return emptyList()
        }
        // 检验功能码最高位是不是1
        if (resp[1].toInt() and 0x80 != 0) return emptyList()
        // 检验标注的数据长度与真实的数据长度是否匹配
        if (resp[2].toInt() != regCount * 2) return emptyList()
        // 开始解析收到的数据
        val values = mutableListOf<Int>()
        var offset = 3
        for (i in 0 until regCount) {
            val high = resp[offset].toInt() and 0xFF
            val low = resp[offset + 1].toInt() and 0xFF
            values.add((high shl 8) or low)
            offset += 2
        }
        return values
    }
}
/**
 * 辅助扩展函数：将 Int 格式化成 4 位十六进制字符串，方便调试日志
 */
private fun Int.toHexString(): String {
    return String.format("%04X", this)
}