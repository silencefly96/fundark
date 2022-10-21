@file:Suppress("unused")

package com.silencefly96.module_common.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Scroller
import com.silencefly96.module_common.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow


/**
 * 模仿桌面切换的多页面切换控件
 * 核心思想：
 * 1、类似viewpager，但同时显示两种页面，中间为主页面，左右为小页面，小页面大小一样，间距排列
 * 2、左右滑动可以将切换页面，超过页面数量大小不能滑动，滑动停止主界面能自动移动到目标位置
 * 3、页面向上移动后，进入拖拽模式，可以将页面拖拽移动到其他页面位置，进行位置调整
 * 4、需要处理滑动冲突，左右和上下的滑动冲突都需要处理
 *
 * @author silence
 * @date 2022-10-20
 */
class DesktopLayerLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attributeSet, defStyleAttr) {

    companion object{
        // 方向
        const val ORIENTATION_VERTICAL = 0
        const val ORIENTATION_HORIZONTAL = 1

        // 状态
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
        const val SCROLL_STATE_DRAG_MODE_MOVING = 3
        const val SCROLL_STATE_DRAG_MODE_SETTING = 4

        // 默认padding值
        const val DEFAULT_PADDING_VALUE = 50

        // 竖向默认主界面比例
        const val DEFAULT_MAIN_PERCENT_VERTICAL = 0.8f

        // 横向默认主界面比例
        const val DEFAULT_MAIN_PERCENT_HORIZONTAL = 0.6f

        // 其他页面相对主界面页面最小的缩小比例
        const val DEFAULT_OTHER_VIEW_SCAN_SIZE = 0.5f

        // 默认主界面比例
        const val DEFAULT_DRAG_MODE_UP_PERCENT = 0.15f

        // 默认滑动距离生效占横屏比例
        const val DEFAULT_SLIDE_EFFECT_PERCENT = 0.50f
    }

    /**
     * 当前主页面的index
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var curIndex = 0
    set(value) {
        field = value
        // 修改选中下标，重新测量布局就行，后面处理
        requestLayout()
    }

    // 滑动距离
    private var mScrollLength = 0f

    // 实际布局的左右坐标值
    private var mRealLeft = 0
    private var mRealRight = 0

    // 上一次按下的横竖坐标
    private var mLastX = 0f
    private var mLastY = 0F

    // 流畅滑动工具
    private val mScroller = Scroller(context)

    // 方向，从XML内获得
    private var mOrientation: Int

    // 是否对屏幕方向自适应，从XML内获得
    private val isAutoFitOrientation: Boolean

    // padding，从XML内获得，如果左右移动，则上下要有padding，但左右没有padding
    private val mPaddingValue: Int

    // 竖向主内容比例，从XML内获得，剩余两边平分
    private val mMainPercentVertical: Float

    // 横向主内容比例，从XML内获得，剩余两边平分
    private val mMainPercentHorizontal: Float

    // 其他页面相对主界面页面最小的缩小比例
    private val mOtherViewScanMinSize: Float

    // 向上滑动进入拖拽模式的向上高度百分比，从XML内获得
    private val mDragModeUpPercent: Float

    // 滑动距离生效占横屏比例
    private val mSlideEffectPercent: Float

    init {
        // 获取XML参数
        val typedArray =
            context.obtainStyledAttributes(attributeSet, R.styleable.DesktopLayerLayout)

        mOrientation = typedArray.getInteger(R.styleable.DesktopLayerLayout_mOrientation,
            ORIENTATION_VERTICAL)

        isAutoFitOrientation =
            typedArray.getBoolean(R.styleable.DesktopLayerLayout_isAutoFitOrientation, false)

        mPaddingValue = typedArray.getInteger(R.styleable.DesktopLayerLayout_mPaddingValue,
            DEFAULT_PADDING_VALUE)

        mMainPercentVertical =
            typedArray.getFraction(R.styleable.DesktopLayerLayout_mMainPercentVertical,
            1, 1, DEFAULT_MAIN_PERCENT_VERTICAL)

        mMainPercentHorizontal =
            typedArray.getFraction(R.styleable.DesktopLayerLayout_mMainPercentHorizontal,
            1, 1, DEFAULT_MAIN_PERCENT_HORIZONTAL)

        mOtherViewScanMinSize =
            typedArray.getFraction(R.styleable.DesktopLayerLayout_mOtherViewScanMinSize,
            1, 1, DEFAULT_OTHER_VIEW_SCAN_SIZE)

        mDragModeUpPercent =
            typedArray.getFraction(R.styleable.DesktopLayerLayout_mDragModeUpPercent,
                1, 1, DEFAULT_DRAG_MODE_UP_PERCENT)

        mSlideEffectPercent =
            typedArray.getFraction(R.styleable.DesktopLayerLayout_mSlideEffectPercent,
            1, 1, DEFAULT_SLIDE_EFFECT_PERCENT)

        typedArray.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 根据屏幕变化修改方向，自适应
        if (isAutoFitOrientation) {
            mOrientation = if (w > h) ORIENTATION_HORIZONTAL else ORIENTATION_VERTICAL
            requestLayout()
        }
    }

/*    override fun onFinishInflate() {
        super.onFinishInflate()
        moveCurViewToLast(curIndex, curIndex)
    }

    // 将选中view放到最后，即绘制在顶层
    private fun moveCurViewToLast(index: Int, preIndex: Int) {
        if (index == preIndex) return
        if (isValuableIndex(index)) {
            // 把原来的view放回去
            val preView = getChildAt(childCount - 1)
            removeViewInLayout(preView)
            addViewInLayout(preView, childCount, preView.layoutParams)

            // 调整新的view
            val curView = getChildAt(index)
            removeViewInLayout(curView)
            addViewInLayout(curView, childCount, curView.layoutParams)
        }else {
            throw IndexOutOfBoundsException("index should in range [0, $childCount)")
        }
    }*/

