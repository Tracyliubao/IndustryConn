package com.tracy.industry.util

import android.serialport.SerialPort
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/4 20:16
 */
class SerialPortUtil(
    private val portPath: String, // 串口路径（如/dev/ttyS1、/dev/ttyUSB0）
    private val baudRate: Int = 9600, // 波特率（工业常用：9600/115200/38400）
    private val dataBits: Int = 8, // 数据位（固定8，工业通用）
    private val stopBits: Int = 1, // 停止位（固定1，工业通用）
    private val parity: Int = 0 // 校验位（0=无校验 NONE，1=奇校验 ODD，2=偶校验 EVEN）
) {
    private var serialPort: SerialPort? = null
    private var inputStream: InputStream? = null // 读数据
    private var outputStream: OutputStream? = null // 写数据
    var isPortOpen = false // 串口是否打开（对外暴露状态）
        private set

    private val TEST_READ_COMMAND = "0103020102"
    private val TEST_WRITE_COMMAND = "010600010064980A"
    private val BUFFER_SIZE = 1024       // 接收缓冲区
    private val RECEIVE_TIMEOUT = 1000L  // 接收超时（ms）
    private val receiveExecutor = Executors.newSingleThreadExecutor() // 异步接收线程池

    /**
     * 核心方法：打开串口（工业级安全校验）
     * @return true=打开成功，false=打开失败
     */
    fun openSerialPort(): Boolean {
        // 1. 前置校验：避免重复打开
        if (isPortOpen) {
            DebugLog.e("串口已打开，无需重复操作：$portPath")
            return true
        }

        // 2. 校验串口路径合法性
        val portFile = File(portPath)
        if (!portFile.exists()) {
            DebugLog.e("串口文件不存在：$portPath（普通手机/模拟器正常，工业设备需替换路径）")
            // 非真实工业设备时，跳过文件校验，只验证逻辑
            return if (portPath.startsWith("/dev/tty")) {
                simulateOpen() // 模拟打开，验证参数配置逻辑
            } else {
                false
            }
        }

        // 3. 权限校验（工业设备必备）
        if (!portFile.canRead() || !portFile.canWrite()) {
            val isChmodSuccess = portFile.setReadable(true) && portFile.setWritable(true)
            if (!isChmodSuccess) {
                DebugLog.e("串口权限不足，无法设置读写权限：$portPath")
                return false
            }
        }

        // 4. 真正打开串口（配置核心参数）
        return try {
            // SerialPort库底层已封装数据位/停止位/校验位，只需传入波特率
            serialPort = SerialPort(portFile, baudRate)
            inputStream = serialPort?.inputStream
            outputStream = serialPort?.outputStream
            isPortOpen = true

            DebugLog.e("串口打开成功！参数配置：")
            DebugLog.e("路径：$portPath | 波特率：$baudRate")
            DebugLog.e("数据位：$dataBits | 停止位：$stopBits | 校验位：${getParityDesc(parity)}")
            true
        } catch (e: SecurityException) {
            DebugLog.e("串口打开失败：权限被拒绝 - ${e.message}")
            false
        } catch (e: IOException) {
            DebugLog.e("串口打开失败：IO异常 - ${e.message}")
            false
        } catch (e: Exception) {
            DebugLog.e("串口打开失败：未知异常 - ${e.message}")
            false
        }
    }

    /**
     * 模拟打开串口（无真实设备时验证参数配置逻辑）
     */
    private fun simulateOpen(): Boolean {
        isPortOpen = true
        DebugLog.e("模拟打开串口成功（无真实设备）！参数配置：")
        DebugLog.e("路径：$portPath | 波特率：$baudRate")
        DebugLog.e("数据位：$dataBits | 停止位：$stopBits | 校验位：${getParityDesc(parity)}")
        return true
    }

    /**
     * 核心方法：关闭串口（工业级资源释放）
     * 必须在页面销毁/APP退后台时调用，避免资源泄漏
     */
    fun closeSerialPort() {
        // 1. 前置校验：未打开则直接返回
        if (!isPortOpen) {
            DebugLog.e("串口未打开，无需关闭：$portPath")
            return
        }
        receiveExecutor.shutdownNow()
        // 2. 安全释放资源（逆序关闭，避免内存泄漏）
        try {
            receiveExecutor.awaitTermination(1, TimeUnit.SECONDS)
            inputStream?.close() // 关闭输入流
            outputStream?.close() // 关闭输出流
            serialPort?.close() // 关闭串口核心对象
        } catch (e: IOException) {
            DebugLog.e("串口关闭失败：IO异常 - ${e.message}")
        } catch (e: Exception) {
            DebugLog.e("串口关闭失败：未知异常 - ${e.message}")
        } catch (e: InterruptedException) {
            receiveExecutor.shutdownNow()
        } finally {
            // 重置状态，避免二次关闭
            inputStream = null
            outputStream = null
            serialPort = null
            isPortOpen = false
            DebugLog.e("串口关闭成功：$portPath")
        }
    }

    /**
     * 辅助方法：获取校验位描述（日志/界面展示用）
     */
    private fun getParityDesc(parity: Int): String {
        return when (parity) {
            0 -> "无校验（NONE）"
            1 -> "奇校验（ODD）"
            2 -> "偶校验（EVEN）"
            else -> "未知校验位"
        }
    }

    /**
     * 对外暴露：获取当前串口配置参数（调试/展示用）
     */
    fun getPortConfig(): String {
        return "串口路径：$portPath\n" +
                "波特率：$baudRate | 数据位：$dataBits | 停止位：$stopBits\n" +
                "校验位：${getParityDesc(parity)}\n" +
                "当前状态：${if (isPortOpen) "已打开" else "已关闭"}"
    }

    /**
     * 工业级核心方法：发送十六进制指令（对外暴露，直接传十六进制字符串）
     * @param hexStr 比如"010300000001840A"（Modbus读取指令）
     * @return true=发送成功，false=发送失败
     */
    fun sendHexCommand(hexStr: String): String {
        if (!isPortOpen) {
            DebugLog.e("串口未打开，无法发送数据：$hexStr")
            return "串口未打开"
        }

        // 2. 校验十六进制字符串合法性（工业端必备）
        if (!isValidHexStr(hexStr)) {
            DebugLog.e("十六进制格式错误：$hexStr（只能包含0-9/A-F，长度为偶数）")
            return "十六进制格式错误"
        }

        // 3. 十六进制字符串转字节数组（工业数据传输的核心格式）
        val sendData = hexStrToBytes(hexStr)

        // 4. 发送字节数据
        return try {
            outputStream?.write(sendData)
            outputStream?.flush() // 强制刷新缓冲区（工业端避免数据滞留）
            DebugLog.e("十六进制指令发送成功！")
            DebugLog.e("发送指令（十六进制）：$hexStr")
            DebugLog.e("发送数据（字节数组）：${bytesToHex(sendData)}")
            "发送成功"
        } catch (e: IOException) {
            DebugLog.e("发送失败：IO异常 - ${e.message}")
            "发送失败"
        } catch (e: Exception) {
            DebugLog.e("发送失败：未知异常 - ${e.message}")
            "发送失败"
        }
    }

    /**
     * 辅助方法：十六进制字符串转字节数组（工业端通用工具）
     * 比如"0103" → byte[]{0x01, 0x03}
     */
    private fun hexStrToBytes(hexStr: String): ByteArray {
        // 先清理字符串（去掉空格、换行，转大写）
        val cleanHex = hexStr.replace(" ", "").replace("\n", "").uppercase()
        val len = cleanHex.length
        val result = ByteArray(len / 2)

        // 每2个字符转1个字节（工业端固定逻辑）
        for (i in 0 until len step 2) {
            val high = Character.digit(cleanHex[i], 16) shl 4
            val low = Character.digit(cleanHex[i + 1], 16)
            result[i / 2] = (high + low).toByte()
        }
        return result
    }

    /**
     * 辅助方法：字节数组转十六进制字符串（日志/展示用）
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val hex = Integer.toHexString(b.toInt() and 0xFF)
            if (hex.length < 2) sb.append("0")
            sb.append(hex.uppercase())
        }
        return sb.toString()
    }

    /**
     * 辅助方法：校验十六进制字符串是否合法（工业端防错必备）
     */
    private fun isValidHexStr(hexStr: String): Boolean {
        val cleanHex = hexStr.replace(" ", "").replace("\n", "")
        // 规则1：长度必须是偶数（2个字符=1个字节）
        if (cleanHex.length % 2 != 0) return false
        // 规则2：只能包含0-9、A-F（不区分大小写）
        val hexPattern = Regex("^[0-9A-Fa-f]+$")
        return hexPattern.matches(cleanHex)
    }

    /**
     * 工业级核心方法：异步接收串口数据（避免阻塞主线程）
     * @param callback 接收结果回调（返回十六进制字符串）
     */
    fun receiveHexData(isRead: Boolean, callback: (String?) -> Unit) {
        // 防呆：串口未打开直接返回null
        if (!isPortOpen) {
            DebugLog.e("串口未打开，无法接收数据")
            callback(null)
            return
        }

        callback(if (isRead) TEST_READ_COMMAND else TEST_WRITE_COMMAND)

        // 异步接收（工业通信必须异步，否则卡死UI）
//        receiveExecutor.execute {
//            val buffer = testData.toByteArray()
//            try {
//                // 设置超时（工业现场避免死等）
//                inputStream?.readTimeout = RECEIVE_TIMEOUT.toInt()
//                // 读取设备返回的字节数据
//                val readLen = inputStream?.read(buffer) ?: -1
//
//                if (readLen > 0) {
//                    // 截取有效数据（避免空字节）
//                    val validData = testData.toByteArray()
//                    // 转成十六进制字符串（方便展示/解析）
//                    val hexData = bytesToHex(validData)
//                    DebugLog.e("接收数据成功！")
//                    DebugLog.e("接收字节数：$readLen")
//                    DebugLog.e("接收数据（十六进制）：$hexData")
//                    // 回调给主线程
//                    callback(hexData)
//                } else {
//                    DebugLog.e("未接收到有效数据（读取长度：$readLen）")
//                    callback(null)
//                }
//            } catch (e: IOException) {
//                DebugLog.e("接收数据失败：IO异常 - ${e.message}")
//                callback(null)
//            } catch (e: Exception) {
//                DebugLog.e("接收数据失败：未知异常 - ${e.message}")
//                callback(null)
//            }
//        }
    }

    /**
     * 工业级实用方法：解析Modbus RTU返回的温度数据（通用模板）
     * @param hexData 设备返回的十六进制字符串（比如"01030200648439"）
     * @return 解析后的温度值（比如100.0℃），null=解析失败
     */
    fun parseTemperatureFromHex(hexData: String?): Double? {
        if (hexData.isNullOrEmpty()) return null

        try {
            // 1. 校验Modbus返回帧格式（工业协议固定规则）
            // 示例帧：01 03 02 01 02
            // 01=从站地址 03=功能码 02=数据长度 0102=温度值 8439=CRC校验
            if (hexData.length < 8 || !hexData.startsWith("0103")) {
                DebugLog.e("Modbus帧格式错误：$hexData")
                return null
            }

            // 2. 截取数据位（第7-10位，示例中是"0102"）
            val tempHex = hexData.substring(6, 10)
            // 3. 十六进制转十进制（0102=258）
            val tempInt = tempHex.toInt(16)
            return tempInt / 1.0
        } catch (e: Exception) {
            DebugLog.e("转换十进制失败：${e.message}")
            return null
        }
    }


    // ========== 2. Modbus 读寄存器（03功能码） ==========
    /**
     * 读取保持寄存器
     * @param deviceAddr 设备地址（1-247）
     * @param startReg 起始寄存器地址
     * @param regCount 读取寄存器数量
     * @return 解析后的寄存器值列表（失败返回空列表）
     */
    fun readHoldingRegisters(deviceAddr: Int, startReg: Int, regCount: Int): List<Int> {
        if (!isPortOpen) return emptyList()

        // 1. 构造Modbus读指令（十六进制字符串）
        val cmdHex = buildReadRegCmd(deviceAddr, startReg, regCount)
        DebugLog.e("构造读寄存器指令：$cmdHex")

        // 2. 发送指令 + 接收返回数据
        val latch = CountDownLatch(1)
        var receiveHex: String? = null
        sendHexCommand(cmdHex)
        receiveHexData(true) { hexData ->
            receiveHex = hexData
            latch.countDown()
        }

        // 3. 等待接收（超时1秒）
        try {
            latch.await(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            DebugLog.e("读寄存器超时")
            return emptyList()
        }

        // 4. 解析返回数据
        return if (receiveHex != null) {
            parseReadRegData(receiveHex!!, regCount)
        } else {
            emptyList()
        }
    }

    /**
     * 构造读寄存器指令（03功能码）
     */
    private fun buildReadRegCmd(deviceAddr: Int, startReg: Int, regCount: Int): String {
        // 设备地址（转2位十六进制）
        val addrHex = String.format("%02X", deviceAddr)
        // 功能码（03）
        val funcHex = "03"
        // 起始寄存器地址（转4位十六进制）
        val startRegHex = String.format("%04X", startReg)
        // 读取数量（转4位十六进制）
        val countHex = String.format("%04X", regCount)
        // 暂存指令（无CRC，模拟模式用）
        val tempCmd = "$addrHex$funcHex$startRegHex$countHex"

        val noCrc = addrHex + funcHex + startRegHex + countHex

        // 真实场景需添加CRC校验，这里简化（Day13讲CRC时补充）
        return tempCmd + getModbusCrc(noCrc) // 固定CRC（模拟用）
    }

    /**
     * 解析读寄存器返回数据
     */
    private fun parseReadRegData(hexData: String, regCount: Int): List<Int> {
        val regValues = mutableListOf<Int>()
        try {
            // 1. 校验帧格式（地址+功能码+数据长度）
            if (hexData.length < 8 || !hexData.startsWith(hexData.substring(0, 2) + "03")) {
                DebugLog.e("读寄存器返回帧格式错误：$hexData")
                return emptyList()
            }

            // 2. 数据长度位（第5-6字符）
            val dataLen = hexData.substring(4, 6).toInt(16)
            // 3. 校验数据长度是否匹配（每个寄存器2字节）
            if (dataLen != regCount * 2) {
                DebugLog.e("返回数据长度不匹配：期望${regCount*2}字节，实际${dataLen}字节")
                return emptyList()
            }

            // 4. 解析每个寄存器值（每4个字符=1个寄存器值）
            var offset = 6 // 数据起始位置
            for (i in 0 until regCount) {
                val regHex = hexData.substring(offset, offset + 4)
                regValues.add(regHex.toInt(16))
                offset += 4
            }
            DebugLog.e("解析读寄存器数据成功：$regValues")
        } catch (e: Exception) {
            DebugLog.e("解析读寄存器数据失败：${e.message}")
        }
        return regValues
    }

    // ========== 3. Modbus 写寄存器（06功能码） ==========
    /**
     * 写单个保持寄存器
     * @param deviceAddr 设备地址
     * @param regAddr 寄存器地址
     * @param value 写入值
     * @return true=写入成功，false=失败
     */
    fun writeSingleRegister(deviceAddr: Int, regAddr: Int, value: Int): Boolean {
        if (!isPortOpen) return false

        // 1. 构造Modbus写指令
        val cmdHex = buildWriteRegCmd(deviceAddr, regAddr, value)
        DebugLog.e("构造写寄存器指令：$cmdHex")

        // 2. 发送指令 + 接收返回数据
        val latch = CountDownLatch(1)
        var receiveHex: String? = null
        sendHexCommand(cmdHex)
        receiveHexData(false) { hexData ->
            receiveHex = hexData
            latch.countDown()
        }

        // 3. 等待接收（超时1秒）
        try {
            latch.await(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            DebugLog.e("写寄存器超时")
            return false
        }

        // 4. 校验返回数据（写成功返回原指令）
        return receiveHex == cmdHex
    }

    /**
     * 构造写寄存器指令（06功能码）
     */
    private fun buildWriteRegCmd(deviceAddr: Int, regAddr: Int, value: Int): String {
        // 设备地址（2位十六进制）
        val addrHex = String.format("%02X", deviceAddr)
        // 功能码（06）
        val funcHex = "06"
        // 寄存器地址（4位十六进制）
        val regHex = String.format("%04X", regAddr)
        // 写入值（4位十六进制）
        val valueHex = String.format("%04X", value)
        // 暂存指令（无CRC，模拟模式用）
        val tempCmd = "$addrHex$funcHex$regHex$valueHex"

        val noCrc = addrHex + funcHex + regHex + valueHex

        // 真实场景需添加CRC校验，这里简化
        return tempCmd + getModbusCrc(noCrc) // 固定CRC（模拟用）
    }

    // 输入：十六进制字符串（不带CRC）
    // 输出：计算好的 CRC 十六进制字符串
    private fun getModbusCrc(hexStr: String): String {
        val bytes = hexStrToBytes(hexStr)
        val crc = calcCrc16(bytes)
        return String.format("%04X", crc)
    }

    // Modbus CRC16 核心算法（固定不变）
    private fun calcCrc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            for (i in 0 until 8) {
                if ((crc and 1) != 0) {
                    crc = crc shr 1
                    crc = crc xor 0xA001
                } else {
                    crc = crc shr 1
                }
            }
        }
        // 高低字节交换（Modbus 规定）
        return ((crc and 0xFF) shl 8) or ((crc shr 8) and 0xFF)
    }

}