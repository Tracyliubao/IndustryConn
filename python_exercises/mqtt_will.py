import time
import paho.mqtt.client as mqtt
import logging

def on_connect(client, userdata, flags, rc):
	print(f"已连接，返回码是{rc}")
	client.publish("liubao/will", "online", qos = 1, retain = True)

def on_message(client, userdata, msg):
	print(f"收到消息：{msg.topic} -> {msg.payload.decode()}")

def connect_with_retry(client, host, port, max = 3):
	count = 0
	while count < max:
		try:
			client.connect(host, port, 60) # 设置60秒心跳间隔
			return True
		except Exception as e:
			count += 1
			logging.error(f"已重试连接{count}次")
			time.sleep(2) #每2秒重连一次

	return False

client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message

# 设置遗嘱消息
# 遗嘱：当客户端异常断线时，Broker会自动发布这条消息
# retain表示会保留消息，新订阅者上线也能立即看到状态
client.will_set(topic = "liubao/will", payload = "offline", qos = 1, retain = True)

if connect_with_retry(client, "broker.hivemq.com", 1883):
	client.loop_start()
	# 等待异步连接完成
	time.sleep(2)

	# 模拟正在工作
	print("设备正在工作中。。。")
	time.sleep(10)
	
	client.publish("liubao/will", "offline_normal", qos = 1)
	print("正常下线")
	time.sleep(1)
	
	client.loop_stop()
	client.disconnect()
else :
	print("连接失败")
	exit(1)







	






	