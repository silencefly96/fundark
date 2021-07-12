@file:Suppress("unused")

package com.silencefly96.module_base.utils

import java.lang.StringBuilder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日期相关工具类
 * @author: fdk
 * @data: 2021/7/9
 */
@Suppress("MemberVisibilityCanBePrivate")
object DateUtil {

    /**
     * 获得指定格式日期
     * @param mode 需要获取的日期模式字符串
     * @return String 需要的特定格式日期
     */
    fun getDateByMode(mode: String): String {
        val simpleDateFormat = SimpleDateFormat(mode, Locale.CHINA)
        simpleDateFormat.timeZone = TimeZone.getTimeZone("GMT+8")
        val date = Date(System.currentTimeMillis())
        return simpleDateFormat.format(date)
    }

    /**
     * 转换日期格式
     * @param src 原日期字符串
     * @param srcMode 原日期字符串格式
     * @param desMode 目标日期字符串格式
     * @return String 目标日期字符串
     */
    fun transformByMode(src: String, srcMode: String, desMode: String): String {
        var des = "null"
        val oldFmt = SimpleDateFormat(srcMode, Locale.CHINA)
        val newFmt = SimpleDateFormat(desMode, Locale.CHINA)
        try {
            oldFmt.parse(src)?.let { date ->
                des = newFmt.format(date)
            }
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        return des
    }

    /**
     * 获取指定日期的年份
     * @param time 指定日期字符串
     * @param mode 指定日期字符串的格式
     * @return String 指定日期的年份
     */
    fun yearOf(time: String, mode: String): String {
        return transformByMode(time, mode, "yyyy")
    }

    /**
     * 获取指定日期的月份
     * @param time 指定日期字符串
     * @param mode 指定日期字符串的格式
     * @return String 指定日期的月份
     */
    fun mouthOf(time: String, mode: String): String {
        return transformByMode(time, mode, "MM")
    }

    /**
     * 获取指定日期是所在月中的第几周
     * @param time 指定日期字符串
     * @param mode 指定日期字符串的格式
     * @return String 指定日期是所在月中的第几周
     */
    fun weekOf(time: String, mode: String): String {
        return transformByMode(time, mode, "W")
    }

    /**
     * 获取指定日期的日期
     * @param time 指定日期字符串
     * @param mode 指定日期字符串的格式
     * @return String 指定日期的日期
     */
    fun dayOf(time: String, mode: String): String {
        return transformByMode(time, mode, "dd")
    }

    /**
     * 字符串时间转date型
     * @param time 指定日期字符串
     * @param mode 指定日期字符串的格式
     * @return Date? 指定日期的 Date
     */
    fun str2Date(time: String, mode: String): Date? {
        val format = SimpleDateFormat(mode, Locale.CHINA)
        var date: Date? = null
        try {
            date = format.parse(time)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        return date
    }

    /**
     * 时间推算操作，例如将日期往前推一天
     * @param time 指定日期字符串
     * @param mode 指定日期字符串的格式
     * @param type 操作类型，例如 Calendar.DATE
     * @param value 操作的值
     * @return Date? 指定日期的 Date
     */
    fun operateTime(time: String, mode: String, type: Int, value: Int): String {
        var result = ""

        val date = str2Date(time, mode)
        date?.let {
            val calendar = Calendar.getInstance()
            calendar.time = it
            calendar.add(type, value)

            val simpleDateFormat = SimpleDateFormat(mode, Locale.CHINA)
            result = simpleDateFormat.format(date)
        }

        return result
    }

    /**
     * 日期往前推一天
     * @param time 指定日期字符串
     * @param mode 指定日期字符串的格式
     * @return String 日期往前推一天的原格式字符串
     */
    fun yesterday(time: String, mode: String): String {
        return operateTime(time, mode, Calendar.DATE, -1)
    }

    /**
     * 日期往后推一天
     * @param time 指定日期字符串
     * @param mode 指定日期字符串的格式
     * @return String 日期往后推一天的原格式字符串
     */
    fun tomorrow(time: String, mode: String): String {
        return operateTime(time, mode, Calendar.DATE, 1)
    }

    /**
     * 将秒转成时分秒形式，适用于转换媒体文件时长
     * @param seconds 秒的值
     * @return String 化为时分秒形式的值。例如 1:30:15
     */
    fun formatSeconds(seconds: Long): String {
        var temp: Int
        val sb = StringBuilder()

        //时，如果有才计算，小于10补零
        if (seconds > 3600) {
            temp = (seconds / 3600).toInt()
            sb.append(if (seconds / 3600 < 10) "0$temp:" else "$temp:")
        }

        //分，小于10补零
        temp = (seconds % 3600 / 60).toInt()
        sb.append(if (temp < 10) "0$temp:" else "$temp:")

        //秒，小于10补零
        temp = (seconds % 3600 % 60).toInt()
        sb.append(if (temp < 10) "0$temp" else "" + temp)

        return sb.toString()
    }

}