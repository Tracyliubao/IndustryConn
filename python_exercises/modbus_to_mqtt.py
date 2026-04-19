import time
import paho.mqtt.client as mqtt
import logging

from modbus_tool import read_register

# 连接失败重试
def connect_mqtt_with_retry(client, host, port, max_retries = 5):
	retries = 0
	while retries < max_retries:
		try:
			client.connect(host, port, 60)
			return True
		except Exception as e:
			retries += 1
			logging.error(f"MQTT 连接失败（第{retries}次尝试）: {e}")
			time.sleep(5 * retries)
	return False

# 1.连接回调，因为不考虑于通过broker与其他端通信，所以不需要on_message回调
def on_connect (client, userdata, flag, rc):
	print(f"MQTT 已连接，返回码：{rc}")

# 2.创建mqtt实例
client = mqtt.Client()
# 3.将写好的回调复制给client
client.on_connect = on_connect

# 4.向指定 Broker 发起连接（非阻塞，立即返回）
result = connect_mqtt_with_retry(client, "broker.hivemq.com", 1883)
if result: 
	# 5.使用线程连接，避免阻塞主线程，启动后台网络线程，处理 MQTT 通信
	client.loop_start()
	# 6.等待 MQTT 连接建立（异步连接需要时间）
	time.sleep(2)
	
	# 7.使用Mock模式，假装返回258，一个包含单个寄存器值 258 的列表
	register_values = []
	print(f"Modbus 读取结果：{register_values}")
	
	# 8.如果响应码不为空
	if register_values:
		# 9. 设置主题
		topic = "liubao/modbus/value"
		# 10.设置发送的信息
		payload = str(register_values[0])
		# 11.开始发布，指定qos=1，至少发送一次，确保服务器端收到信息
		client.publish(topic, payload, qos=1)
		print(f"已发布消息到主题'{topic}': {payload}")
	else :
		print("未读取到寄存器值，不发布")
	
	# 12. 睡眠2秒，模拟上传时间
	time.sleep(2)
	# 13.停止线程
	client.loop_stop()
	# 14.与服务器断开连接
	client.disconnect()
else :
	print("连接失败，不发布")

		


