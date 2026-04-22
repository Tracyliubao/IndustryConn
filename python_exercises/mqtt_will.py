import time
import paho.mqtt.client as mqtt
import logging

def on_connect(client, userdata, flags, rc):
	print(f"已连接，返回码是{rc}")

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
# 模拟存入数据库
def savetodatabase(data):
	print(data)

client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message

# 设置遗嘱消息
# 遗嘱：当客户端异常断线时，Broker会自动发布这条消息
# retain表示会保留消息，新订阅者上线也能立即看到状态
client.will_set(topic = "liubao/will", payload = "offline", qos = 1, retain = True)

if connect_with_retry(client, "broker.hivemqx.com", 1883):
	client.loop_start()
	# 等待异步连接完成
	time.sleep(2)

	value = [258] #实际用read_register
	if value:
		topic = "liubao/will"
		payload = str(value[0])
		client.publish(topic, payload, qos = 1)
	
	client.loop_stop()
	client.disconnect()
else :
	value = [258]
	if value:
		savetodatabase(str(value[0]))
	print("MQTT 连接失败，数据暂存本地，程序退出")
	exit(0)







	






	