@file:Suppress("unused")

package com.silencefly96.module_common.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.View
import java.lang.ref.WeakReference
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 模仿博客粒子线条的view
 *
 * 核心思想
 * 1、黑色背景，初始中间一点，作为核心，随后每隔一段时间随机出现一个新的点
 * 2、新出现的点如果在核心点附近就会被吸引，否则自己成为核心
 * 3、新出现的点会和距离在范围内的点进行连线，最多只能连线三个
 * 4、每个点会随机移动，但是会趋向于离核心是一个固定值
 * 5、核心也会移动，接触到控件边界会有反弹
 *
 * @author silence
 * @date 2022-11-09
 *
 */
class ParticleLinesBgView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attributeSet, defStyleAttr){

    companion object{
        // 捕获距离
        const val ATTRACT_LENGTH = 200f

        // 维持的合适距离
        const val PROPER_LENGTH = 100f

        // 距离计算公式
        fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            return sqrt(((x1 - x2).toDouble().pow(2.0)
                    + (y1 - y2).toDouble().pow(2.0)).toFloat())
        }
    }

    // 处理的handler
    private val mHandler = ParticleHandler(this)

    // 画笔
    private val mPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 3f
        style = Paint.Style.STROKE
        flags = Paint.ANTI_ALIAS_FLAG
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = getDefaultSize(480, widthMeasureSpec)
        val height = getDefaultSize(720, heightMeasureSpec)
        // 自定义控件需要有默认值，不然无法使用wrap_content
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 测量完毕，开始作图
        // 第一个点
        generateNewParticle(w / 2f, h / 2f)
        // 通过发送消息给handler实现间隔添加其他点
        val message = mHandler.obtainMessage()
        message.arg1 = w
        message.arg2 = h
        mHandler.sendMessageDelayed(message, 300)
    }

    // 创建新的粒子
    private fun generateNewParticle(x: Float, y: Float) {

    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

    }

    // 粒子，weight: 作为核心时权重
    class Particle(var x: Float, var y: Float, var index: Int,
                   var isCenter: Boolean, var center: Particle?, var weight: Int) {

        // 随机移动，如有核心趋向于核心移动
        private fun move() {
            val dx: Float
            val dy: Float

            // 维持范围内，六分之一的改了脱离核心点
            val exitCenterOdds = (Math.random() * 6).toInt() == 0
            // 吸引范围内，二分之一概率被吸引
            val attractOdds = (Math.random() * 2).toInt() == 0

            // 计算偏移值
            if (center != null && (!exitCenterOdds || attractOdds)) {
                //趋向于中心
                val lenX = center!!.x - x
                val lenY = center!!.y - y
                dx = if (lenX > PROPER_LENGTH) lenX / 4f else -lenX / 4f
                dy = if (lenY > PROPER_LENGTH) lenY / 4f else -lenY / 4f
            }else {
                // 随机移动
                dx = (Math.random() * 2).toFloat()
                dy = (Math.random() * 2).toFloat()
            }

            x += dx
            y += dy
        }
    }

    // kotlin自动编译为Java静态类，控件引用使用弱引用
    class ParticleHandler(view: ParticleLinesBgView): Handler(Looper.getMainLooper()){
        // 控件引用
        private val mRef: WeakReference<ParticleLinesBgView> = WeakReference(view)
        override fun handleMessage(msg: Message) {
            // 从msg拿到宽高
            val x = (Math.random() * msg.arg1).toFloat()
            val y = (Math.random() * msg.arg2).toFloat()

            // 新增点
            mRef.get()?.generateNewParticle(x, y)
            // 循环发送
            mRef.get()?.mHandler?.sendMessageDelayed(msg, 300)
        }
    }
}