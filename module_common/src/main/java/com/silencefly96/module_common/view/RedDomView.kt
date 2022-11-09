@file:Suppress("unused")

package com.silencefly96.module_common.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.animation.addListener
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 拖拽消失的小红点
 *
 * 核心思想：
 * 1、一个正方形view，中间是小红点，小红点距离边框有一定距离
 * 2、拖动小红点，小红点会变形，并产生尾焰效果
 * 3、释放时，如果在设定范围外小红点消失，范围内则恢复
 *
 * @author silence
 * @date 2022-11-07
 *
 */
class RedDomView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attributeSet, defStyleAttr) {

    companion object{
        const val STATE_NORMAL = 0
        const val STATE_DRAGGING = 1
        const val STATE_SETTING = 2
        const val STATE_FINISHED = 3
    }

    // 状态
    private var mState = STATE_NORMAL

    /**
     * 红点半径占控件宽高的比例
      */
    var domPercent = 0.25f

    /**
     * 红点消失的长度占最短宽高的比例
     */
    var disappearPercent = 0.25f

    /**
     * 消失回调
     */
    var listener: OnDisappearListener? = null


    // 半径
    private var mDomRadius: Float = 0f

    // 消失长度
    private var mDisappearLength = 0f

    // 滑动距离和移动距离的缩放比例
    private val mDraggingScale = 0.5f

    // 圆心所在位置
    private var mRadiusX = 0f
    private var mRadiusY = 0f

    // 上一次touch的点
    private var mLastX = 0f
    private var mLastY = 0f

    // 绘制拖拽时的路径
    private val path = Path()

    // 恢复的属性动画
    private val animator = ValueAnimator.ofFloat(0f, 1f)

    // 画笔
    private val mPaint = Paint().apply {
        strokeWidth = 5f
        color = Color.RED
        style = Paint.Style.FILL
        flags = Paint.ANTI_ALIAS_FLAG
    }

