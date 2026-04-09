package com.tracy.industry.page.serial

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.tracy.industry.R
import com.tracy.industry.base.BaseBindingActivityKt
import com.tracy.industry.databinding.ActivitySerialPortBinding
import com.tracy.industry.service.ForegroundService
import com.tracy.industry.ui.theme.generateViewModel
import com.tracy.industry.ui.theme.showMessage
import com.tracy.industry.util.DebugLog
import com.tracy.industry.util.SerialPortUtil

/**
 * Des:串口相关功能
 * Author:LiuBao
 * Time:2026/4/8 21:14
 */
class SerialPortActivity: BaseBindingActivityKt<ActivitySerialPortBinding, SerialPortViewModel>() {

    // 工业设备常见串口路径（测试用，实际根据设备调整）
    private val serialPortPath = "/dev/ttyS1"
    private lateinit var serialPortUtil: SerialPortUtil
    private val TEST_HEX_COMMAND = "010300000001"

    private var foreService: ForegroundService? = null

    override fun createViewModel(): SerialPortViewModel = generateViewModel(SerialPortViewModel::class.java)

    override fun getLayoutResource(): Int = R.layout.activity_serial_port

    override fun onSubCreate() {

        // 启动前台Service
        startForegroundService()
        // 配置串口数据
        // 2. 初始化串口工具类（配置9600波特率，工业默认参数）
        serialPortUtil = SerialPortUtil(
            portPath = serialPortPath,
            baudRate = 9600,
            dataBits = 8,
            stopBits = 1,
            parity = 0
        )

        mBinding.tvSerial.setOnClickListener {
            val isOpen = serialPortUtil.openSerialPort()
            mBinding.tvSerial.text = if (isOpen) "已打开" else "未开启"
        }

        mBinding.tvCloseSerial.setOnClickListener {
            serialPortUtil.closeSerialPort()
            showMessage("已关闭")
        }

        mBinding.tvCheckSerial.setOnClickListener {
            val portConfig = serialPortUtil.getPortConfig()
            mBinding.tvCheckSerial.text = portConfig
        }

        mBinding.tvSend.setOnClickListener {
            val message = serialPortUtil.sendHexCommand(TEST_HEX_COMMAND)
            mBinding.tvSend.text = message
        }

        mBinding.tvReceive.setOnClickListener {
            serialPortUtil.receiveHexData(true) { hexData ->
                runOnUiThread {
                    if (hexData != null) {
                        val temperature = serialPortUtil.parseTemperatureFromHex(hexData)
                        val parseResult = if (temperature != null) {
                            "解析成功：$temperature℃"
                        } else {
                            "解析失败"
                        }
                        mBinding.tvReceive.text = parseResult
                    }
                }
            }
        }

        mBinding.tvRead.setOnClickListener {
            val regValues = serialPortUtil.readHoldingRegisters(deviceAddr = 1, startReg = 0, regCount = 1)
            if (regValues.isNotEmpty()) {
                // 工业场景：温度值=寄存器值/10
                val temperature = regValues[0] / 10.0
                mBinding.tvRead.text = "寄存器原始值：${regValues[0]}，解析温度值：${temperature}℃"
            } else {
                mBinding.tvRead.text = "读寄存器失败"
            }
        }

        mBinding.tvWrite.setOnClickListener {
            val success = serialPortUtil.writeSingleRegister(deviceAddr = 1, regAddr = 1, value = 100)
            mBinding.tvWrite.text = if (success) {
                "寄存器地址：1写入值：100"
            } else {
                "写寄存器失败"
            }
        }

        mBinding.tvState.postDelayed(kotlinx.coroutines.Runnable {
            mBinding.tvState.text = foreService?.getTemperature()
        }, 10000)
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            DebugLog.e("LoginService bind success")
            foreService = (service as ForegroundService.MyBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName) {
            DebugLog.e()
        }
    }

    // 启动前台Service的方法
    private fun startForegroundService() {
        val serviceIntent = Intent(this, ForegroundService::class.java)
        // Android O+ 启动前台Service要用startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        bindService(Intent(this, ForegroundService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        serialPortUtil.closeSerialPort()
        // 结束后台服务
        stopService(Intent(this, ForegroundService::class.java))
        unbindService(serviceConnection)
    }
}