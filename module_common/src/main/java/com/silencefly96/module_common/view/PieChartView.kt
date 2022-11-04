@file:Suppress("unused")

package com.silencefly96.module_common.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * 饼状图
 *
 * 核心思想：
 * 1、外面传入数据，实现扇形图展示
 * 2、扇形图能够单指旋转、二指放大、三指移动，四指以上同时按下进行复位
 * 3、旋转、放大、平移效果能够叠加
 *
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attributeSet, defStyleAttr) {

    companion object{
        // 没有手指按下状态
        const val STATE_NORMAL = 0
        // 单指按下，进行旋转
        const val STATE_ROTATING = 1
        // 双指按下，进行缩放
        const val STATE_SCALING = 2
        // 三指按下，进行移动
        const val STATE_MOVING = 3
        // 三指以上按下，进行复位
        const val STATE_RESETTING = 4
    }

    /**
     * 数据，所占比例根据单位占总数计算
     */
    var data: MutableList<Int>? = null
    set(value) {
        field = value
        calculatePercent()
    }

    // 用于图表绘制的数据
    private val mPieData: MutableList<Triple<Int, Float, Int>> = ArrayList()

    // 半径占最小边框的比例
    private val mRadiusPercent = 0.8f

    // 画笔
    private val mPaint: Paint = Paint().apply {
        // 颜色
        color = Color.BLACK
        // 粗细，设置为0时无论怎么放大 都是1像素
        strokeWidth = 5f
        // 抗锯齿
        flags = Paint.ANTI_ALIAS_FLAG
        // 填充模式
        style = Paint.Style.FILL
    }

    // 矩形, 绘制弧形需要用到
    private var mRectF: RectF = RectF()

    // 中点坐标
    private var mCenterX: Int = 0
    private var mCenterY: Int = 0

    // 圆的半径
    private var mRadius: Float = 0f

    // 状态
    private var mState = STATE_NORMAL

    // 单指情况
    // 开始坐标
    private var mLastX = 0f
    private var mLastY = 0f
    private var mDegree = 0f
    private var mCountDegree = 0f

    // 双指情况
    // 第二个手指按下时两指间的距离，即初始距离，放大比例以此为基准
    private var mFirstDistance = 1f
    private var mCurrentDistance = 1f

    // 三指情况
    // 上一次三点中心
    private var mLastTripleCenterX = 0f
    private var mLastTripleCenterY = 0f
    // 本次移动值
    private var mMoveX = 0f
    private var mMoveY = 0f
    // 累计移动值
    private var mCountMoveX = 0f
    private var mCountMoveY = 0f


    // 计算比例
    private fun calculatePercent() {
        var count = 0
        val colors = arrayOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN)
        // 计算总数
        for (i in data!!) {
            count += i
        }
        // 填入比例
        var color = colors[0]
        var lastColor = colors[1]
        for (i in data!!) {
            // 避免相邻颜色相同
            while (color == lastColor) {
                color = colors[(Math.random() * colors.size).toInt()]
            }
            mPieData.add(Triple(i, i / count.toFloat(), color))
            lastColor = color
        }

        Log.e("TAG", "calculatePercent: mPieData=$mPieData")
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // 自定义view要设置好默认大小
        val width = getDefaultSize(100, widthMeasureSpec)
        val height = getDefaultSize(100, heightMeasureSpec)

        // 由控件宽高获得中心点坐标
        mCenterX = width / 2
        mCenterY = height / 2

        // 半径,设置为最小宽度的80%
        mRadius = (if (mCenterX < mCenterY) mCenterX else mCenterY) * mRadiusPercent

        // 绘制的矩形
        mRectF.set(mCenterX - mRadius, mCenterY - mRadius,
            mCenterX + mRadius, mCenterY + mRadius)

        setMeasuredDimension(width, height)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when(ev.actionMasked) {
            // 第一个点按下
            MotionEvent.ACTION_DOWN -> {
                //Log.e("TAG", "ACTION_DOWN --> 1")
                // 一只手指时旋转
                mState = STATE_ROTATING
                mLastX = ev.x
                mLastY = ev.y
            }
            // 第二个或者以上的点按下
            MotionEvent.ACTION_POINTER_DOWN -> {
                when (ev.pointerCount) {
                    2 -> {
                        //Log.e("TAG", "ACTION_POINTER_DOWN --> 2")
                        mState = STATE_SCALING
                        // 两指时计算初始距离
                        mFirstDistance = getDistance(
                            ev.getX(0), ev.getY(0),
                            ev.getX(1), ev.getY(1))
                        mCurrentDistance = mFirstDistance
                    }
                    3 -> {
                        Log.e("TAG", "ACTION_POINTER_DOWN --> 3")
                        mState = STATE_MOVING
                        // 三指时计算三点的中心
                        val pair = getTripleCenter(
                            ev.getX(0), ev.getY(0),
                            ev.getX(1), ev.getY(1),
                            ev.getX(2), ev.getY(2))
                        mLastTripleCenterX = pair.first
                        mLastTripleCenterY = pair.second
                        Log.e("TAG", "ACTION_POINTER_DOWN --> ($mLastTripleCenterX, $mLastTripleCenterY)")
                    }
                    else -> {
                        //Log.e("TAG", "ACTION_POINTER_DOWN --> 4+")
                        mState = STATE_RESETTING
                        // 更改状态，刷新即可
                        invalidate()
                    }
                }
            }
            // 所有的点移动
            MotionEvent.ACTION_MOVE -> {
                // 移动时处理，如果需要跟踪手指，需要用到actionIndex、PointerId、PointerIndex
                if (ev.pointerCount == 1) {
                    //Log.e("TAG", "ACTION_MOVE --> 1")
                    // 当前点和上一次的点计算角度
                    mDegree = getDegree(mLastX, mLastY, ev.x, ev.y).toFloat()
                    mLastX = ev.x
                    mLastY = ev.y
                    invalidate()
                }else if (ev.pointerCount == 2) {
                    //Log.e("TAG", "ACTION_MOVE --> 2")
                    val newDistance = getDistance(
                        ev.getX(0), ev.getY(0),
                        ev.getX(1), ev.getY(1))

                    // 缩小放大，限定些范围减少调用，不用touchSlop值太小了
                    if (abs(mCurrentDistance - newDistance) > 5) {
                        // 更新
                        mCurrentDistance = newDistance
                        // 不使用scaleX和scaleY，直接onDraw里面自己处理
                        invalidate()
                    }
                }else if(ev.pointerCount == 3) {
                    //Log.e("TAG", "ACTION_MOVE --> 3")
                    // 三指时计算三点的中心
                    val pair = getTripleCenter(
                        ev.getX(0), ev.getY(0),
                        ev.getX(1), ev.getY(1),
                        ev.getX(2), ev.getY(2))

                    mMoveX = pair.first - mLastTripleCenterX
                    mMoveY = pair.second - mLastTripleCenterY
                    // 限定移动范围
                    val maxLengthX = width / 2f - mRadius
                    val maxLengthY = height / 2f - mRadius
                    if ((mCountMoveX + mMoveX) >= -maxLengthX ||
                        (mCountMoveX + mMoveX) <= maxLengthX ||
                        (mCountMoveY + mMoveY) >= -maxLengthY ||
                        (mCountMoveY + mMoveY) <= maxLengthY) {
                        mLastTripleCenterX = pair.first
                        mLastTripleCenterY = pair.second
                        //Log.e("TAG", "ACTION_POINTER_DOWN --> ($mLastTripleCenterX, $mLastTripleCenterY)")
                        invalidate()
                    }else{
                        mMoveX = 0f
                        mMoveY = 0f
                    }
                }
            }
            // 非最后一个点抬起
            MotionEvent.ACTION_POINTER_UP -> {
                //Log.e("TAG", "ACTION_POINTER_UP --> ${ev.pointerCount}")
                // 经过测试，手指抬起时还未更改pointerCount
                when (ev.pointerCount) {
                    2 -> mState = STATE_ROTATING
                    3 -> mState = STATE_SCALING
                    4 -> mState = STATE_MOVING
                }
            }
            // 最后一个点抬起
            MotionEvent.ACTION_UP -> {
                //Log.e("TAG", "ACTION_UP --> 1")
                mState = STATE_NORMAL
            }
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

    private fun getTripleCenter(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float)
        : Pair<Float, Float> {
        val x = (x1 + x2 + x3) / 3
        val y = (y1 + y2 + y3) / 3
        return Pair(x, y)
    }

    private fun getDegree(x1: Float, y1: Float, x2: Float, y2: Float): Double {
        // 带上移动偏移量的圆心
        val centerX = mCenterX + mCountMoveX
        val centerY = mCenterY + mCountMoveY

        // 起点角度
        val radians1 = atan2(y1 - centerY, x1 - centerX).toDouble()
        // 终点角度
        val radians2 = atan2(y2 - centerY, x2 - centerX).toDouble()
        // 从弧度转换成角度
        // Log.e("TAG", "getDegree: $degree")
        return Math.toDegrees(radians2 - radians1)
    }

    override fun onDraw(canvas: Canvas) {
        // 混合效果
        if (mState != STATE_RESETTING) {
            // 对canvas进行旋转，注意每次canvas都会复位，所以要用累加值
            if (mState == STATE_ROTATING) {
                mCountDegree += mDegree
            }
            canvas.rotate(mCountDegree, mCenterX.toFloat() + mCountMoveX,
                mCenterY.toFloat() + mCountMoveY)


            // 对canvas进行缩放
            val scale = mCurrentDistance / mFirstDistance
            canvas.scale(scale, scale, mCenterX.toFloat(), mCenterY.toFloat())


            // 对canvas进行移动
            if(mState == STATE_MOVING) {
                mCountMoveX += mMoveX
                mCountMoveY += mMoveY
            }
            canvas.translate(mCountMoveX, mCountMoveY)
        }else {
            // 重置
            mCountMoveX = 0f
            mCurrentDistance = mFirstDistance
            mCountMoveX = 0f
            mCountMoveY = 0f
        }

        // 绘制圆弧
        var angleCount = 0f
        for (peer in mPieData) {
            val angle = 360 * peer.second
            mPaint.color = peer.third
            canvas.drawArc(mRectF, angleCount, angle, true, mPaint)
            angleCount += angle
        }
    }
}