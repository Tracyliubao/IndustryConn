package com.tracy.industry.util
 
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.tencent.mmkv.MMKV
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * 工业App配置管理类（适配PLC/设备连接、采集参数等工业配置）
 */
class IndustrialConfigManager private constructor() {
    // MMKV 实例（工业场景推荐单例）
    private val mmkv = ConfParams.getMMKVInstance()

    // 工业配置项常量（按需扩展）
    companion object {


        // 单例模式（工业App通用）
        @Volatile
        private var INSTANCE: IndustrialConfigManager? = null
        fun getInstance(): IndustrialConfigManager {
            return INSTANCE ?: synchronized(this) {
                val instance = IndustrialConfigManager()
                INSTANCE = instance
                instance
            }
        }
    }

    // --------------- 读取配置 ---------------
    fun getDeviceIp(): String = mmkv.decodeString(ConfParams.KEY_DEVICE_IP, "") ?: ""
    fun getDevicePort(): Int = mmkv.decodeInt(ConfParams.KEY_DEVICE_PORT, ConfParams.DEFAULT_PORT)
    fun getPlcType(): String = mmkv.decodeString(ConfParams.KEY_PLC_TYPE, "") ?: ""
    fun getSampleRate(): Int = mmkv.decodeInt(ConfParams.KEY_SAMPLE_RATE, ConfParams.DEFAULT_SAMPLE_RATE)
    fun getAlarmThreshold(): Int = mmkv.decodeInt(ConfParams.KEY_ALARM_THRESHOLD, ConfParams.DEFAULT_ALARM_THRESHOLD)

    // --------------- 清空配置（工业场景核心）---------------
    /**
     * 清空自定义配置（保留默认值，工业场景推荐）
     * 避免清空后设备无法基础连接
     */
    fun clearCustomConfig() {
        mmkv.encode(ConfParams.KEY_DEVICE_IP, "")
        mmkv.encode(ConfParams.KEY_PLC_TYPE, "")
        // 保留默认端口、采集频率等基础参数
        mmkv.encode(ConfParams.KEY_DEVICE_PORT, ConfParams.DEFAULT_PORT)
        mmkv.encode(ConfParams.KEY_SAMPLE_RATE, ConfParams.DEFAULT_SAMPLE_RATE)
    }

    /**
     * 重置所有配置（恢复出厂设置，谨慎使用）
     */
    fun resetAllConfig() {
        mmkv.clearAll()
    }

    // --------------- 导出配置（工业场景核心）---------------
    /**
     * 导出配置为JSON字符串
     */
    fun exportConfigToJson(): String {
        // 封装配置数据
        val configData = IndustrialConfig(
            deviceIp = getDeviceIp(),
            devicePort = getDevicePort(),
            plcType = getPlcType(),
            sampleRate = getSampleRate(),
            alarmThreshold = getAlarmThreshold()
        )
        // Gson转为JSON（工业上位机通用格式）
        return Gson().toJson(configData)
    }

    /**
     * 将JSON配置保存到本地文件
     * @param json 配置JSON字符串
     * @return 保存成功返回文件路径，失败返回null
     */
    fun saveConfigToFile(json: String): Boolean {
        // 工业App文件存储路径（外部存储，用户可访问）
        val configDir = File(ConfParams.DIR_CONFIG)
        if (!configDir.exists()) {
            configDir.mkdirs() // 创建目录（工业场景需处理创建失败）
        }
        // 文件名带时间戳（便于区分版本，工业备份常用）
        val fileName = "config.json"
        val configFile = File(configDir, fileName)

        return try {
            FileWriter(configFile).use { writer ->
                writer.write(json)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    // --------------- 导入配置（工业场景核心）---------------
    /**
     * 从本地JSON文件导入配置
     * @param filePath 配置文件路径（如/storage/emulated/0/industrial_config/config_1710000000000.json）
     * @return ImportResult 导入结果（成功/失败原因）
     */
    fun importConfigFromFile(filePath: String): ImportResult {
        // 1. 校验文件是否存在
        val configFile = File(filePath)
        if (!configFile.exists() || !configFile.isFile) {
            return ImportResult.Failure("配置文件不存在")
        }

        return try {
            // 2. 读取文件并解析JSON
            val gson = Gson()
            val configData = FileReader(configFile).use { reader ->
                gson.fromJson(reader, IndustrialConfig::class.java)
            }

            // 3. 工业参数合法性校验（核心：避免非法配置导致设备故障）
            val validateResult = validateIndustrialConfig(configData)
            if (!validateResult.isValid) {
                return ImportResult.Failure("参数不合法：${validateResult.errorMsg}")
            }

            // 4. 写入MMKV存储（覆盖原有配置）
            mmkv.encode(ConfParams.KEY_DEVICE_IP, configData.deviceIp)
            mmkv.encode(ConfParams.KEY_DEVICE_PORT, configData.devicePort)
            mmkv.encode(ConfParams.KEY_PLC_TYPE, configData.plcType)
            mmkv.encode(ConfParams.KEY_SAMPLE_RATE, configData.sampleRate)
            mmkv.encode(ConfParams.KEY_ALARM_THRESHOLD, configData.alarmThreshold)

            ImportResult.Success("配置导入成功")
        } catch (e: JsonSyntaxException) {
            ImportResult.Failure("JSON格式错误：${e.message}")
        } catch (e: IOException) {
            ImportResult.Failure("文件读取失败：${e.message}")
        } catch (e: Exception) {
            ImportResult.Failure("导入异常：${e.message}")
        }
    }

    /**
     * 工业配置参数合法性校验（工业场景必须！）
     * 比如IP格式、端口范围、采集频率不能为0等
     */
    private fun validateIndustrialConfig(config: IndustrialConfig): ValidateResult {
        // 校验IP格式
        if (config.deviceIp.isNotEmpty()) {
            try {
                InetAddress.getByName(config.deviceIp)
            } catch (e: UnknownHostException) {
                return ValidateResult(false, "设备IP格式错误：${config.deviceIp}")
            }
        }

        // 校验端口范围（工业常用：1-65535，502是Modbus默认端口）
        if (config.devicePort < 1 || config.devicePort > 65535) {
            return ValidateResult(false, "端口号非法（需1-65535）：${config.devicePort}")
        }

        // 校验采集频率（工业场景：最小100ms，避免高频采集导致设备卡顿）
        if (config.sampleRate < 100) {
            return ValidateResult(false, "采集频率过低（最小100ms）：${config.sampleRate}ms")
        }

        // 校验报警阈值（示例：0-100）
        if (config.alarmThreshold < 0 || config.alarmThreshold > 100) {
            return ValidateResult(false, "报警阈值非法（需0-100）：${config.alarmThreshold}")
        }

        return ValidateResult(true, "")
    }

    // 导入结果密封类（便于上层处理不同结果）
    sealed class ImportResult {
        data class Success(val msg: String) : ImportResult()
        data class Failure(val errorMsg: String) : ImportResult()
    }

    // 校验结果数据类
    private data class ValidateResult(
        val isValid: Boolean,
        val errorMsg: String
    )

    // 工业配置数据类（与JSON字段对应）
    data class IndustrialConfig(
        val deviceIp: String,
        val devicePort: Int,
        val plcType: String,
        val sampleRate: Int,
        val alarmThreshold: Int
    )
}
