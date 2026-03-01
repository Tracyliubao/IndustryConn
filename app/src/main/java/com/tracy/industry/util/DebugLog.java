package com.tracy.industry.util;

import android.util.Log;

/**
 * 日志打印工具
 * 
 * @author zhouby
 *
 */
public class DebugLog {

    public static boolean isDebugScroll= false;
    public static boolean isDebugDownload= true;
	/**控制是否打印日志**/
	public static boolean isDebug = true;
	/**类名**/
	private static String className;
	/**方法名**/
	private static String methodName;
	
    private DebugLog(){
        /* Protect from instantiations */
    }

    private static void createLogE(String log){

//        if (log.length() > 4000) {
//            for (int i = 0; i < log.length(); i += 4000) {
//                //当前截取的长度<总长度则继续截取最大的长度来打印
//                if (i + 4000 < log.length()) {
//                    Log.e(className + "[" + methodName + "_line" + i + "]", log.substring(i, i + 4000));
//                } else {
//                    //当前截取的长度已经超过了总长度，则打印出剩下的全部信息
//                    Log.e(className + "[" + methodName + "_line" + i + "]", log.substring(i, log.length()));
//                }
//            }
//        } else {
            //直接打印
            Log.e(className + "[" + methodName + "]", log);
//        }
    }

    private static String createLog(String log){
 
        StringBuffer buffer = new StringBuffer();
        buffer.append("[");
        buffer.append(methodName);
        buffer.append("]");
        buffer.append(log);

        return buffer.toString();
    }
 
    private static void getMethodNames(StackTraceElement[] sElements){
        className = sElements[1].getFileName();
        methodName = sElements[1].getMethodName();
    }

    public static void e(){
        if (!isDebug)
            return;
        getMethodNames(new Throwable().getStackTrace());
        Log.e(className, createLog(""));
    }

    public static void scroll(String message){
        if (!isDebug || !isDebugScroll)
            return;

        // Throwable instance must be created before any methods
        getMethodNames(new Throwable().getStackTrace());
        Log.e(className, createLog(message));
    }

    public static void download(String message){
        if (!isDebugDownload)
            return;
        e(message);
    }

    public static void e(String message){
        if (!isDebug)
            return;

        // Throwable instance must be created before any methods  
        getMethodNames(new Throwable().getStackTrace());
        createLogE(message);
    }

    public static void i(String message){
        if (!isDebug)
            return;
 
        getMethodNames(new Throwable().getStackTrace());
        Log.i(className, createLog(message));
    }
 
    public static void d(String message){
        if (!isDebug)
            return;
 
        getMethodNames(new Throwable().getStackTrace());
        Log.d(className, createLog(message));
    }
 
    public static void v(String message){
        if (!isDebug)
            return;
 
        getMethodNames(new Throwable().getStackTrace());
        Log.v(className, createLog(message));
    }
 
    public static void w(String message){
        if (!isDebug)
            return;
 
        getMethodNames(new Throwable().getStackTrace());
        Log.w(className, createLog(message));
    }
 
    public static void wtf(String message){
        if (!isDebug)
            return;
 
        getMethodNames(new Throwable().getStackTrace());
        Log.wtf(className, createLog(message));
    }    
}
