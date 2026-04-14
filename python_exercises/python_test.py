# 这是一条注释
# print("hello world")
# print('"Hello, World"')
# print('she doesn\'t is my father')
# print(r'C:\this\name') # 引号前加r，可以避免转义
# print('''\
# Usage: thingy [OPTIONS]
# 	 -h                        Display this usage message
# 	 -H hostname 
# ''')
# addr = 3
# noformat = 'adf adf adf'
# print(f'{addr}father')
# print(str(3) + 'father')
# print(str.format(noformat))
# string = 'python'
# print(string[-5]) #y
# print(string[:2]) #py
# print(string[2:]) #thon

#实战
# #day1
# address = input()
# print(f'正在连接设备地址{address}')
# com1 = 1
# com2 = 3
# print(f'{com1:02X}{com2:02X}')
# num = 258
# print(f'{num:04X}')

# list = [1,2,3,4,5]
# list2 = list[:2]
# print(list)
# print(list2)

# #day2
# temperatures = [25, 31, 28, 33, 27]
# for x in temperatures:
# 	if (x > 30):
# 		print(x)
# commend = '01030201023815'
# print(commend[:-4])
# com = int(input())
# pote = [9600, 19200, 115200]
# if com in pote:
# 	print('波特率正确')
# else:
# 	print('波特率错误')

#day3
# def hex_to_int(hex_str):
# 	return int(hex_str, 16)#直接将16进制转成10进制
# def build_cmd(addr, func):
# 	return f'{addr:02X}{func:02X}'
# def extract_data(commend):
# 	return commend[0:-4]
# print(hex_to_int('0102'))
# print(build_cmd(1,3))
# print(extract_data("01030201023815"))

# #day4
# import struct
# packField = struct.pack('>H', 258)
# print(packField.hex().upper())
# unPackField = struct.unpack('>H', b'\x01\x02')#unpack固定返回元组
# print(unPackField[0])
# packText = struct.pack('>BBHH', 0x01, 0x03, 0x00, 0x01)#B-1字节无符号字节，H2字节无符号短整数
# print(packText.hex().upper())

#day5
import argparse
parser = argparse.ArgumentParser(description='Modbus RTU调试工具')
parser.add_argument('--port', required = True, help = '串口号')
parser.add_argument('--baud', type = int, default = 9600, help = '波特率')
parser.add_argument('--addr', type = int, default = 1, help = '设备地址')
parser.add_argument('--start', type = int, default = 0, help = '起始寄存器地址')
parser.add_argument('--count', type = int, default = 1, help = '读取寄存器数量')
#解析
args = parser.parse_args()

print(f'端口：{args.port}')
print(f'波特率：{args.baud}')
print(f'设备地址：{args.addr}')
print(f'起始地址：{args.start}')
print(f'寄存器数量：{args.count}')

#day6
import serial
try:
	ser = serial.Serial(port = 'COM999')
	ser.close()
except serial.SerialException:
	print('串口不存在')

print(serial.__version__)

config = {
	'baudrate': 9600,
	'timeout': 1
}
print(config)


