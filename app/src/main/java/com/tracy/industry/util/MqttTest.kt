package com.tracy.industry.util

import com.tracy.industry.base.MyApplication
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
/**
 * Des:Mqtt连接测试类
 * Author:LiuBao
 * Time:2026/4/25 20:30
 */
class MqttTest {

    companion object {
        private const val TAG = "IndustrialMqtt"
        private const val MQTT_BROKER = "tcp://broker.hivemq.com:1883"
        private val MQTT_CLIENT_ID = "android_industrial_${System.currentTimeMillis()}"
    }

    private lateinit var mqttClient: MqttAndroidClient
    private var isConnected = false

    /**
     * 连接MQTT服务器
     * 1. 初始化 MqttAndroidClient
     * 2. 配置 MqttConnectOptions（心跳、超时、自动重连、遗嘱消息）
     * 3. 设置 MqttCallbackExtended（连接成功/断开/收到消息/送达确认）
     * 4. 调用 connect 方法
     */
    fun connect(onSuccess: () -> Unit, onFailed: (String) -> Unit) {
        // 初始化client
        mqttClient = MqttAndroidClient(MyApplication.instance, MQTT_BROKER, MQTT_CLIENT_ID, MemoryPersistence())

        // 连接参数
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            keepAliveInterval = 30
            connectionTimeout = 10
            isAutomaticReconnect = true
            setWill("liubao/test", "offline".toByteArray(), 1, true)
        }

        // 获取回调信息
        mqttClient.setCallback(object : MqttCallbackExtended{

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                // 已连接
                isConnected = true
                onSuccess()
            }

            override fun connectionLost(cause: Throwable?) {
                // 连接断开
                isConnected = false
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                // 收到服务器下发指令
                DebugLog.e("topic=${topic}, message=${message?.payload}")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // 消息发送的确认，仅qos = 1, 2时有效
            }

        })

        // 开始连接
        try {
            mqttClient.connect(options, null, object : IMqttActionListener{
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    // 发送连接请求成功
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    // 发送连接请求失败
                    onFailed(exception?.message ?: "")
                }

            })
        }
        catch (e: Exception){
            e.printStackTrace()
            onFailed(e.message ?: "")
            // 连接失败
        }

    }

    /**
     * 发布消息
     * 1. 检查连接状态
     * 2. 构造 MqttMessage（QoS=1, retain=true）
     * 3. 调用 publish
     */
    fun publishData(topic: String, data: String) {
        if (!isConnected){
            DebugLog.e("Mqtt未连接，发送失败")
            return
        }
        try {
            val mqttMsg = MqttMessage(data.toByteArray()).apply {
                qos = 1
                isRetained = true
            }
            mqttClient.publish(topic, mqttMsg)
        }
        catch (e: Exception){
            e.printStackTrace()
            DebugLog.e("发送失败")
        }

    }

    /**
     * 断开连接
     */
    fun disconnect() {
        try {
            if (::mqttClient.isInitialized && isConnected){
                mqttClient.disconnect()
                isConnected = false
            }
        }
        catch (e: Exception){
            e.printStackTrace()
            DebugLog.e("断开连接异常")
        }

    }
}