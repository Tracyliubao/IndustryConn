package com.tracy.industry.page.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tracy.industry.R
import com.tracy.industry.base.BaseBindingActivityKt
import com.tracy.industry.databinding.ActivityMainBinding
import com.tracy.industry.service.ForegroundService
import com.tracy.industry.ui.theme.generateViewModel
import com.tracy.industry.util.DebugLog

class MainActivity : BaseBindingActivityKt<ActivityMainBinding, MainViewModel>() {

    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

    override fun createViewModel(): MainViewModel = generateViewModel(MainViewModel::class.java)

    override fun getLayoutResource(): Int = R.layout.activity_main

    override fun onSubCreate() {
        initPermission()

        mBinding.tvShow.setOnClickListener {
            mModel.insertDevice()
        }

        mBinding.tvName.setOnClickListener {
            mModel.queryDevice()
        }

        mModel.deviceData.observe(this){
            it?.apply {
                mBinding.tvName.text = this.deviceName
            }
        }
    }

    /**
     * 开始准备数据
     */
    private fun prepareData(){

        //创建基础目录
        mModel.createDir()

        // 启动前台Service
        startForegroundService()
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
    }

    private fun initPermission(){
        // 1. 检查并申请所有必要权限
        val permissionsToRequest = mutableListOf<String>()

        // Android 13+ 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 检查存储读取权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // 检查存储写入权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // 有需要申请的权限
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // 所有权限都已授予
            prepareData()
        }
    }

    /**
     * 处理权限申请结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 用户授予权限，启动Service
                startForegroundService()
            } else {
                // 用户拒绝权限，通知无法显示，Service也无法正常运行
                println("用户拒绝了通知权限，前台Service无法显示常驻通知")
            }
        }
    }

}
