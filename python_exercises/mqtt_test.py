import paho.mqtt.client as mqtt
import time

received_message = []
subscribed = False  # 订阅完成标志

# 5. “连接成功”自动调用
def on_connect(client, userdata, flags, rc):
    # 6. 告诉 Python：我要修改外面那个全局变量 subscribed
    global subscribed
    # 7. rc=0 代表成功，非0代表失败（像 HTTP 状态码）
    print(f"已连接至 HiveMQ Broker，返回码: {rc}")
    # 订阅主题
    client.subscribe("liubao/test", qos = 1)
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
    content = msg.payload.decode()
    # 获取消息的QoS等级
    qos = msg.qos
    # 13. 打印主题和内容
    print(f"收到消息: QoS = {qos}, topic = {msg.topic}, content = {content}")
    received_message.append(content)

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
print('发布QoS 0 消息。。。')
client.publish("liubao/test", "这条消息可能丢失", qos = 0) #只发一次
time.sleep(1)

print("发布QoS 1 消息。。。")
client.publish("liubao/test", "这条消息保证到达", qos = 1) #至少发一次，确保对方收到
time.sleep(3)

client.loop_stop()
client.disconnect()

print(f"总共收到{len(received_message)}条消息")
if len(received_message) == 2:
    print(f"【成功】收到两条消息")
elif len(received_message) == 1:
    print(f"【成功】收到一条消息")
else:
    print("【失败】两条消息都没收到")

#nc -vz mqtt.eclipseprojects.io 1883
#nc -vz broker.hivemq.com 1883

