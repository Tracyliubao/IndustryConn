import time
import paho.mqtt.client as mqtt
import logging
from modbus_tool import read_register

MAX_PUBLISH_RETRIES = 2

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

def query_data_size():
	list = select * from table
	return list.size

def query_data():
	list = select * from table
	return list

client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message

# 设置遗嘱消息
# 遗嘱：当客户端异常断线时，Broker会自动发布这条消息
# retain表示会保留消息，新订阅者上线也能立即看到状态
client.will_set(topic = "liubao/will", payload = "offline", qos = 1, retain = True)

while True:
	if connect_with_retry(client, "broker.hivemqx.com", 1883):
		client.loop_start()
		# 等待异步连接完成
		time.sleep(2)

		pending_rows = query_data
		for row in pending_rows:
			topic, payload, qos = row
			success = False
			# 单条补传机制
			for temp in range(MAX_PUBLISH_RETRIES + 1):
				try:
					client.publish(topic, payload, qos)
					success = True
					break
				except Exception as e:
					if temp < MAX_PUBLISH_RETRIES:
						# 补传加间隔控制
						time.sleep(0.1)
				
			if success:
				delete_pending(id)
			else :
				break


		while True:
			try:
				value = read_register()
				if value:
					client.publish("liubao/will", str(value[0]), qos = 1)
				time.sleep(1)
			except Exception as e:
				break
	else :
		value = read_register()
		if value:
			savetodatabase(str(value[0]))
		print("MQTT 连接失败，数据暂存本地，程序退出")
		time.sleep(10)







	






	