    /**
     * 重置
     */
    fun reset() {
        mState = STATE_NORMAL
        mRadiusX = width / 2f
        mRadiusY = height / 2f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = getDefaultSize(100, widthMeasureSpec)
        val height = getDefaultSize(100, heightMeasureSpec)

        // 计算得到半径
        mDomRadius = (if (width < height) width else height) * domPercent
        mRadiusX = width / 2f
        mRadiusY = height / 2f

        // 消失长度
        mDisappearLength = (if (width < height) width else height) * disappearPercent

        setMeasuredDimension(width, height)
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 结束了不应该接受事件，通过设置OnClickListener使用reset去重置
        if (mState == STATE_FINISHED) {
            if (event.action == MotionEvent.ACTION_DOWN) performClick()
            else return true
        }

        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                mLastX = event.x
                mLastY = event.y

                // 设置中或者拖拽时，快速重新按下，应该再次接手动画
                if(mState != STATE_NORMAL) {
                    animator.removeAllListeners()
                    animator.cancel()
                }
                mState = STATE_DRAGGING
            }
            MotionEvent.ACTION_MOVE -> {
                // 注意canvas移动和手指移动是一致的，view的scroll移动的是窗口
                val dx = event.x - mLastX
                val dy = event.y - mLastY

                // 移动圆心
                mRadiusX += dx * mDraggingScale
                mRadiusY += dy * mDraggingScale

                mLastX = event.x
                mLastY = event.y

                // 请求重绘
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                mState = STATE_SETTING

                // 这里用属性动画模拟拖拽，回到初始圆心
                val upRadiusX = mRadiusX
                val upRadiusY = mRadiusY
                animator.addUpdateListener {
                    // 根据比例，按直线移动圆心到中点
                    val progress = it.animatedValue as Float
                    mRadiusX = upRadiusX + (width / 2f - upRadiusX) * progress
                    mRadiusY = upRadiusY + (height / 2f - upRadiusY) * progress
                    invalidate()
                }
                animator.addListener(onEnd = {
                    mState = STATE_NORMAL
                })
                animator.duration = 100
                animator.start()
            }
        }
        return true
    }

    @Suppress("RedundantOverride")
    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when(mState) {
            STATE_NORMAL -> {
                // 正常状态是一个圆
                canvas.drawCircle(width / 2f, height / 2f, mDomRadius, mPaint)
            }
            STATE_DRAGGING, STATE_SETTING -> {
                // 圆心和中点连线相对于X轴的夹角，注意atan2是四象限敏感[-PI, PI]，atan范围为[-PI/2, PI/2]
                val radiansLine = atan2((mRadiusY - height / 2f).toDouble(),
                    (mRadiusX - width /2f).toDouble()).toFloat()
                // 圆心和中点连线的长度，通过角度算，分母为零为什么没问题？
                val lineLength = (mRadiusX - width /2f) / cos(radiansLine)

                // 判断是否达到消失要求，如果消失不应该再绘制
                if (lineLength > mDisappearLength) {
                    mState = STATE_FINISHED
                    listener?.onDisappear()
                    return
                }

                // 以圆心为顶点，切点、圆心、中心的夹角值，是一个正值
                val radiansCenter = asin(mDomRadius / lineLength)
                // 切点和中心连线长度
                val length = lineLength * cos(radiansCenter)
                // 由角度获取两个切点的坐标值
                val x1 = width /2f + length * cos(radiansLine + radiansCenter)
                val y1 = height / 2f + length * sin(radiansLine + radiansCenter)
                val x2 = width /2f + length * cos(radiansLine - radiansCenter)
                val y2 = height / 2f + length * sin(radiansLine - radiansCenter)

                // 绘制
                // 普通代码，一个圆加三角形
//                canvas.drawCircle(mRadiusX, mRadiusY, mDomRadius, mPaint)
//                path.reset()
//                path.moveTo(x1, y1)
//                path.lineTo(width / 2f, height / 2f)
//                path.lineTo(x2, y2)
//                path.close()

                // 强行贝塞尔曲线
                // 先用完整的圆覆盖lineLength < 2 * mDomRadius的情况，大于时圆会被覆盖
                canvas.drawCircle(mRadiusX, mRadiusY, mDomRadius, mPaint)
                path.reset()
                path.moveTo(x1, y1)
                // 拟合圆弧，三阶贝塞尔曲线，控制点在圆心和中点连线的圆外
                var tempX1 = x1 + (length * cos(radiansLine + radiansCenter))
                var tempY1 = y1 + ( length * sin(radiansLine + radiansCenter))
                var tempX2 = x2 + (length * cos(radiansLine - radiansCenter))
                var tempY2 = y2 + ( length * sin(radiansLine - radiansCenter))
                // 接近圆不是圆
                path.cubicTo(tempX1, tempY1, tempX2, tempY2, x2, y2)

                // 尾焰，第一个控制点在切线延长线上，第二个控制点在圆心连线上(取值和效果有关)
                tempX1 = x2 - mDomRadius * cos(radiansLine - radiansCenter)
                tempY1 = y2 - mDomRadius * sin(radiansLine - radiansCenter)
                tempX2 = width / 2f + (lineLength * 0.25f * cos(radiansLine))
                tempY2 = height / 2f + (lineLength * 0.25f * sin(radiansLine))
                // 第一条
                path.cubicTo(tempX1, tempY1, tempX2, tempY2, width / 2f, height / 2f)

                // 另一段
                tempX1 = tempX2
                tempY1 = tempY2
                tempX2 = x1 - (mDomRadius * cos(radiansLine + radiansCenter))
                tempY2 = y1 - (mDomRadius * sin(radiansLine + radiansCenter))

                path.cubicTo(tempX1, tempY1, tempX2, tempY2, x1, y1)
                path.close()

                canvas.drawPath(path, mPaint)
            }
            STATE_FINISHED -> {}
        }

        // 这里便于调试，把消失范围画一下，多加一只画笔，省的麻烦
        canvas.drawCircle(width / 2f, height / 2f, mDisappearLength, tempPaint)
    }

    private val tempPaint = Paint().apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
        color = Color.LTGRAY
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        flags = Paint.ANTI_ALIAS_FLAG
    }

    interface OnDisappearListener{
        fun onDisappear()
    }
}