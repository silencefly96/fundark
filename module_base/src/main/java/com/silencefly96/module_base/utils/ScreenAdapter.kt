@file:Suppress("MemberVisibilityCanBePrivate")

package com.silencefly96.module_base.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi

/**
 * 屏幕适配方案
 * 参考自：https://www.jianshu.com/p/1eeb0d8d1c86，稍作修改
 * 提供 dp、sp 以及 pt 作为适配单位，建议开发中以 dp、sp 适配为主，pt 可作为 dp、sp 的适配补充
 * 由今日头条适配方案修改而来: https://mp.weixin.qq.com/s/d9QCoBP6kV9VSWvVldVVwA
 * @author fdk
 * @date 2021/07/12
 */
@Suppress("unused")
class ScreenAdapter private constructor() {

    /**
     * 适配信息
     */
    data class MatchInfo(
        var screenWidth: Int = 0,
        var screenHeight: Int = 0,
        var appDensity: Float = 0f,
        var appDensityDpi: Float = 0f,
        var appScaledDensity: Float = 0f,
        var appXdpi: Float = 0f)

    companion object {

        /**
         * 屏幕适配的基准
         */
        const val MATCH_BASE_WIDTH = 0
        const val MATCH_BASE_HEIGHT = 1

        /**
         * 适配单位
         */
        const val MATCH_UNIT_DP = 0
        const val MATCH_UNIT_PT = 1

        /**
         * 原始适配信息
         */
        var matchInfo: MatchInfo? = null

        /**
         * Activity 的生命周期监测
         */
        private var mActivityLifecycleCallback: ActivityLifecycleCallbacks? = null

        /**
         * 初始化
         *
         * @param application
         */
        @SuppressLint("ObsoleteSdkInt")
        fun setup(application: Application) {
            val displayMetrics = application.resources.displayMetrics
            if (matchInfo == null) {
                // 记录系统的原始值
                matchInfo = MatchInfo()
                matchInfo!!.screenWidth = displayMetrics.widthPixels
                matchInfo!!.screenHeight = displayMetrics.heightPixels
                matchInfo!!.appDensity = displayMetrics.density
                matchInfo!!.appDensityDpi = displayMetrics.densityDpi.toFloat()
                matchInfo!!.appScaledDensity = displayMetrics.scaledDensity
                matchInfo!!.appXdpi = displayMetrics.xdpi
            }

            //最低版本大于 SDK 19
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                // 添加字体变化的监听
                with(application) {
                    registerComponentCallbacks(
                        object : ComponentCallbacks {
                                override fun onConfigurationChanged(newConfig: Configuration) {
                                    // 字体改变后,将 appScaledDensity 重新赋值
                                    if (newConfig.fontScale > 0) {
                                        matchInfo!!.appScaledDensity =
                                            resources.displayMetrics.scaledDensity
                                    }
                                }

                                override fun onLowMemory() {}
                            })
                }
            }
        }

        /**
         * 在 application 中全局激活适配（也可单独使用 match() 方法在指定页面中配置适配）
         *
         */
        @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        fun register(application: Application, designSize: Float, matchBase: Int, matchUnit: Int) {
            if (mActivityLifecycleCallback == null) {
                mActivityLifecycleCallback = object : ActivityLifecycleCallbacks {
                    override fun onActivityCreated(
                        activity: Activity,
                        savedInstanceState: Bundle?
                    ) {
                        //为每一个Activity都进行适配
                        match(activity, designSize, matchBase, matchUnit)
                    }

                    override fun onActivityStarted(activity: Activity) {}
                    override fun onActivityResumed(activity: Activity) {}
                    override fun onActivityPaused(activity: Activity) {}
                    override fun onActivityStopped(activity: Activity) {}
                    override fun onActivitySaveInstanceState(activity: Activity,
                        outState: Bundle) {}
                    override fun onActivityDestroyed(activity: Activity) {}
                }

                //再application中监听Activity生命周期变化
                application.registerActivityLifecycleCallbacks(mActivityLifecycleCallback)
            }
        }

        /**
         * 全局取消所有的适配
         */
        @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        fun unregister(application: Application, vararg matchUnit: Int) {
            if (mActivityLifecycleCallback != null) {
                application.unregisterActivityLifecycleCallbacks(mActivityLifecycleCallback)
                mActivityLifecycleCallback = null
            }
            for (unit in matchUnit) {
                cancelMatch(application, unit)
            }
        }

