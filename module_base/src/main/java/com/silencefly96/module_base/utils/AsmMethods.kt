package com.silencefly96.module_base.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import android.webkit.WebSettings
import com.silencefly96.module_base.BuildConfig
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry

@Suppress("unused")
object AsmMethods {
    // 优化使用，方法池，减少反射调用次数
    private val sMethodMap: MutableMap<String, Method?> = ConcurrentHashMap(16)

    // ASM替换代码勿动: 替换获取外部文件
    @JvmStatic
    fun getExternalDir(): File {
        var result = File("")
        // 通过反射执行
        try {
            // PhoneUtils.getExternalDir()
            val methodStr =
                StringBuilder().append("getExternal").append("StorageDirectory").toString()
            var method = sMethodMap["getExternalDir"]
            if (null == method) {
                method = Environment::class.java.getMethod(methodStr)
                sMethodMap["getExternalDir"] = method
            }
            result = method!!.invoke(null) as File
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    // ASM替换代码勿动: 替换直接动态注册广播
    @JvmStatic
    fun registerMyReceiver(
        context: Context,
        receiver: BroadcastReceiver?,
        filter: IntentFilter?
    ): Intent? {
        var result: Intent? = null
        // 通过反射执行
        try {
            val methodStr = StringBuilder().append("register").append("Receiver").toString()
            var method = sMethodMap["registerMyReceiver"]
            if (null == method) {
                method = context.javaClass.getMethod(
                    methodStr,
                    BroadcastReceiver::class.java,
                    IntentFilter::class.java
                )
                sMethodMap["registerMyReceiver"] = method
            }
            result = method!!.invoke(context, receiver, filter) as Intent
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    // ASM替换代码勿动: 处理SQL数据库注入漏洞: rawQuery
    @JvmStatic
    fun rawMyQuery(
        database: SQLiteDatabase,
        sql: String?,
        selectionArgs: Array<String?>?
    ): Cursor? {
        var result: Cursor? = null
        // 通过反射执行
        try {
            val methodStr = StringBuilder().append("raw").append("Query").toString()
            var method = sMethodMap["rawMyQuery"]
            if (null == method) {
                method = database.javaClass.getMethod(
                    methodStr,
                    String::class.java,
                    Array<String>::class.java
                )
                sMethodMap["rawMyQuery"] = method
            }
            result = method!!.invoke(database, sql, selectionArgs) as Cursor
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    // ASM替换代码勿动: 处理SQL数据库注入漏洞: rawQuery
    @JvmStatic
    fun execMySQL(database: SQLiteDatabase, sql: String?) {
        // 通过反射执行
        try {
            val methodStr = StringBuilder().append("exec").append("SQL").toString()
            var method = sMethodMap["execMySQL"]
            if (null == method) {
                method = database.javaClass.getMethod(methodStr, String::class.java)
                sMethodMap["execMySQL"] = method
            }
            method!!.invoke(database, sql)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ASM替换代码勿动: ZipperDown漏洞
    @JvmStatic
    fun getZipEntryName(entry: ZipEntry): String {
        var result = ""
        // 通过反射执行
        try {
            val methodStr = StringBuilder().append("get").append("Name").toString()
            var method = sMethodMap["getZipEntryName"]
            if (null == method) {
                method = entry.javaClass.getMethod(methodStr)
                sMethodMap["getZipEntryName"] = method
            }
            result = method!!.invoke(entry) as String
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 整改部分: 防止解压目录跳过文件
        if (result.contains("…/")) {
            result = ""
        }
        return result
    }

    // ASM替换代码勿动: 日志函数泄露风险
    @JvmStatic
    fun optimizeLog(tag: String?, msg: String?): Int {
        var result = 0
        if (BuildConfig.DEBUG) {
            // 要防止这里被替代，引发StackOverflow问题
            result = Log.d(tag, msg!!)
        }
        return result
    }

    // ASM替换代码勿动: 日志函数泄露风险
    @JvmStatic
    fun optimizeLogE(tag: String?, msg: String?): Int {
        var result = 0
        if (BuildConfig.DEBUG) {
            // 要防止这里被替代，引发StackOverflow问题
            result = Log.e(tag, msg!!)
        }
        return result
    }

    // ASM替换代码勿动: WebView组件跨域访问风险
    @JvmStatic
    fun setMyJsEnabled(settings: WebSettings, flag: Boolean) {
        // 通过反射执行
        try {
            val methodStr =
                StringBuilder().append("set").append("JavaScript").append("Enabled").toString()
            var method = sMethodMap["setMyJsEnabled"]
            if (null == method) {
                method = settings.javaClass.getMethod(methodStr, Boolean::class.javaPrimitiveType)
                sMethodMap["setMyJsEnabled"] = method
            }
            method!!.invoke(settings, flag)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}