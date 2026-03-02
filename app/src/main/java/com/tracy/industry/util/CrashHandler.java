package com.tracy.industry.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.tracy.industry.page.main.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Desc:
 *
 * @author：LiuBao
 * @date: 2025/3/1 14:30
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    //系统默认的UncaughtException处理类
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    //CrashHandler实例
    private static CrashHandler INSTANCE = new CrashHandler();
    //程序的Context对象
    private Context mContext;
    // 崩溃日志保存路径
    private String crashLogPath;

    /**
     * 保证只有一个CrashHandler实例
     */
    private CrashHandler() {
    }

    /**
     * 获取CrashHandler实例 ,单例模式
     */
    public static CrashHandler getInstance() {
        return INSTANCE;
    }

    /**
     * A.初始化
     *
     * @param context
     */
    public void init(Context context) {
        mContext = context;
        //获取系统默认的UncaughtException处理器
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        //设置该CrashHandler为程序的默认处理器
        Thread.setDefaultUncaughtExceptionHandler(this);
        crashLogPath = ConfParams.Companion.getDIR_CRASH();
    }

    /**
     * B.当UncaughtException发生时会转入该函数来处理
     */
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            if (!handleCrash(ex)) {
                mDefaultHandler.uncaughtException(thread, ex);
            }
            else {
                // 处理崩溃：重启App（也可以跳转到错误页面/退出App）
                restartApp();
            }
        } catch (Exception e) {
            if (mDefaultHandler != null) {
                mDefaultHandler.uncaughtException(thread, ex);
            }
        }
    }

    // 工业App崩溃处理核心逻辑
    private Boolean handleCrash(Throwable e) {
        try {
            // ========== 步骤1：保存崩溃前的工业数据（核心中的核心） ==========
//            saveCrashData();
            // ========== 步骤2：记录工业级崩溃日志（运维排查必备） ==========
            saveCrashLogToFile(e);
            // ========== 步骤3：给工业现场用户友好提示（不是普通的“出错了”） ==========
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * 保存崩溃日志到本地文件
     *
     * @param ex 异常信息
     */
    private void saveCrashLogToFile(Throwable ex) throws Exception {
        // 收集设备信息和异常信息
        StringBuilder sb = new StringBuilder();
        sb.append("========== 崩溃信息 ==========\n");
        sb.append("崩溃时间: ").append(getCurrentTime()).append("\n");
        sb.append("设备型号: ").append(Build.MODEL).append("\n");
        sb.append("Android版本: ").append(Build.VERSION.RELEASE).append("\n");
        sb.append("应用版本: ").append("1.0.0").append("\n");
        sb.append("异常信息: \n");

        // 获取异常堆栈信息
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable cause = ex.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();
        sb.append(writer.toString());

        // 保存到文件
        String logFileName = "crash_" + getCurrentTime() + ".txt";
        File logFile = new File(crashLogPath, logFileName);
        // 创建目录（如果不存在）
        if (!logFile.getParentFile().exists()) {
            logFile.getParentFile().mkdirs();
        }
        // 写入文件
        FileOutputStream fos = new FileOutputStream(logFile);
        fos.write(sb.toString().getBytes());
        fos.close();

        DebugLog.e("崩溃日志已保存: " + logFile.getAbsolutePath());
    }

    /**
     * 重启App
     */
    private void restartApp() {
        DebugLog.e("CrashHandler restartApp");
        // 获取当前App的启动Activity（需替换为你的主Activity）
        Intent intent = new Intent(mContext, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        // 杀死当前进程（必须执行，否则旧进程会残留）
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }

    /**
     * 获取当前时间（用于日志命名）
     */
    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        return sdf.format(new Date());
    }
}
