package com.tracy.industry.socket

import android.os.Handler
import android.os.Looper
import com.tracy.industry.util.DebugLog
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * 工业级WebSocket长连接管理器
 * 核心特性：
 * 1. 自动重连（网络波动/服务器重启）
 * 2. 心跳保活（30秒ping包，避免连接被回收）
 * 3. 二进制/文本双传输（适配工业数据）
 * 4. 全日志追溯（连接/收发/异常）
 * 5. 资源自动释放（无内存泄漏）
 * 6. 网络切换重连（WiFi/4G/内网）
 * Author:LiuBao
 * Time:2026/3/11 21:36
 */
class WebSocketManager {
    companion object {
        // 心跳间隔（工业标准30秒）
        private const val HEARTBEAT_INTERVAL = 5 * 1000L
        // 重连间隔（3秒，避免频繁重试）
        private const val RECONNECT_INTERVAL = 3 * 1000L
        // 最大重连次数（-1表示无限重连）
        private const val MAX_RECONNECT_COUNT = -1

        @Volatile
        private var instance: WebSocketManager? = null

        fun getInstance(): WebSocketManager {
            return instance ?: synchronized(this) {
                instance ?: WebSocketManager().also {
                    instance = it
                }
            }
        }
    }

    // 核心对象
    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var request: Request? = null
    // 状态控制
    private var isConnected = false
    private var reconnectCount = 0
    private var isManualDisconnect = false
    // 心跳处理器
    private val mainHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable: Runnable = object : Runnable {
        override fun run() {
            if (isConnected) {
                // 发送工业级ping包
                webSocket?.send("ping")
                DebugLog.e("发送心跳ping包")
            }
            // 直接用this，指向当前Runnable，循环执行
            mainHandler.postDelayed(this, HEARTBEAT_INTERVAL)
        }
    }
    // 回调接口（对外暴露）
    interface Callback {
        // 连接状态变化
        fun onConnectionStateChanged(isConnected: Boolean)
        // 接收文本数据（指令/状态）
        fun onTextDataReceived(data: String)
        // 接收二进制数据（设备原始字节）
        fun onBinaryDataReceived(data: ByteArray)
        // 异常回调（便于问题定位）
        fun onError(code: Int, msg: String)
    }
    private var callback: Callback? = null

    /**
     * 初始化连接配置
     * @param wsUrl WebSocket地址（ws://xxx 或 wss://xxx）
     * @param callback 回调接口
     */
    fun init(wsUrl: String, callback: Callback) {
        this.callback = callback
        // 1. 构建OkHttpClient（工业级配置）
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
        // 2. 构建请求
        request = Request.Builder()
            .url(wsUrl)
            // 工业场景添加自定义请求头（设备标识/协议版本）
            .addHeader("Device-Type", "Android-Industrial")
            .addHeader("Protocol-Version", "V1.0")
            .build()
    }

    /**
     * 启动连接
     */
    fun connect() {
        if (isManualDisconnect) {
            DebugLog.e("手动断开后禁止自动重连，请调用reconnect()")
            return
        }
        if (okHttpClient == null || request == null) {
            callback?.onError(-1, "未初始化连接配置")
            return
        }
        // 关闭旧连接
        disconnect()
        // 建立新连接
        webSocket = okHttpClient?.newWebSocket(request!!, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                isConnected = true
                reconnectCount = 0
                isManualDisconnect = false
                DebugLog.e("连接成功：${response.message}")
                // 启动心跳
                startHeartbeat()
                // 回调连接状态
                callback?.onConnectionStateChanged(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                DebugLog.e("接收文本数据：$text")
                callback?.onTextDataReceived(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                val data = bytes.toByteArray()
                DebugLog.e("接收二进制数据，长度：${data.size}")
                callback?.onBinaryDataReceived(data)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                handleDisconnect(code, "连接关闭：$reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                val errMsg = "连接失败：${t.message}"
                DebugLog.e(errMsg)
                callback?.onError(response?.code ?: -2, errMsg)
                handleDisconnect(-1, errMsg)
            }
        })
    }

    /**
     * 处理断开连接（自动触发重连）
     */
    private fun handleDisconnect(code: Int, msg: String) {
        isConnected = false
        // 停止心跳
        stopHeartbeat()
        // 回调连接状态
        callback?.onConnectionStateChanged(false)
        callback?.onError(code, msg)
        // 自动重连逻辑
        if (!isManualDisconnect) {
            if (MAX_RECONNECT_COUNT == -1 || reconnectCount < MAX_RECONNECT_COUNT) {
                reconnectCount++
                DebugLog.e("准备重连（第${reconnectCount}次），间隔${RECONNECT_INTERVAL/1000}秒")
                mainHandler.postDelayed({ connect() }, RECONNECT_INTERVAL)
            } else {
                DebugLog.e("重连次数已达上限，停止重连")
            }
        }
    }

    /**
     * 手动重连（手动断开后调用）
     */
    fun reconnect() {
        isManualDisconnect = false
        connect()
    }

    /**
     * 手动断开连接（停止重连）
     */
    fun disconnect() {
        isManualDisconnect = true
        isConnected = false
        stopHeartbeat()
        webSocket?.close(1000, "手动断开连接")
        webSocket = null
        DebugLog.e("手动断开连接")
    }

    /**
     * 发送文本数据（工业指令/状态上报）
     */
    fun sendTextData(data: String): Boolean {
        return if (isConnected && webSocket != null) {
            webSocket?.send(data)
            DebugLog.e("发送文本数据：$data")
            true
        } else {
            DebugLog.e("连接未建立，发送文本数据失败：$data")
            callback?.onError(-3, "连接未建立，发送失败")
            false
        }
    }

    /**
     * 发送二进制数据（BLE/串口采集的原始字节）
     */
    fun sendBinaryData(data: ByteArray): Boolean {
        return if (isConnected && webSocket != null) {
            webSocket?.send(ByteString.of(*data))
            DebugLog.e("发送二进制数据，长度：${data.size}")
            true
        } else {
            DebugLog.e("连接未建立，发送二进制数据失败，长度：${data.size}")
            callback?.onError(-3, "连接未建立，发送失败")
            false
        }
    }

    /**
     * 释放所有资源（APP退出时调用）
     */
    fun release() {
        disconnect()
        mainHandler.removeCallbacksAndMessages(null)
        okHttpClient?.dispatcher?.executorService?.shutdown()
        okHttpClient = null
        callback = null
        instance = null
        DebugLog.e("资源已全部释放")
    }

    /**
     * 启动心跳
     */
    private fun startHeartbeat() {
        stopHeartbeat()
        mainHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL)
    }

    /**
     * 停止心跳
     */
    private fun stopHeartbeat() {
        mainHandler.removeCallbacks(heartbeatRunnable)
    }

    /**
     * 获取当前连接状态
     */
    fun isConnected(): Boolean = isConnected
}