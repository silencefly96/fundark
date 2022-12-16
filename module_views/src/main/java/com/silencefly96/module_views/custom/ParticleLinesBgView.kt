@file:Suppress("unused")

package com.silencefly96.module_views.custom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.lang.ref.WeakReference
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 模仿博客粒子线条的view
 *
 * 核心思想简易版
 *
 * 1、随机出现点
 * 2、范围内的点连线
 * 3、手指按下，加入点，范围内点向手指移动
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

        // 维持的合适距离
        const val PROPER_LENGTH = 150f

        // 新增点的间隔时间
        const val ADD_POINT_TIME = 100L

        // 距离计算公式
        fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            return sqrt(((x1 - x2).toDouble().pow(2.0)
                    + (y1 - y2).toDouble().pow(2.0)).toFloat())
        }
    }

    // 存放的粒子, LRU
    private var nextIndex = 0
    private val mParticles =
        object: LinkedHashMap<Int, Particle>(200, 0.75f, true){
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Particle>): Boolean {
            val result = size > 200
            if (result) nextIndex = eldest.key else nextIndex++
            return result
        }
    }

    // 处理的handler
    private val mHandler = ParticleHandler(this)

    // 手指按下位置
    private var mTouchParticle: Particle? = null

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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                mTouchParticle = Particle(event.x, event.y)
                mHandler.removeCallbacksAndMessages(null)
            }
            MotionEvent.ACTION_MOVE -> {
                mTouchParticle!!.x = event.x
                mTouchParticle!!.y = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                mTouchParticle = null
                // 重新更新
                val message = mHandler.obtainMessage()
                message.arg1 = width
                message.arg2 = height
                mHandler.sendMessageDelayed(message, ADD_POINT_TIME)
            }
        }
        return true
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
        mHandler.sendMessageDelayed(message, ADD_POINT_TIME)
    }

    // 创建新的粒子
    private fun generateNewParticle(x: Float, y: Float) {

        // 创建新粒子
        val particle = Particle(x, y)
        mParticles[nextIndex] = particle

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 更新
        for (i in mParticles.values) {
            // 随机移动
            var dx = (Math.random() * 4).toFloat() - 2
            var dy = (Math.random() * 4).toFloat() - 2
            if (mTouchParticle != null) {
                val distance = getDistance(mTouchParticle!!.x, mTouchParticle!!.y, i.x, i.y)
                if (distance < 2 * PROPER_LENGTH && distance > PROPER_LENGTH) {
                    // 趋向于中心
                    dx = (mTouchParticle!!.x - i.x) / 10f
                    dy = (mTouchParticle!!.y - i.y) / 10f
                }
            }

            if (i.x + dx > width || i.x + dx < 0) i.x -= dx else i.x += dx
            if (i.y + dy > height || i.y + dy < 0) i.y -= dy else i.y += dy
        }

        // 遍历连线
        for (i in 0 until mParticles.size) {
            for (j in i until mParticles.size) {
                if (getDistance(mParticles[i]!!.x, mParticles[i]!!.y,
                        mParticles[j]!!.x, mParticles[j]!!.y) < PROPER_LENGTH
                ) {
                    canvas.drawLine(mParticles[i]!!.x, mParticles[i]!!.y,
                        mParticles[j]!!.x, mParticles[j]!!.y, mPaint)
                }
            }

            // 和手指的点连线
            if (mTouchParticle != null && getDistance(mParticles[i]!!.x, mParticles[i]!!.y,
                    mTouchParticle!!.x, mTouchParticle!!.y) < PROPER_LENGTH
            ) {
                canvas.drawLine(mParticles[i]!!.x, mParticles[i]!!.y,
                    mTouchParticle!!.x, mTouchParticle!!.y, mPaint)
            }
        }

        // 吸引范围
        mTouchParticle?.let {
            mPaint.color = Color.BLUE
            canvas.drawCircle(mTouchParticle!!.x, mTouchParticle!!.y, PROPER_LENGTH, mPaint)
            mPaint.color = Color.LTGRAY
        }
    }


    // 粒子
    class Particle(var x: Float, var y: Float)

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
            val message = obtainMessage()
            message.arg1 = msg.arg1
            message.arg2 = msg.arg2
            mRef.get()?.mHandler?.sendMessageDelayed(message, ADD_POINT_TIME)
        }
    }
}