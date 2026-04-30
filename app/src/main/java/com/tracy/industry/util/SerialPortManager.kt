package com.tracy.industry.util

import android.serialport.SerialPort
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Des:串口管理类
 * Author:LiuBao
 * Time:2026/4/28 20:53
 */
class SerialPortManager(
    private val portPath: String, // 串口路径（如/dev/ttyS1、/dev/ttyUSB0）
    private val baudRate: Int = 9600, // 波特率（工业常用：9600/115200/38400）
) {
    private var serialPort: SerialPort? = null
    private var inputStream: InputStream? = null // 读数据
    private var outputStream: OutputStream? = null // 写数据
    private var isOpen = false // 串口是否打开（对外暴露状态）
        private set


    @Synchronized
    fun open(): Boolean {
        return try {
            val portFile = File(portPath)
            if (!portFile.exists()) {
                DebugLog.e("串口文件不存在: $portPath")
                return false
            }
            serialPort = SerialPort(portFile, baudRate)
            inputStream = serialPort?.inputStream
            outputStream = serialPort?.outputStream
            isOpen = true
            true
        } catch (e: Exception) {
            DebugLog.e("串口打开失败: ${e.message}")
            // 失败时确保所有资源置空
            inputStream = null
            outputStream = null
            serialPort = null
            isOpen = false
            false
        }
    }

    /**
     * 发送原始字节数组
     * @return true 表示数据已写入发送缓冲区，false 表示写入失败
     */
    @Synchronized
    fun send(data: ByteArray): Boolean {
        if (!isOpen) {
            DebugLog.e("串口未打开，发送失败")
            return false
        }
        return try {
            outputStream?.write(data)
            DebugLog.e("串口发送成功: ${data.joinToString(" ") { "%02X".format(it) }}")
            true
        } catch (e: Exception) {
            DebugLog.e("串口发送失败: ${e.message}")
            false
        }
    }

    /**
     * 读取串口数据
     *
     * 工业现场优化：不强制读满 maxLen 字节，有数据就立即返回，避免死等。
     * 调用方负责判断响应帧是否完整。
     *
     * @param maxLen 单次最多读取的字节数
     * @param timeoutMs 最大等待时间（毫秒）
     * @return 实际读取到的字节数组，超时或无数据时返回空数组
     */
    @Synchronized
    fun read(maxLen: Int = 256, timeoutMs: Int = 500): ByteArray {
        if (!isOpen) {
            DebugLog.e("串口未打开，读取失败")
            return ByteArray(0)
        }
        val buffer = ByteArray(maxLen)
        var totalRead = 0
        val startTime = System.currentTimeMillis()
        try {
            while (totalRead < maxLen && (System.currentTimeMillis() - startTime) < timeoutMs) {
                val available = inputStream?.available() ?: 0
                if (available > 0) {
                    val toRead = minOf(available, maxLen - totalRead)
                    val count = inputStream?.read(buffer, totalRead, toRead) ?: 0
                    if (count > 0) {
                        totalRead += count
                        break
                    }
                } else {
                    Thread.sleep(20)
                }
            }
        } catch (e: Exception) {
            DebugLog.e("串口读取失败: ${e.message}")
        }
        return buffer.copyOf(totalRead)
    }

    @Synchronized
    fun close() {
        try {
            inputStream?.close()
            outputStream?.close()
            serialPort?.close()
        } catch (e: Exception) {
            DebugLog.e("串口关闭失败: ${e.message}")
        } finally {
            inputStream = null
            outputStream = null
            serialPort = null
            isOpen = false
        }
    }
}