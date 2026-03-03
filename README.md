# IndustryConn - 工业数据采集系统

## 项目简介

IndustryConn 是一款面向工业场景的Android数据采集应用，专注于在复杂网络环境下实现设备数据的可靠采集、存储和传输。应用采用离线优先架构，支持断网采集存储、定时任务调度、网络续传等核心功能，确保工业数据的完整性和连续性。

## 核心功能

### 1. 断网采集存储
- **离线数据存储**：使用Room数据库实现设备数据的本地持久化存储
- **自动数据采集**：支持定时采集设备信息，无网络时自动存储到本地
- **数据完整性保障**：断网不影响采集任务，确保数据不丢失

### 2. 定时任务调度
- **WorkManager集成**：利用WorkManager实现可靠的周期性任务调度
- **灵活采集周期**：支持15分钟标准采集周期，可根据工业需求调整
- **任务持久化**：应用重启后自动恢复采集任务，确保任务连续性

### 3. 网络续传
- **智能网络检测**：实时监听网络状态变化
- **自动数据上传**：网络恢复后自动将本地存储的数据上传至服务器
- **断点续传**：支持数据分批上传，避免网络波动导致的数据丢失

### 4. 强保活机制
- **前台服务**：通过ForegroundService提升进程优先级，防止系统回收
- **双进程守护**：主服务与守护进程相互监控，确保服务持续运行
- **电池优化白名单**：引导用户关闭电池优化，避免系统限制后台任务
- **开机自启**：支持设备开机后自动启动采集服务

### 5. 工业时间校准
- **每日自动校准**：每日0点自动执行工业时间校准任务
- **精准时间同步**：确保采集数据的时间戳准确性

## 技术栈

### 核心框架
- **Kotlin**：现代、简洁、安全的编程语言
- **Jetpack组件**：使用Android Jetpack最佳实践

### 数据存储
- **Room数据库**：Google官方推荐的SQLite封装库
  - 支持实体类（Entity）、数据访问对象（DAO）、数据库（Database）
  - 支持协程扩展，避免主线程操作
  - 支持数据库版本升级和数据迁移

### 任务调度
- **WorkManager**：Android官方后台任务调度框架
  - 支持周期性任务（最小15分钟）
  - 支持任务约束（电池、网络等条件）
  - 支持任务链和唯一任务
  - 应用重启后自动恢复任务

### 服务保活
- **前台服务（ForegroundService）**：
  - 显示常驻通知，提升进程优先级
  - 使用心跳机制检测服务存活状态
  - 支持通知渠道配置（Android 8.0+）
- **守护服务（GuardService）**：
  - 独立进程运行，与主服务相互监控
  - 主服务被杀死时自动重启主服务
  - 双进程架构提高服务存活率

### 数据存储
- **MMKV**：腾讯开源的高性能键值存储
  - 比SharedPreferences性能更高
  - 支持跨进程访问
  - 支持多类型数据存储

### 网络通信
- **RxJava2**：响应式编程框架
  - 处理异步操作和事件流
  - 支持线程切换和错误处理
  - 与Retrofit配合实现网络请求

### 权限管理
- **RxPermission**：基于RxJava的权限请求库
  - 简化Android运行时权限请求流程
  - 支持链式调用和权限组合

### UI框架
- **Jetpack Compose**：现代声明式UI框架
- **DataBinding**：数据绑定框架，简化UI与数据的交互
- **Material Design**：遵循Material Design设计规范

## 项目架构

```
app/
├── base/                    # 基础类
│   ├── BaseActivityKt.kt    # Activity基类
│   ├── BaseViewModel.kt     # ViewModel基类
│   └── MyApplication.kt     # Application类
├── database/                # 数据库模块
│   ├── dao/                 # 数据访问对象
│   ├── entity/              # 数据实体
│   ├── AppDatabase.kt       # 数据库实例
│   └── DeviceRepository.kt  # 数据仓库
├── page/                    # 页面模块
│   └── main/                # 主页面
├── receiver/                # 广播接收器
│   ├── BootCompletedReceiver.kt         # 开机自启
│   ├── IndustrialCalibrateReceiver.kt    # 工业时间校准
│   └── NetworkStateReceiver.kt          # 网络状态监听
├── service/                 # 服务模块
│   ├── ForegroundService.kt # 前台服务
│   └── GuardService.kt      # 守护服务
├── util/                    # 工具类
│   ├── ConfParams.kt        # 配置参数
│   ├── IndustrialTimeUtils.kt # 工业时间工具
│   └── DeviceMonitorWorker.kt # 设备监控Worker
└── widget/                  # 自定义组件
```

