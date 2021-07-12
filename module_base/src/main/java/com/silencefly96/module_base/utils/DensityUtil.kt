package com.silencefly96.module_base.utils

import android.content.Context
import android.util.TypedValue

/**
 * 常用单位转换的工具类
 * @author fdk
 * @date 2021/07/09
 */
@Suppress("unused")
object DensityUtil {

    /**
     * dp2px
     * @param context 上下文对象
     * @param dpVal dp值
     * @return Int px值
     */
    fun dp2px(context: Context, dpVal: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dpVal, context.resources
                .displayMetrics
        ).toInt()
    }

    /**
     * sp2px
     * @param context 上下文对象
     * @param spVal sp值
     * @return Int px值
     */
    fun sp2px(context: Context, spVal: Float): Int {
//        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, spVal, context.getResources()
//                .getDisplayMetrics());
        val fontScale = context.resources.displayMetrics.scaledDensity
        return (spVal * fontScale + 0.5f).toInt()
    }

    /**
     * px2dp
     * @param context 上下文对象
     * @param pxVal px值
     * @return Float dp值
     */
    fun px2dp(context: Context, pxVal: Float): Float {
        val scale = context.resources.displayMetrics.density
        return pxVal / scale
    }

    /**
     * px2sp
     * @param context 上下文对象
     * @param pxVal px值
     * @return Float sp值
     */
    fun px2sp(context: Context, pxVal: Float): Float {
        return pxVal / context.resources.displayMetrics.scaledDensity
    }

    /**
     * 获取屏幕宽度像素值
     * @param context 上下文对象
     * @return Int 屏幕宽度像素值
     */
    fun getDisplayWidth(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    /**
     * 获取屏幕高度像素值
     * @param context 上下文对象
     * @return Int 屏幕高度像素值
     */
    fun getDisplayHeight(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }
}