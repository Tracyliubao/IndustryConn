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
        private val BROKER_URL = "tcp://broker.hivemq.com:1883"
        private val clientId = "Industry_conn_${System.currentTimeMillis()}"
    }

    private lateinit var mqttClient: MqttAndroidClient
    private var isConnected = false
    private val topic = "liubao/test"

    fun connect(onSuccess: () -> Unit, onFailure: (String) -> Unit){
        mqttClient = MqttAndroidClient(MyApplication.instance, BROKER_URL, clientId, MemoryPersistence())

        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            keepAliveInterval = 30
            connectionTimeout = 10
            setWill(topic, "offline".toByteArray(), 1, true)
        }

        mqttClient.setCallback(object : MqttCallbackExtended{
            override fun connectionLost(cause: Throwable?) {
                cause?.printStackTrace()
                // 连接断开
                isConnected = false
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                // 收到服务器下发指令
                DebugLog.e("topic=${topic}，message=${message?.payload}")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // 确认服务是否收到发送请求
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                // 连接成功
                isConnected = true
                onSuccess()
            }

        })

        try {
            mqttClient.connect(options, null, object : IMqttActionListener{
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    // 发送连接请求成功
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    // 发送失败
                    isConnected = false
                    onFailure(exception?.message ?: "")
                }

            })
        }
        catch (e: Exception){
            isConnected = false
            onFailure(e.message ?: "")
        }

    }

    fun publish(data: String){
        if (!isConnected){
            DebugLog.e("Mqtt已断开，发送失败")
            return
        }
        val mqttMsg = MqttMessage(data.toByteArray()).apply {
            qos = 1
            isRetained = true
        }
        try {
            mqttClient.publish(topic, mqttMsg)
        }
        catch (e: Exception){
            e.printStackTrace()
            DebugLog.e("发送失败")
        }
    }

    fun disconnect(){
        try {
            if (::mqttClient.isInitialized && isConnected){
                mqttClient.disconnect()
                isConnected = false
            }
        }
        catch (e: Exception){
            e.printStackTrace()
            DebugLog.e("断开连接失败")
        }
    }

}