    private fun isValuableIndex(index: Int) = (index in 0 until childCount)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // 获取默认尺寸，考虑背景大小
        val width = max(getDefaultSize(0, widthMeasureSpec), suggestedMinimumWidth)
        val height = max(getDefaultSize(0, heightMeasureSpec), suggestedMinimumHeight)

        // 对主内容view测量
        val curView = getChildAt(curIndex)
        // 根据抛物线得到缩放比例
        val scanSize = mOtherViewScanMinSize + scanByParabola(0, 1,
            (mScrollLength / (width * mSlideEffectPercent)).toInt())
        val curWidth: Int
        val curHeight: Int

        // 不同方向尺寸不同
        if (mOrientation == ORIENTATION_HORIZONTAL) {
            curWidth = (width * scanSize).toInt()
            curHeight = height - 2 * mPaddingValue
        }else {
            curWidth = (width * scanSize).toInt()
            curHeight = height - 2 * mPaddingValue
        }

        curView.measure(MeasureSpec.makeMeasureSpec(curWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(curHeight, MeasureSpec.EXACTLY))

        // 对其他view测量，大小都一样，左闭右开
        val otherWidth = (curWidth * mOtherViewScanMinSize).toInt()
        val otherHeight = (curHeight * mOtherViewScanMinSize).toInt()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child == curView) continue
            child.measure(MeasureSpec.makeMeasureSpec(otherWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(otherHeight, MeasureSpec.EXACTLY))
        }

        setMeasuredDimension(width, height)
    }

    // 根据抛物线计算比例，y属于[0, 1]
    // 映射关系：(form - to, 0) -> (to - to, 1)，即(0, 0) -> (0, 1)
    @Suppress("SameParameterValue")
    private fun scanByParabola(from: Int, to: Int, cur: Int): Double {
        // 公式：val y = 1 - x.toDouble().pow(2.0)
        val x = (cur - from / to - from).toDouble()
        return 1 - x.pow(2.0)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // 对主内容view布局，放在正中心
        val curView = getChildAt(curIndex)
        curView.layout(
            (r - l) / 2 - curView.measuredWidth / 2,
            mPaddingValue,
            (r - l) / 2 + curView.measuredWidth / 2,
            mPaddingValue + curView.measuredHeight
        )

        // 对其他view进行布局，这里没考虑类似ViewPager的预加载问题，全部布局了
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child == curView) continue

            // 按间距排列
            val step = i - curIndex
            val stepLen = (r - l) / 4

            // 中间减去间距，再减去一半的宽度，得到左边坐标
            val left = (r - l) / 2 + stepLen * step - child.measuredWidth / 2
            val top = (b - t) / 2 + child.measuredHeight / 2
            val right = left + child.measuredWidth / 2
            val bottom = top + child.measuredHeight / 2

            child.layout(left, top, right, bottom)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        //super.onDraw(canvas)
        // 先绘制其他view
        for (i in 0 until childCount) {
            if (i != curIndex) getChildAt(i).draw(canvas)
        }

        // 对主内容view绘制
        getChildAt(curIndex).draw(canvas)
    }

    override fun onInterceptHoverEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when(ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    mLastX = ev.x
                    mLastY = ev.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dX = ev.x - mLastX
                    return checkScrollInView(scrollX + dX)
                }
                //MotionEvent.ACTION_UP -> {}
            }
        }
        return super.onInterceptHoverEvent(ev)
    }

    // 根据可以滚动的范围，计算是否可以滚动
    private fun checkScrollInView(length : Float): Boolean {
        val leftMexScrollLength = if (mRealLeft >= 0) 0 else abs(mRealLeft)
        val rightMaxScrollLength = if (mRealRight > measuredWidth) 0
            else mRealRight - measuredWidth
        return length >= -leftMexScrollLength && length <= rightMaxScrollLength
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when(ev.action) {
                // 防止点击空白位置或者子view未处理touch事件
                MotionEvent.ACTION_DOWN -> return true
                MotionEvent.ACTION_MOVE -> {
                    val dX = ev.x - mLastX
                    // 移动自身就可以
                    scrollTo((scrollX + dX).toInt(), scrollY)
                }
                MotionEvent.ACTION_UP -> {

                }
            }
        }
        return super.onTouchEvent(ev)
    }


    override fun computeScroll() {
        super.computeScroll()
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.currX, mScroller.currY)
            postInvalidate()
        }
    }
}