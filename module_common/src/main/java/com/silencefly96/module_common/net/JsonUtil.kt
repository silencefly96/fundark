package com.silencefly96.module_common.net

import com.google.gson.Gson

/**
 * json 工具类，用于对象和字符串的互相转换
 * @author fdk
 * @date 2021/07/13
 */
object JsonUtil {

    /**
     * json 对象
     */
    private val gson = Gson()

    /**
     * 将对象转为字符串
     * @param srcObj 任意对象
     */
    fun toJson(srcObj: Any?): String {
        return if (srcObj == null) "" else gson.toJson(srcObj)
    }

    /**
     * 将字符串转回对象
     * @param json json字符串
     * @param clazz 目标类
     */
    fun <T> getObject(json: String, clazz: Class<T>): T {
        return gson.fromJson(json, clazz)
    }
}