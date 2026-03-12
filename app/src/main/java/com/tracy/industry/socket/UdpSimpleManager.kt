package com.tracy.industry.socket

import com.tracy.industry.util.DebugLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Des:建议UDP连接
 * Author:LiuBao
 * Time:2026/3/12 21:32
 */
class UdpSimpleManager {
    private val UDP_PORT = 8899
    private var socket: DatagramSocket? = null

    interface UdpCallback{
        fun onReceive(message: String)
    }

    // 初始化 UDP
    fun init(callback: UdpCallback) {
        try {
            socket = DatagramSocket(UDP_PORT)
            DebugLog.e("UDP 初始化成功，端口：$UDP_PORT")
            startReceive(callback)
        } catch (e: Exception) {
            DebugLog.e("UDP 初始化失败：${e.message}")
        }
    }

    // 发送 UDP 文本
    fun sendText(text: String) {
        Thread {
            try {
                val address = InetAddress.getByName("127.0.0.1") // 本机
                val data = text.toByteArray()
                val packet = DatagramPacket(data, data.size, address, UDP_PORT)
                socket?.send(packet)
                DebugLog.e("发送：$text")
            } catch (e: Exception) {
                DebugLog.e("发送失败：${e.message}")
            }
        }.start()
    }

    // 后台接收 UDP
    private fun startReceive(callback: UdpCallback) {
        Thread {
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                while (true) {
                    socket?.receive(packet)
                    val receiveStr = String(packet.data, 0, packet.length)
                    DebugLog.e("接收：$receiveStr")
                    callback.onReceive(receiveStr)
                }
            } catch (e: Exception) {
                DebugLog.e("接收停止：${e.message}")
            }
        }.start()
    }

    // 释放
    fun release() {
        socket?.close()
    }
}