## 关键特性

### 1. 数据采集流程
```
定时触发 → 采集设备数据 → 判断网络状态
    ↓
有网络 → 直接上传服务器
    ↓
无网络 → 存储到Room数据库 → 等待网络恢复 → 上传数据
```

### 2. 保活机制
```
前台服务（主进程）
    ↓ 相互监控
守护服务（独立进程）
    ↓
开机自启 + 电池优化白名单 + 心跳检测
```

### 3. 前后台状态管理
- **进入后台**：5分钟后自动停止采集任务，节省资源
- **回到前台**：自动恢复采集任务（如果之前是运行状态）
- **进程重启**：WorkManager自动恢复定时任务

## 配置说明

### 1. 数据库配置
- 数据库名称：`industry_database`
- 数据库版本：`1`
- 导出Schema：`false`
- 主要表：`device_data`（设备数据表）

### 2. WorkManager配置
- 任务名称：`CollectTask`
- 采集周期：`15分钟`
- 任务策略：`REPLACE`（替换已有任务）
- 约束条件：电池不低时执行

### 3. 前台服务配置
- 通知渠道ID：`KEEP_ALIVE_CHANNEL`
- 通知ID：`1001`
- 心跳间隔：`5秒`
- 前台服务类型：`dataSync`

### 4. 存储路径
- 应用基础路径：`/Android/data/com.tracy.industry/files/industry_conn/`
- 日志路径：`/industry_conn/log/`
- 数据库路径：`/industry_conn/database/`

## 权限说明

| 权限 | 用途 |
|------|------|
| `FOREGROUND_SERVICE` | 启动前台服务 |
| `POST_NOTIFICATIONS` | 显示通知（Android 13+） |
| `FOREGROUND_SERVICE_DATA_SYNC` | 数据同步前台服务 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 请求忽略电池优化 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |
| `WAKE_LOCK` | 唤醒锁 |
| `WRITE_EXTERNAL_STORAGE` | 写入外部存储 |
| `READ_EXTERNAL_STORAGE` | 读取外部存储 |
| `READ_PHONE_STATE` | 读取手机状态 |

## 使用说明

### 1. 启动应用
- 应用启动后自动初始化数据库、WorkManager等组件
- 自动启动前台服务和守护进程
- 开始执行定时采集任务

### 2. 权限申请
- 首次启动时申请必要权限
- 引导用户关闭电池优化
- 权限被拒绝时提供跳转设置页面的选项

### 3. 数据采集
- 每15分钟自动采集一次设备数据
- 采集数据包括设备ID、时间戳、状态等信息
- 数据优先上传，无网络时存储到本地

### 4. 数据查看
- 主页面显示采集状态和数据统计
- 支持导出数据库到本地文件
- 支持查看日志信息

## 注意事项

1. **电池优化**：为确保服务稳定运行，建议用户将应用添加到电池优化白名单
2. **后台限制**：Android 8.0+对后台服务有严格限制，前台服务是必要的保活手段
3. **存储权限**：Android 10+使用分区存储，数据存储在应用私有目录
4. **网络续传**：网络恢复后会自动上传本地数据，建议保持网络连接稳定
5. **WorkManager限制**：周期性任务最小间隔为15分钟，无法设置更短时间

## 版本信息

- **版本号**：1.0
- **编译SDK**：35
- **最低SDK**：24（Android 7.0）
- **目标SDK**：35（Android 15）

## 开发环境

- **IDE**：Android Studio
- **Gradle**：8.10.2
- **Kotlin**：2.0.0
- **JDK**：11

## 许可证

本项目为工业内部使用项目，未经授权不得用于商业用途。

## 联系方式

如有问题或建议，请联系开发团队。
