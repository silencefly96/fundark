package com.silencefly96.module_base.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import java.util.*

/**
 * SharedPreferences 工具类
 * 使用前先设置 Context，或者在每次使用的时候传入 context
 * 支持 String Int Float Boolean Long StringSet StringCollection StringLIst
 * 若要保存其他对象，可以利用 Json 工具将对象转成 String 保存
 *
 * @author fdk
 * @date 2021/07/12
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@SuppressLint("StaticFieldLeak")
object SPUtil {

    /** SharedPreferences 存放路径 */
    private const val spPath = "configure"

    /** 全局的上下文对象 */
    private var mContext: Context? = null

    /**
     * 初始化上下文对象
     * @param application 应用 context，避免内存泄漏
     */
    fun setup(application: Context) {
        mContext = application
    }

    /**
     * 获得 SharedPreferences
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getPreferences(context: Context?): SharedPreferences? =
        context?.run {
            getSharedPreferences(spPath, Context.MODE_PRIVATE)
        }

    /**
     * 获得 edit，继续提交再 apply/commit
     * @param context 上下文对象
     */
    fun getEdit(context: Context?) =
        getPreferences(context)?.run {
            edit()
        }

    /**
     * 通过键获取字符串
     * @param key 键
     * @param defaultValue 默认值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getString(key: String, defaultValue: String?, context: Context?) =
        getPreferences(context)?.getString(key, defaultValue)

    /**
     * 通过键获取字符串
     * @param key 键
     */
    fun getString(key: String, context: Context?) =
        getString(key, null, context)

    /**
     * 保存字符串，暂时不提交，并获得 edit，继续提交再 apply/commit
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putStringThan(key: String, value: String?, context: Context? = mContext) =
        getEdit(context)?.apply {
            putString(key, value)
        }

    /**
     * 保存字符串
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putString(key: String, value: String?, context: Context? = mContext) =
        putStringThan(key, value, context)?.apply()

    /**
     * 通过键获取int
     * @param key 键
     * @param defaultValue 默认值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getInt(key: String, defaultValue: Int, context: Context? = mContext) =
        getPreferences(context)?.getInt(key, defaultValue)

    /**
     * 通过键获取int
     * @param key 键
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getInt(key: String, context: Context? = mContext) = getInt(key, 0, context)

    /**
     * 保存int，暂时不提交，并获得 edit，继续提交再 apply/commit
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putIntThan(key: String, value: Int, context: Context? = mContext) =
        getEdit(context)?.apply {
            putInt(key, value)
        }

    /**
     * 保存int
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putInt(key: String, value: Int, context: Context? = mContext) =
        putIntThan(key, value, context)?.apply()

    /**
     * 通过键获取float
     * @param key 键
     * @param defaultValue 默认值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getFloat(key: String, defaultValue: Float, context: Context? = mContext) =
        getPreferences(context)?.getFloat(key, defaultValue)

    /**
     * 通过键获取Float
     * @param key 键
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getFloat(key: String, context: Context? = mContext) = getFloat(key, 0f, context)

    /**
     * 保存Float，暂时不提交，并获得 edit，继续提交再 apply/commit
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putFloatThan(key: String, value: Float, context: Context? = mContext) =
        getEdit(context)?.apply {
            putFloat(key, value)
        }

    /**
     * 保存Float
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putFloat(key: String, value: Float, context: Context? = mContext) =
        putFloatThan(key, value, context)?.apply()

    /**
     * 通过键获取Boolean
     * @param key 键
     * @param defaultValue 默认值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getBoolean(key: String, defaultValue: Boolean, context: Context? = mContext) =
        getPreferences(context)?.getBoolean(key, defaultValue)

    /**
     * 通过键获取Boolean
     * @param key 键
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getBoolean(key: String, context: Context? = mContext) =
        getBoolean(key, false, context)

    /**
     * 保存Boolean，暂时不提交，并获得 edit，继续提交再 apply/commit
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putBooleanThan(key: String, value: Boolean, context: Context? = mContext) =
        getEdit(context)?.apply {
            putBoolean(key, value)
        }

    /**
     * 保存Boolean
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putBoolean(key: String, value: Boolean, context: Context? = mContext) =
        putBooleanThan(key, value, context)?.apply()


    /**
     * 通过键获取Long
     * @param key 键
     * @param defaultValue 默认值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getLong(key: String, defaultValue: Long, context: Context? = mContext) =
        getPreferences(context)?.getLong(key, defaultValue)

    /**
     * 通过键获取Long
     * @param key 键
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getLong(key: String, context: Context? = mContext) = getLong(key, 0L, context)

    /**
     * 保存Long，暂时不提交，并获得 edit，继续提交再 apply/commit
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putLongThan(key: String, value: Long, context: Context? = mContext) =
        getEdit(context)?.apply {
            putLong(key, value)
        }

    /**
     * 保存Long
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putLong(key: String, value: Long, context: Context? = mContext) =
        putLongThan(key, value, context)?.apply()


    /**
     * 通过键获取StringSet
     * @param key 键
     * @param defaultValue 默认值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getStringSet(key: String, defaultValue: Set<String>?,
                     context: Context? = mContext): Set<String>? =
        getPreferences(context)?.getStringSet(key, defaultValue)

    /**
     * 通过键获取StringSet
     * @param key 键
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getStringSet(key: String, context: Context? = mContext) =
        getStringSet(key, null, context)

    /**
     * 保存StringSet，暂时不提交，并获得 edit，继续提交再 apply/commit
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putStringSetThan(key: String, value: Set<String>?, context: Context? = mContext) =
        getEdit(context)?.apply {
            putStringSet(key, value)
        }

    /**
     * 保存StringSet
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putStringSet(key: String, value: Set<String>?, context: Context? = mContext) =
        putStringSetThan(key, value, context)?.apply()

    /**
     * 通过键获取StringCollection，实际以TreeSet形式保存(保证了有序)
     * @param key 键
     * @param defaultValue 默认值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getStringCollection(key: String, defaultValue: Collection<String>?,
                      context: Context? = mContext): Collection<String>?  =
        getStringSet(key, if (defaultValue == null) null else TreeSet(defaultValue), context)

    /**
     * 通过键获取StringCollection，实际以set形式保存
     * @param key 键
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getStringCollection(key: String, context: Context? = mContext): Collection<String>? =
        getStringCollection(key, null, context)

    /**
     * 保存StringCollection，暂时不提交，并获得 edit，继续提交再 apply/commit，实际以set形式保存
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putStringCollectionThan(key: String, value: Collection<String>?, context: Context? = mContext) =
        putStringSetThan(key, if (value == null) null else TreeSet(value), context)

    /**
     * 保存StringCollection，实际以set形式保存
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putStringCollection(key: String, value: Collection<String>?, context: Context? = mContext) =
        putStringCollectionThan(key, value, context)?.apply()

    /**
     * 通过键获取StringList，调用Collection方法即可，实际以set形式保存
     * @param key 键
     * @param defaultValue 默认值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getStringList(key: String, defaultValue: List<String>?, context: Context? = mContext) =
        getStringCollection(key, defaultValue, context)

    /**
     * 通过键获取StringList，实际以set形式保存
     * @param key 键
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun getStringList(key: String, context: Context? = mContext) =
        getStringList(key, null, context)

    /**
     * 保存StringList，暂时不提交，并获得 edit，调用Collection方法即可，实际以set形式保存
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putStringListThan(key: String, value: List<String>?, context: Context? = mContext) =
        putStringCollectionThan(key, value, context)

    /**
     * 保存StringList，实际以set形式保存
     * @param key 键
     * @param value 值
     * @param context 上下文对象，默认为初始化的上下文对象
     */
    fun putStringList(key: String, value: List<String>?, context: Context? = mContext) =
        putStringListThan(key, value, context)?.apply()
}