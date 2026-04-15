import argparse
import struct
import serial

def calc_crc16(data: bytes) -> int:
	# 1. 初始化 CRC 寄存器为 0xFFFF（Modbus 标准规定）
	crc = 0xFFFF
	 # 2. 遍历输入数据的每一个字节
	for byte in data:
		# ^ 是异或运算符：crc ^= byte 等价于 crc = crc ^ byte
        # 将当前 CRC 的低 8 位与新字节进行异或操作
		crc ^= byte
		# 3. 对当前字节的每一位进行处理（共 8 位）
		for x in range(8):
			# & 是按位与运算符：crc & 1 检查 CRC 的最低位是否为 1
            # 若最低位为 1，说明需要执行异或多项式操作
			if crc & 1:
				# >> 是右移运算符：crc >> 1 将 CRC 右移一位（相当于除以 2）
                # ^ 是异或运算符：右移后与多项式 0xA001 异或
				crc = (crc >> 1) ^ 0xA001
			else :
				# 最低位为 0，仅右移一位
				crc = crc >> 1
	# 4. 返回最终计算出的 16 位 CRC 值（大端序原始值）
	return crc

def build_read_reg_cmd(device_addr: int, start_reg: int, reg_count: int) -> bytes:
	data = struct.pack('>BBHH', device_addr, 0x03, start_reg, reg_count)
	# 生成大端序CRC
	crc = calc_crc16(data)
	# 转为小端序CRC
	small_crc = struct.pack('<H', crc)
	return data + small_crc

def parse_response(resp: bytes):
	if len(resp) < 5:
		return None, '响应过短'
	# 校验CRC
	data_part = resp[:-2]
	received_crc = struct.unpack('<H', resp[-2:])[0]
	calc_crc = calc_crc16(data_part)
	if received_crc != calc_crc:
		return None, f'CRC校验失败，收到: {received_crc:04X}，计算: {calc_crc:04X}'
		# 表示功能码出错了，异常功能码
	if data_part[1] & 0x80:
		# 表示哪里出错了，异常码
		return None, f'设备返回异常码: {data_part[2]:02X}'
	# 解析寄存器值
	byte_count = data_part[2]
	# 因为前 3 个字节（地址、功能码、字节数）是协议头，所以需要-3，才是真正的数据
	if byte_count != len(data_part) - 3:
		return None, "数据长度不匹配"
	reg_value = []
	for i in range(3, len(data_part), 2):
		val = struct.unpack('>H', data_part[i:i+2])[0]
		reg_value.append(val)
	return reg_value, '成功'

def main():
    parser = argparse.ArgumentParser(description='Modbus RTU 调试工具')
    parser.add_argument('port', help='串口号，例如 /dev/ttyUSB0 或 COM3')
    parser.add_argument('--baud', type=int, default=9600, help='波特率，默认9600')
    parser.add_argument('--addr', type=int, default=1, help='设备地址，默认1')
    parser.add_argument('--start', type=int, default=0, help='起始寄存器地址')
    parser.add_argument('--count', type=int, default=1, help='读取寄存器数量')
    args = parser.parse_args()

    try:
        # 打开串口
        ser = serial.Serial(
            port=args.port,
            baudrate=args.baud,
            bytesize=serial.EIGHTBITS,
            parity=serial.PARITY_NONE,
            stopbits=serial.STOPBITS_ONE,
            timeout=1
        )
        print(f"已打开 {args.port} @ {args.baud}")
    except serial.SerialException as e:
        print(f"串口打开失败: {e}")
        return

    # 构造并发送指令
    cmd = build_read_reg_cmd(args.addr, args.start, args.count)
    print(f"发送: {cmd.hex(' ').upper()}")
    ser.write(cmd)

    # 接收响应
    resp = ser.read(256)
    if not resp:
        print("无响应（超时）")
    else:
        print(f"接收: {resp.hex(' ').upper()}")
        values, msg = parse_response(resp)
        if values is not None:
            print(f"解析成功，寄存器值: {values}")
        else:
            print(f"解析失败: {msg}")

    ser.close()

if __name__ == '__main__':
    main()





