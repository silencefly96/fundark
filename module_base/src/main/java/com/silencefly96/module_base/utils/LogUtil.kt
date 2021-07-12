package com.silencefly96.module_base.utils

import android.os.Handler
import android.os.HandlerThread
import android.text.TextUtils
import android.util.Log
import java.io.File
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日志工具类
 * 如果需要保存日志到文件，需要传入一个 handlerThread
 * 使用了反射，请勿混淆
 * 如果不需要使用保存到文件，建议直接使用 Log,并通过混淆规则在正式版中去除日志
 *
 * @author fdk
 * @date 2021/07/12
 */
@Suppress("unused")
object LogUtil {

    /** Log输出的控制开关 */
    private var isShowLog = true

    /** Log输出的等级, 0-v-d-i-w-e-6 */
    private var outLevel = 0

    /** Log输出到本地文件控制开关 */
    private var isSaveLog = true

    /** 自定义Log开头形式 */
    private var startHead = "LogUtil - - - -"

    /** Log输出文件目录 */
    private var logDir =
//        Environment.getExternalStorageDirectory().path +
                "/logs/"

    /** Log输出文件名  */
    private val logName = Date(System.currentTimeMillis()).let {
        val simpleDateFormat = SimpleDateFormat("log_yyyyMMdd.log", Locale.CHINA)
        simpleDateFormat.timeZone = TimeZone.getTimeZone("GMT+8")
        simpleDateFormat.format(it)
    }

    /** 日志缓存预定义最大缓存数量 */
    private const val LOG_BUFFER_SIZE = 1000

    /** 处理消息的 Handler */
    private var mHandler: Handler? = null

    /** 要输出日志的话，需要从外部传入一个异步的 HandlerThread，注意在外部销毁  */
    private var mHandlerThread: HandlerThread? = null
        set(value) {
            field = value
            field?.apply {
                // 启动线程
                start()

                // 日志缓存，写在这，避免在除 handler 之外调用
                val mLogBuffer = ArrayList<String>()
                //val mLogBuffer = CopyOnWriteArrayList<String>()

                // 获得线程的 handler 来处理 log 消息
                mHandler = Handler(looper) {
                    mLogBuffer.add(it.obj as String)
                    if (mLogBuffer.size > LOG_BUFFER_SIZE) {
                        val sb = StringBuilder()
                        for (log in mLogBuffer) {
                            sb.append(log)
                        }

                        //使用kotlin函数以追加的形式写入文件
                        File(logDir, logName).appendText(sb.toString())
                        //清空数据
                        mLogBuffer.clear()
                    }
                    true
                }
            }
        }

    /**
     * 详细输出调试
     * @param objTag
     * @param msg
     */
    fun v(objTag: Any, msg: String?) {
        log(objTag, msg, "v")
    }

    /**
     * debug输出调试
     * @param objTag
     * @param msg
     */
    fun d(objTag: Any, msg: String?) {
        log(objTag, msg, "d")
    }

    fun i(objTag: Any, msg: String?) {
        log(objTag, msg, "i")
    }

    /**
     * 警告的调试信息
     * @param objTag
     * @param msg
     */
    fun w(objTag: Any, msg: String?) {
        log(objTag, msg, "w")
    }

    /**
     * 错误调试信息
     * @param objTag
     * @param msg
     */
    fun e(objTag: Any, msg: String?) {
        log(objTag, msg, "e")
    }

    /**
     * 日志处理方法
     * @param objTag
     * @param msg
     * @param type 输出类型：v d i w e
     */
    private fun log(objTag: Any, msg: String?, type: String) {
        if (!isShowLog) { return }
        var tag: String

        // 如果objTag是String，则直接使用
        // 如果objTag不是String，则使用它的类名
        // 如果在匿名内部类，写this的话是识别不了该类，所以获取当前对象全类名来分隔
        when (objTag) {
            is String -> {
                tag = objTag
            }
            is Class<*> -> {
                tag = objTag.simpleName
            }
            else -> {
                tag = objTag.javaClass.name
                val split = tag.split("\\.").toTypedArray()
                tag = split[split.size - 1].split("\\$").toTypedArray()[0]
            }
        }

        //通过反射去执行对应 LOG 方法
        func[type]?.apply {
            try {
                if (TextUtils.isEmpty(msg)) {
                    //静态方法调用
                    invoke(null, startHead + tag, "EMPTY")
                } else if (outLevel <= 1) {
                    invoke(null, startHead + tag, msg!!)

                    if (isSaveLog) {
                        //通过 handler 发送消息到异步线程处理
                        mHandler?.apply {
                            sendMessage(obtainMessage(0, "$startHead $tag $type: $msg"))
                        }
                    }
                }
            }catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 储存 Log 类的五个方法，便于统一调用 */
    private val func = HashMap<String,Method>().apply {
        val clazz = Log::class.java
        put("v",clazz.getDeclaredMethod("v"))
        put("d",clazz.getDeclaredMethod("d"))
        put("i",clazz.getDeclaredMethod("i"))
        put("w",clazz.getDeclaredMethod("w"))
        put("e",clazz.getDeclaredMethod("e"))
    }
}