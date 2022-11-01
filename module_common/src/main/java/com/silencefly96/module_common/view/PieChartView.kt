package com.silencefly96.module_common.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 饼状图
 *
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attributeSet, defStyleAttr) {

    // 第二个手指按下时两指间的距离，即初始距离，放大比例以此为基准
    private var mFirstDistance = 0f
    // MOVE 触发次数太多，调整一下，累计距离大于一定值才缩放
    private var mLastDistance = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when(ev.actionMasked) {
            // 第一个点按下
            MotionEvent.ACTION_DOWN -> {}
            // 第二个或者以上的点按下
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount == 2) {
                    // 两指时计算初始距离
                    mFirstDistance = getDistance(
                        ev.getX(0), ev.getY(0),
                        ev.getX(1), ev.getY(1))
                    mLastDistance = mFirstDistance

                    Log.e("TAG", "mFirstDistance: $mFirstDistance")
                }
            }
            // 所有的点移动
            MotionEvent.ACTION_MOVE -> {
                // 当有两个点时处理移动，且只处理第一个或者第二个点的移动
                if (ev.pointerCount == 2) {
                    val newDistance = getDistance(
                        ev.getX(0), ev.getY(0),
                        ev.getX(1), ev.getY(1))

                    // 缩小放大
                    if (abs(mLastDistance - newDistance) > 5) {
                        Log.e("TAG", "newDistance: $newDistance")
                        val scaleSize = newDistance / mFirstDistance
                        scaleX = scaleSize
                        scaleY = scaleSize

                        // 更新
                        mLastDistance = newDistance
                    }
                }
            }
            // 非最后一个点抬起
            MotionEvent.ACTION_POINTER_UP -> {}
            // 最后一个点抬起
            MotionEvent.ACTION_UP -> {}
        }

        // 注意拦截事件，至少拦截ACTION_DOWN
        return true
    }

    private fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        // 平方和公式
        @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
        return sqrt((Math.pow((x1 - x2).toDouble(), 2.0)
                + Math.pow((y1 - y2).toDouble(), 2.0)).toFloat())
    }
}