package com.silencefly96.module_hardware.camera.video

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.core.view.children
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt


/**
 * 简化的DesktopLayerLayout，实用起来，只考虑横向，要处理滑动冲突
 *
 * @author silence
 * @date 2024-03-11
 */
class ScrollViewLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attributeSet, defStyleAttr) {

    companion object{
        // 状态
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
    }

    /**
     * 当前主页面的index
     */
    private var curIndex = 0

    // 由于将view提高层级会搞乱顺序，需要记录原始位置信息
    private val mInitViews = ArrayList<View>()

    // view之间的间距
    private var mGateLength = 0

    // 系统最小移动距离
    private val mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // 滑动距离
    private var mDxLen = 0f

    // 控件状态
    private var mState = SCROLL_STATE_IDLE

    // 当前设置的属性动画
    private var mValueAnimator: ValueAnimator? = null

    // 上一次按下的横竖坐标
    private var mLastX = 0f

    // 获得所有xml内的view，保留原始顺序
    override fun onFinishInflate() {
        super.onFinishInflate()
        mInitViews.addAll(children)
    }

    @Suppress("UnnecessaryVariable")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 获取默认尺寸，考虑背景大小
        val width = max(getDefaultSize(0, widthMeasureSpec), suggestedMinimumWidth)
        val height = max(getDefaultSize(0, heightMeasureSpec), suggestedMinimumHeight)

        // 设置间距
        mGateLength = width / 4

        // 中间 view 占满高度
        val maxWidth = height
        val maxHeight = height

        // 两侧 view 大小
        val minWidth = (height / 2f).toInt()
        val minHeight = (height / 2f).toInt()

