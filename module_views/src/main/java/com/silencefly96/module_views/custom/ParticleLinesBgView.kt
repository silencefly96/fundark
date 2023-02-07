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
        // 屏幕刷新时间，每秒20次
        const val SCREEN_FLUSH_TIME = 50L

        // 新增点的间隔时间
        const val POINT_ADD_TIME = 200L

        // 粒子存活时间
        const val POINT_ALIVE_TIME = 18000L

        // 吸引的合适距离
        const val ATTRACT_LENGTH = 250f

        // 维持的合适距离
        const val PROPER_LENGTH = 150f

        // 粒子被吸引每次接近的距离
        const val POINT_MOVE_LENGTH = 30f

        // 距离计算公式
        fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            return sqrt(((x1 - x2).toDouble().pow(2.0)
                    + (y1 - y2).toDouble().pow(2.0)).toFloat())
        }
    }

    // 存放的粒子
    private val mParticles = ArrayList<Particle>(64)

    // 手指按下位置
    private var mTouchParticle: Particle? = null

    // 处理的handler
    private val mHandler = ParticleHandler(this)

    // 画笔
    private val mPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 3f
        style = Paint.Style.STROKE
        flags = Paint.ANTI_ALIAS_FLAG
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 通过发送消息给handler实现间隔添加点
        mHandler.removeMessages(0)
        mHandler.sendEmptyMessageDelayed(0, POINT_ADD_TIME)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 绘制点和线
        for (i in 0 until mParticles.size) {
            val point = mParticles[i]
            canvas.drawPoint(point.x, point.y, mPaint)
            // 连线
            for (j in (i + 1) until mParticles.size) {
                val another = mParticles[j]
                val distance = getDistance(point.x, point.y, another.x, another.y)
                if (distance <= PROPER_LENGTH) {
                    canvas.drawLine(point.x, point.y, another.x, another.y, mPaint)
                }
            }
        }

        mTouchParticle?.let {
            // 手指按下点与附近连线
            for(point in mParticles) {
                val distance = getDistance(point.x, point.y, it.x, it.y)
                if (distance <= PROPER_LENGTH) {
                    canvas.drawLine(point.x, point.y, it.x, it.y, mPaint)
                }
            }

            // 吸引范围显示
            mPaint.color = Color.BLUE
            canvas.drawCircle(it.x, it.y, PROPER_LENGTH, mPaint)
            mPaint.color = Color.LTGRAY
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                mTouchParticle = Particle(event.x, event.y, 0)
            }
            MotionEvent.ACTION_MOVE -> {
                mTouchParticle!!.x = event.x
                mTouchParticle!!.y = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                mTouchParticle = null
            }
        }
        return true
    }

    // 粒子
    class Particle(var x: Float, var y: Float, var counter: Int)

    // kotlin自动编译为Java静态类，控件引用使用弱引用
    class ParticleHandler(view: ParticleLinesBgView): Handler(Looper.getMainLooper()){
        // 控件引用
        private val mRef: WeakReference<ParticleLinesBgView> = WeakReference(view)
        // 粒子出现控制
        private var mPointCounter = 0

        override fun handleMessage(msg: Message) {
            mRef.get()?.let {view->
                // 新增点
                mPointCounter++
                if (mPointCounter == (POINT_ADD_TIME / SCREEN_FLUSH_TIME).toInt()) {
                    // 随机位置
                    val x = (Math.random() * view.width).toFloat()
                    val y = (Math.random() * view.height).toFloat()
                    view.mParticles.add(Particle(x, y, 0))
                    mPointCounter = 0
                }

                val iterator = view.mParticles.iterator()
                while (iterator.hasNext()) {
                    val point = iterator.next()

                    // 移除失活粒子
                    if (point.counter == (POINT_ALIVE_TIME / SCREEN_FLUSH_TIME).toInt()) {
                        iterator.remove()
                    }

                    // 手指按下时，粒子朝合适的距离移动
                    view.mTouchParticle?.let {
                        val distance = getDistance(point.x, point.y, it.x, it.y)
                        if(distance in PROPER_LENGTH..ATTRACT_LENGTH) {
                            // 横向接近
                            if (point.x < it.x) point.x += POINT_MOVE_LENGTH
                            else point.x -= POINT_MOVE_LENGTH
                            // 纵向接近
                            if (point.y < it.y) point.y += POINT_MOVE_LENGTH
                            else point.y -= POINT_MOVE_LENGTH
                        }else if(distance <= PROPER_LENGTH) {
                            // 横向远离
                            if (point.x < it.x) point.x -= POINT_MOVE_LENGTH
                            else point.x += POINT_MOVE_LENGTH
                            // 纵向远离
                            if (point.y < it.y) point.y -= POINT_MOVE_LENGTH
                            else point.y += POINT_MOVE_LENGTH
                        }
                    }
                }

                // 循环发送
                view.invalidate()
                view.mHandler.sendEmptyMessageDelayed(0, POINT_ADD_TIME)
            }
        }
    }
}