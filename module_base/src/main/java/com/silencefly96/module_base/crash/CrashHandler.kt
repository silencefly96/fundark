package com.silencefly96.module_base.crash

import android.content.Context
import android.os.Build
import java.io.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

@Suppress("unused", "MemberVisibilityCanBePrivate")
class CrashHandler : Thread.UncaughtExceptionHandler {

    //异常日志路径
    private var mLogPath: File? = null

    //设备信息
    private var mDeviceInfo: String? = null

    //默认的主线程异常处理器
    private var mDefaultHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * 处理异常
     * @param thread 线程
     * @param ex 异常
     */
    override fun uncaughtException(thread: Thread, ex: Throwable) {
        //自行处理异常
        handleException(ex)
        //自行处理完再交给默认处理器
        if (null != mDefaultHandler) {
            mDefaultHandler!!.uncaughtException(thread, ex)
        }
    }

    /**
     * 自行处理异常
     * @param throwable 异常
     */
    private fun handleException(throwable: Throwable) {
        val sb = StringBuilder()
        //获取异常信息
        getExceptionInfo(sb, throwable)
        //附加设备信息
        sb.append(mDeviceInfo)
        //保存到文件
        Thread { saveCrash2File(sb.toString()) }.start()
    }

    /**
     * 获取异常信息
     * @param sb StringBuilder 用于追加异常信息
     * @param throwable 异常
     */
    private fun getExceptionInfo(sb: StringBuilder, throwable: Throwable) {
        val writer: Writer = StringWriter()
        val printWriter = PrintWriter(writer)
        throwable.printStackTrace(printWriter)
        //循环获取包装异常的异常原因
        var cause = throwable.cause
        while (cause != null) {
            cause.printStackTrace(printWriter)
            cause = cause.cause
        }
        printWriter.close()
        val exStr = writer.toString()
        sb.append(exStr).append("\n")
    }

    /**
     * 保存到文件
     * @param crashInfo 异常信息
     */
    private fun saveCrash2File(crashInfo: String) {
        try {
            // 用于格式化日期,作为日志文件名的一部分
            @Suppress("SpellCheckingInspection")
            val formatter: DateFormat = SimpleDateFormat("yyMMddHHmmss", Locale.CHINA)
            val time = formatter.format(Date())
            val fileName = "crash_$time.log"
            val filePath = mLogPath!!.absolutePath + File.separator + fileName
            val fos = FileOutputStream(filePath)
            fos.write(crashInfo.toByteArray())
            fos.flush()
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        //单例
        @Volatile
        var instance: CrashHandler? = null
            get() {
                //DCL双重校验
                if (field == null) {
                    synchronized(CrashHandler::class.java) {
                        if (field == null) {
                            field = CrashHandler()
                        }
                    }
                }
                return field
            }

        //在application中初始化
        fun init(context: Context) {
            val crashHandler = instance
            crashHandler!!.mLogPath = getCrashCacheDir(context)
            crashHandler.mDeviceInfo = getDeviceInfo(context)
            crashHandler.mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            //设置主线程处理器为自定义处理器
            Thread.setDefaultUncaughtExceptionHandler(crashHandler)
        }

        /**
         * 获取部分设备信息
         * @param context 上下文对象
         * @return 设备信息
         */
        private fun getDeviceInfo(context: Context): String {
            val sb = StringBuilder()
            try {
                val pkgMgr = context.packageManager
                val pkgInfo = pkgMgr.getPackageInfo(context.packageName, 0)
                sb.append("packageName: ").append(pkgInfo.packageName).append("\n")
                //sb.append("versionCode: ").append(pkgInfo.versionCode).append("\n")
                sb.append("versionName: ").append(pkgInfo.versionName).append("\n")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            //厂商，例如Xiaomi，Meizu，Huawei等。
            sb.append("brand: ").append(Build.BRAND).append("\n")
            //产品型号全称，例如 meizu_mx3
            sb.append("product: ").append(Build.PRODUCT).append("\n")
            //产品型号名，例如 mx3
            sb.append("device: ").append(Build.DEVICE).append("\n")
            //安卓版本名，例如 4.4.4
            sb.append("androidVersionName: ").append(Build.VERSION.RELEASE).append("\n")
            //安卓API版本，例如 19
            sb.append("androidApiVersion: ").append(Build.VERSION.SDK_INT).append("\n")

            //通过反射记录Build所有数据
//        Field[] fields = Build.class.getDeclaredFields();
//        for (Field field : fields) {
//            try {
//                field.setAccessible(true);
//                info.put(field.getName(), field.get(null).toString());
//                Log.d(TAG, field.getName() + " : " + field.get(null));
//            } catch (Exception e) {
//                Log.e(TAG, "an error occurred when collect crash info", e);
//            }
//        }
            return sb.toString()
        }

        //获取日志缓存目录，不用储存权限
        private fun getCrashCacheDir(context: Context): File {
            val cachePath: String = if (context.externalCacheDir != null) {
                //外部储存路径
                context.externalCacheDir!!.path
            } else {
                context.filesDir.path
            }

            val dir = File(cachePath + File.separator + "log")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

        //获取所有日志文件
        val crashLogs: List<File?>
            get() {
                val crashDir = instance!!.mLogPath
                //避免直接使用asList方法，产生的视图无法修改
                return ArrayList<File?>().apply {
                    crashDir!!.listFiles()?.let{
                        addAll(it)
                    }
                }
            }

        /**
         * 读取崩溃日志文件内容并返回
         */
        val crashLogsStrings: List<String>
            get() {
                val logs: MutableList<String> = ArrayList()
                val files = crashLogs
                var inputStream: FileInputStream
                for (file in files) {
                    try {
                        inputStream = FileInputStream(file)
                        var len: Int
                        val temp = ByteArray(1024)
                        val sb = StringBuilder("")
                        while (inputStream.read(temp).also { len = it } > 0) {
                            sb.append(String(temp, 0, len))
                        }
                        inputStream.close()
                        logs.add(sb.toString())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return logs
            }

        /**
         * 删除日志文件
         */
        fun removeCrashLogs(files: List<File>) {
            for (file in files) {
                file.delete()
            }
        }
    }
}