        var childWidth: Int
        var childHeight: Int
        for (i in 0 until childCount) {
            val child = mInitViews[i]
            val scanSize = getViewScanSize(i, scrollX)
            childWidth = minWidth + ((maxWidth - minWidth) * scanSize).toInt()
            childHeight = minHeight + ((maxHeight - minHeight) * scanSize).toInt()

            child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY))
        }

        setMeasuredDimension(width, height)
    }

    // 选中view为最大，可见部分会缩放，不可见部分和第三排一样大
    private fun getViewScanSize(index: Int, scrolledLen: Int): Float {
        var scanSize = 0f

        // 开始时当前view未测量，不计算
        if (measuredWidth == 0) return scanSize

        // 初始化的时候，第一个放中间，所以index移到可见范围为[2+index, index-2]，可见!=可移动
        val scrollLeftLimit = (index - 2) * mGateLength
        val scrollRightLimit = (index + 2) * mGateLength

        // 先判断child是否可见
        if (scrolledLen in scrollLeftLimit..scrollRightLimit) {
            // 根据二次函数计算比例
            scanSize = scanByParabola(scrollLeftLimit, scrollRightLimit, scrolledLen).toFloat()
        }

        return scanSize
    }

    // 根据抛物线计算比例，y属于[0, 1]
    // 映射关系：(form, 0) ((from + to) / 2, 0) (to, 0) -> (0, 0) (1, 1) (2, 0)
    private fun scanByParabola(from: Int, to: Int, cur: Int): Double {
        // 公式：val y = 1 - (x - 1).toDouble().pow(2.0)
        // Log.e("TAG", "scanByParabola:from=$from, to=$to, cur=$cur ")
        val x = ((cur - from) / (to - from).toFloat() * 2).toDouble()
        return 1 - (x - 1).pow(2.0)
    }

    // layout 按顺序间距排列即可，大小有onMeasure控制,开始位置在中心，也和curIndex无关
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        Log.e("TAG", "onLayout(container): top=$t, bottom=$b")
        Log.e("TAG", "onLayout(container): left=$l, right=$r")
        // 从正中间开始排列
        val startX = (r + l) / 2

        // 排列布局
        for (i in 0 until childCount) {
            val child = mInitViews[i]

            // 中间减去间距，再减去一半的宽度，得到左边坐标
            val left = startX + mGateLength * i - child.measuredWidth / 2
            val top = (b + t) / 2 - child.measuredHeight / 2
            val right = left + child.measuredWidth
            val bottom = top + child.measuredHeight

            Log.e("TAG", "onLayout($i): top=$top, bottom=$bottom")
            Log.e("TAG", "onLayout($i): left=$left, right=$right")
            child.layout(left, top, right, bottom)
        }

        // 修改大小，布局完成后移动
        scrollBy(mDxLen.toInt(), 0)
        mDxLen = 0f

        // 完成布局及移动后，绘制之前，将可见view提高层级
        val targetIndex = getCurrentIndex()
        for (i in 2 downTo 0) {
            val preIndex = targetIndex - i
            val aftIndex = targetIndex + i

            // 逐次提高层级，注意在mInitViews拿就可以，不可见不管
            if (preIndex in 0..childCount) {
                bringChildToFront(mInitViews[preIndex])
            }

            if (aftIndex != preIndex && aftIndex in 0 until childCount) {
                bringChildToFront(mInitViews[aftIndex])
            }
        }
    }

    // 根据滚动距离获得当前index
    private fun getCurrentIndex()= (scrollX / mGateLength.toFloat()).roundToInt()

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when(it.action) {
                MotionEvent.ACTION_DOWN -> {
                    mLastX = ev.x
                    if(mState == SCROLL_STATE_IDLE) {
                        mState = SCROLL_STATE_DRAGGING
                    }else if (mState == SCROLL_STATE_SETTLING) {
                        mState = SCROLL_STATE_DRAGGING
                        // 去除结束监听，结束动画
                        mValueAnimator?.removeAllListeners()
                        mValueAnimator?.cancel()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    // 若ACTION_DOWN是本view拦截，则下面代码不会触发，要在onTouchEvent判断
                    val dX = mLastX - ev.x
                    return checkScrollInView(scrollX + dX)
                }
                MotionEvent.ACTION_UP -> {}
            }
        }
        return super.onInterceptHoverEvent(ev)
    }

    // 根据可以滚动的范围，计算是否可以滚动
    private fun checkScrollInView(length : Float): Boolean {
        // 一层情况
        if (childCount <= 1) return false
        // 左右两边最大移动值，即把最后一个移到中间
        val leftScrollLimit = 0
        val rightScrollLimit = (childCount - 1) * mGateLength

        return (length >= leftScrollLimit && length <= rightScrollLimit)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when(it.action) {
                // 防止点击空白位置或者子view未处理touch事件
                MotionEvent.ACTION_DOWN -> return true
                MotionEvent.ACTION_MOVE -> {
                    // 如果是本view拦截的ACTION_DOWN，要在此判断
                    val dX = mLastX - ev.x
                    if(checkScrollInView(scrollX + dX)) {
                        move(ev)
                    }
                }
                MotionEvent.ACTION_UP -> moveUp()
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun move(ev: MotionEvent) {
        val dX = mLastX - ev.x

        // 修改mScrollLength，重新measure及layout，再onLayout的最后实现移动
        mDxLen += dX
        if(abs(mDxLen) >= mTouchSlop) {
            requestLayout()
        }

        // 更新值
        mLastX = ev.x
    }

    private fun moveUp() {
        // 赋值
        val targetScrollLen = getCurrentIndex() * mGateLength

        // 这里使用ValueAnimator处理剩余的距离，模拟滑动到需要的位置
        val animator = ValueAnimator.ofFloat(scrollX.toFloat(), targetScrollLen.toFloat())
        animator.addUpdateListener { animation ->
            // Log.e("TAG", "stopMove: " + animation.animatedValue as Float)
            mDxLen = animation.animatedValue as Float - scrollX
            requestLayout()
        }

        // 在动画结束时修改curIndex
        animator.addListener (onEnd = {
            curIndex = getCurrentIndex()
            mState = SCROLL_STATE_IDLE
        })

        // 设置状态
        mState = SCROLL_STATE_SETTLING

        animator.duration = 300L
        animator.start()
    }
}