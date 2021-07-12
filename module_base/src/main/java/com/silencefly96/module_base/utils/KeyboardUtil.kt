package com.silencefly96.module_base.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.InputMethodManager

/**
 * 键盘工具类
 * @author fdk
 * @date 2021/07/12
 */
object KeyboardUtil {

    /**
     * 隐藏键盘
     * @param view 视图
     */
    fun hideKeyboard(view: View?) {
        if (view != null) {
            val imm =
                view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    /**
     * 显示键盘
     * @param view 视图
     */
    fun showKeyboard(view: View?) {
        if (view != null) {
            val imm =
                view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * 复制字符串到粘贴板
     * @param string 复制的字符串
     * @param context 上下文对象
     * @param copyEmpty 是否复制空字符串
     */
    @JvmOverloads
    fun copyString(string: String, context: Context, copyEmpty: Boolean = false): Boolean {
        if (!TextUtils.isEmpty(string) || copyEmpty) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val mClipData = ClipData.newPlainText("Label", string)
            cm.setPrimaryClip(mClipData)
            return true
        }
        return false
    }
}