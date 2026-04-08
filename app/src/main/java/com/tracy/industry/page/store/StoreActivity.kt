package com.tracy.industry.page.store

import android.content.Intent
import android.net.Uri
import com.tracy.industry.R
import com.tracy.industry.base.BaseBindingActivityKt
import com.tracy.industry.databinding.ActivityStoreBinding
import com.tracy.industry.ui.theme.generateViewModel
import com.tracy.industry.ui.theme.showMessage
import com.tracy.industry.util.ConfParams
import com.tracy.industry.util.IndustrialConfigManager
import com.tracy.industry.util.IndustrialTimeUtils

/**
 * Des:本地存储页
 * Author:LiuBao
 * Time:2026/4/8 21:00
 */
class StoreActivity : BaseBindingActivityKt<ActivityStoreBinding, StoreViewModel>(){

    private val IMPORT_FILE_REQUEST_CODE = 1002

    override fun createViewModel(): StoreViewModel = generateViewModel(StoreViewModel::class.java)

    override fun getLayoutResource(): Int  = R.layout.activity_store

    override fun onSubCreate() {
        mBinding.tvInsert.setOnClickListener {
            mModel.insertDevice()
        }

        mBinding.tvName.setOnClickListener {
            mModel.queryDevice()
        }

        mBinding.tvExport.setOnClickListener {
            // 先导出
            val file = IndustrialConfigManager.getInstance().exportConfigToJson()
            // 再保存
            val result = IndustrialConfigManager.getInstance().saveConfigToFile(file)
            if (result){
                showMessage("导出成功")
            }
            else {
                showMessage("导出失败")
            }
        }

        mBinding.tvImport.setOnClickListener {
            // 打开系统文件选择器，选择JSON配置文件
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json" // 只显示JSON文件
            }
            startActivityForResult(intent, IMPORT_FILE_REQUEST_CODE)
        }

        mBinding.tvCrash.setOnClickListener {
            val str: String? = null
            str!!.length
        }

        mBinding.tvStopCollect.setOnClickListener {
            IndustrialTimeUtils.stopCollectTask()
            ConfParams.getMMKVInstance().encode(ConfParams.KEY_COLLECT_RUNNING, false)
        }

        initObserver()
    }

    private fun initObserver(){
        mModel.deviceData.observe(this){
            it?.apply {
                mBinding.tvName.text = this.deviceName
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // 将URI转为文件路径（适配Android 10+分区存储）
                val filePath = getFilePathFromUri(uri)
                if (filePath.isNullOrEmpty()) {
                    showMessage("无法获取文件路径")
                    return
                }

                // 调用导入方法
                val importResult = IndustrialConfigManager.getInstance().importConfigFromFile(filePath)
                when (importResult) {
                    is IndustrialConfigManager.ImportResult.Success -> {
                        showMessage("导入成功")
                        // 刷新UI，显示导入后的配置
                        refreshConfigUI()
                    }
                    else -> {
                        showMessage("导入失败")
                    }
                }
            }
        }
    }

    // URI转文件路径（关键：适配Android 10+）
    private fun getFilePathFromUri(uri: Uri): String? {
        if ("file".equals(uri.scheme, true)) {
            return uri.path
        }
        if (!"content".equals(uri.scheme, true)) {
            return null
        }
        val resolver = contentResolver
        val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && c.moveToFirst()) c.getString(idx) else null
        } ?: "import.json"
        val dir = java.io.File(ConfParams.DIR_CONFIG)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val outFile = java.io.File(dir, name)
        resolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (true) {
                    read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        } ?: return null
        return outFile.absolutePath
    }

    // 刷新配置UI（示例：显示导入后的IP、端口等）
    private fun refreshConfigUI() {
        mBinding.tvContent.text = "${IndustrialConfigManager.getInstance().getDeviceIp()}\t" +
                "${IndustrialConfigManager.getInstance().getDevicePort()}\t" +
                "${IndustrialConfigManager.getInstance().getPlcType()}"
    }
}