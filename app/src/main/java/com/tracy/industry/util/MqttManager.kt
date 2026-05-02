package com.tracy.industry.util

import com.tracy.industry.base.MyApplication
import com.tracy.industry.database.DeviceRepository
import com.tracy.industry.database.entity.InfoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence


/**
 * Des:Mqtt连接工具类
 * Author:LiuBao
 * Time:2026/3/15 21:42
 */
class MqttManager {
    companion object {
        private const val TAG = "IndustrialMqtt"
        private const val MQTT_BROKER = "tcp://broker.hivemq.com:1883"
        // 客户端ID：工业场景用设备SN，这里用时间戳保证唯一
        private val MQTT_CLIENT_ID = "android_industrial_${System.currentTimeMillis()}"
        private val repository = DeviceRepository()
    }

    private lateinit var mqttClient: MqttAndroidClient
    private var isConnected = false
    private val topic = "liubao/test"
    private val mqttScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 统一处理数据库操作，避免在回调线程中直接调用suspend函数。
     */
    private fun runDbTask(taskName: String, block: suspend DeviceRepository.() -> Unit) {
        mqttScope.launch {
            runCatching { repository.block() }
                .onFailure { e -> DebugLog.e("数据库操作失败[$taskName]：${e.message}") }
        }
    }

    /**
     * 连接MQTT服务器（工业场景：断线自动重连）
     */
    fun connect(onSuccess: () -> Unit, onFailed: (String) -> Unit) {
        // 初始化MQTT客户端
        mqttClient = MqttAndroidClient(MyApplication.instance, MQTT_BROKER, MQTT_CLIENT_ID, MemoryPersistence())

        // 连接参数，类似于Python的argparse.ArgumentParser
        val options = MqttConnectOptions().apply {
            isCleanSession = false // 保留会话，重连后收离线消息
            keepAliveInterval = 30 // MQTT协议层心跳（不是Wi-Fi心跳），Broker超过1.5倍此时间未收到消息判定离线
            connectionTimeout = 10 // 连接超时10秒
            isAutomaticReconnect = true // 断线自动重连
            // 遗嘱消息
            setWill(topic, "offline".toByteArray(), 1, true)
        }

        // 连接回调
        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                isConnected = true
                // reconnect 表示是否重连过，true表示重连过，且已重连成功，false表示初次连接
                DebugLog.e("MQTT连接成功")
                onSuccess()
                runDbTask("queryInfoOnConnect") {
                    val queryInfo = queryInfo().firstOrNull().orEmpty()
                    if (queryInfo.isNotEmpty()){
                        for (temp in queryInfo){
                            publish(temp.content, temp.id, true)
                        }
                    }
                }
            }

            // 连接失败
            override fun connectionLost(cause: Throwable?) {
                isConnected = false
                DebugLog.e("MQTT连接断开：${cause?.message}")
            }

            // 接收服务器下发的指令（比如：触发BLE采集）
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val cmd = String(message?.payload ?: byteArrayOf())
                DebugLog.e("收到工业指令[$topic]：$cmd")
                // 工业场景：接收到指令后调用BleManager采集数据
                // 比如 cmd == "collect_ble" → bleManager.readSensorData()
            }

            // 消息成功送达 Broker 的确认（仅 QoS 1/2）
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // 消息已成功送达 Broker，可以安全删除本地缓存
            }
        })

        // 发起连接
        try {
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    // 发送连接请求成功，连接是否成功要看connectComplete回调
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    onFailed(exception?.message ?: "连接失败")
                }
            })
        } catch (e: Exception) {
            onFailed(e.message ?: "连接异常")
        }
    }

    /**
     * 发布数据
     * @param topic 工业规范Topic：/industrial/ble/设备ID/数据类型
     * @param data 比如：{"deviceId":"BLE001","temp":25.5,"time":1710588000000}
     */
    fun publish(data: String, infoId: Int = -1, isResend: Boolean = false){
        val mqttMsg = MqttMessage(data.toByteArray()).apply {
            qos = 1
            isRetained = true
        }
        try {
            if (isConnected){
                mqttClient.publish(topic, mqttMsg)
                if (infoId > -1){
                    runDbTask("deleteOnDelivered") { deleteInfo(infoId) }
                }
            }
            else {
                DebugLog.e("Mqtt发送失败")
                // 实时数据才需要保存，补传数据不存入数据库
                if (!isResend){
                    runDbTask("insertInfoOnPublishFailed") {
                        insertInfo(InfoEntity(content = data, status = 0))
                    }
                }
            }
        }
        catch (e: Exception){
            e.printStackTrace()
            DebugLog.e("发送失败")
            // 实时数据才需要保存，补传数据不存入数据库
            if (!isResend){
                runDbTask("insertInfoOnPublishFailed") {
                    insertInfo(InfoEntity(content = data, status = 0))
                }
            }
        }
    }

    /**
     * 定时调用，发送云端
     */
    fun publishFromModbus(sp: SerialPortManager) {
        if (!isConnected) return
        val cmd = ModbusUtils.buildReadHoldingRegistersCmd(1, 0, 1)
        if (!sp.send(cmd)) return
        val resp = sp.read()
        val values = ModbusUtils.parseReadHoldingRegistersResponse(resp, 1)
        if (values.isNotEmpty()) {
            publish(values[0].toString())
        }
    }

    /**
     * 断开连接（Service销毁时调用）
     */
    fun disconnect() {
        if (::mqttClient.isInitialized && isConnected) {
            try {
                mqttClient.disconnect()
                isConnected = false
            } catch (e: Exception) {
                DebugLog.e("断开失败：${e.message}")
            }
        }
        mqttScope.cancel()
    }
}