        /**
         * 适配屏幕（放在 Activity 的 setContentView() 之前执行）
         *
         * @param context
         * @param designSize 设计图的尺寸
         * @param matchBase  适配基准
         * @param matchUnit  使用的适配单位
         */
        @JvmOverloads
        fun match(
            context: Context,
            designSize: Float,
            matchBase: Int = MATCH_BASE_WIDTH,
            matchUnit: Int = MATCH_UNIT_DP
        ) {
            if (designSize == 0f) {
                throw UnsupportedOperationException("The designSize cannot be equal to 0")
            }

            if (matchUnit == MATCH_UNIT_DP) {
                matchByDP(context, designSize, matchBase)
            } else if (matchUnit == MATCH_UNIT_PT) {
                matchByPT(context, designSize, matchBase)
            }
        }

        /**
         * 重置适配信息，取消适配
         * @param context 上下文对象
         */
        fun cancelMatch(context: Context) {
            cancelMatch(context, MATCH_UNIT_DP)
            cancelMatch(context, MATCH_UNIT_PT)
        }

        /**
         * 重置适配信息，取消适配
         *
         * @param context 上下文对象
         * @param matchUnit 需要取消适配的单位
         */
        fun cancelMatch(context: Context, matchUnit: Int) {
            if (matchInfo != null) {
                val displayMetrics = context.resources.displayMetrics
                if (matchUnit == MATCH_UNIT_DP) {
                    if (displayMetrics.density != matchInfo!!.appDensity) {
                        displayMetrics.density = matchInfo!!.appDensity
                    }
                    if (displayMetrics.densityDpi.toFloat() != matchInfo!!.appDensityDpi) {
                        displayMetrics.densityDpi = matchInfo!!.appDensityDpi.toInt()
                    }
                    if (displayMetrics.scaledDensity != matchInfo!!.appScaledDensity) {
                        displayMetrics.scaledDensity = matchInfo!!.appScaledDensity
                    }
                } else if (matchUnit == MATCH_UNIT_PT) {
                    if (displayMetrics.xdpi != matchInfo!!.appXdpi) {
                        displayMetrics.xdpi = matchInfo!!.appXdpi
                    }
                }
            }
        }

        /**
         * 使用 dp 作为适配单位（适合在新项目中使用，在老项目中使用会对原来既有的 dp 值产生影响）
         *
         * dp 与 px 之间的换算:
         *  *  px = density * dp
         *  *  density = dpi / 160
         *  *  px = dp * (dpi / 160)
         *
         *
         * @param context
         * @param designSize 设计图的宽/高（单位: dp）
         * @param base       适配基准
         */
        private fun matchByDP(context: Context, designSize: Float, base: Int) {
            val targetDensity: Float = when (base) {
                MATCH_BASE_WIDTH -> matchInfo!!.screenWidth * 1f / designSize
                MATCH_BASE_HEIGHT -> matchInfo!!.screenHeight * 1f / designSize
                else -> matchInfo!!.screenWidth * 1f / designSize
            }

            val targetDensityDpi = (targetDensity * 160).toInt()
            val targetScaledDensity =
                targetDensity * (matchInfo!!.appScaledDensity / matchInfo!!.appDensity)

            //主要修改 density、densityDpi、scaledDensity
            val displayMetrics = context.resources.displayMetrics
            displayMetrics.density = targetDensity
            displayMetrics.densityDpi = targetDensityDpi
            displayMetrics.scaledDensity = targetScaledDensity
        }

        /**
         * 使用 pt 作为适配单位（因为 pt 比较冷门，新老项目皆适合使用；也可作为 dp 适配的补充，
         * 在需要同时适配宽度和高度时，使用 pt 来适配 dp 未适配的宽度或高度）
         *
         *  pt 转 px 算法: pt * metrics.xdpi * (1.0f/72)
         *
         * @param context
         * @param designSize 设计图的宽/高（单位: pt）
         * @param base       适配基准
         */
        private fun matchByPT(context: Context, designSize: Float, base: Int) {
            val targetXdpi: Float = when (base) {
                MATCH_BASE_WIDTH -> matchInfo!!.screenWidth * 72f / designSize
                MATCH_BASE_HEIGHT -> matchInfo!!.screenHeight * 72f / designSize
                else -> matchInfo!!.screenWidth * 72f / designSize
            }

            val displayMetrics = context.resources.displayMetrics
            displayMetrics.xdpi = targetXdpi
        }
    }
}