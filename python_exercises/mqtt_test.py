import paho.mqtt.client as mqtt
import time

received_message = None
subscribed = False  # 订阅完成标志

# 5. “连接成功”自动调用
def on_connect(client, userdata, flags, rc):
    # 6. 告诉 Python：我要修改外面那个全局变量 subscribed
    global subscribed
    # 7. rc=0 代表成功，非0代表失败（像 HTTP 状态码）
    print(f"已连接至 HiveMQ Broker，返回码: {rc}")
    # 订阅主题
    client.subscribe("liubao/test")
    # 标记订阅请求已发出（实际在 Broker 侧几乎即时生效）
    # 9. 订阅请求已发出，把标志位设成 True
    subscribed = True  
    print("订阅请求已发送")

# 10. “当收到任何订阅的主题消息时”自动调用
# 相当于 Android 里的 BroadcastReceiver.onReceive()。
def on_message(client, userdata, msg):
    # 11. 告诉 Python：我要修改外面那个全局变量 received_message
    global received_message
    # 12. 把收到的字节数据转成字符串，存起来
    received_message = msg.payload.decode()
    # 13. 打印主题和内容
    print(f"收到消息: {msg.topic} -> {received_message}")

# 14. 创建一个 MQTT 客户端实例
client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message

# 注意：这是异步的，调用后不会阻塞，立即执行下一行代码
client.connect("broker.hivemq.com", 1883, 60)
# 17. 启动一个后台线程，专门负责处理网络数据的收发。
client.loop_start()

# 等待连接建立并完成订阅（最多等待 5 秒）
timeout = 5
start_time = time.time()
# 未明确订阅成功时，每0.1秒检查一次
while not subscribed and (time.time() - start_time) < timeout:
    time.sleep(0.1)

if not subscribed:
    print("订阅超时，请检查网络")
    client.loop_stop()
    client.disconnect()
    exit(1)

# 额外等待一小段时间，确保 Broker 真正记录了订阅
time.sleep(0.5)

# 现在发布消息
client.publish("liubao/test", "订阅确认后再发布，必达！")
print("已发布消息，等待接收...")

# 等待接收消息
time.sleep(3)

client.loop_stop()
client.disconnect()

if received_message:
    print(f"【成功】接收到的消息: {received_message}")
else:
    print("【失败】仍未收到消息，请稍后重试。")

#nc -vz mqtt.eclipseprojects.io 1883
#nc -vz broker.hivemq.com 1883

