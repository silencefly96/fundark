package com.silencefly96.module_common.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Scroller
import android.widget.TextView
import androidx.core.view.forEach
import kotlin.math.min

/**
 * 有header和footer的滚动控件
 * 核心思想：
 * 1、由header、container、footer三部分组成
 * 2、滚动中间控件时，上面有内容时header不显示，下面有内容时footer不显示
 * 3、滑动到header和footer最大值时不能滑动，释放的时候需要回弹
 * 4、完全显示时隐藏footer
 *
 * @author silence
 * @date 2022-10-08
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class HeaderFooterView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0,
    var header: View? = null,
    var footer: View? = null
): ViewGroup(context, attributeSet, defStyleAttr){

    var onReachHeadListener: OnReachHeadListener? = null
    var onReachFootListener: OnReachFootListener? = null

    //上次事件的横坐标
    private var mLastY = 0f

    //总高度
    private var totalHeight = 0

    //是否全部显示
    private var isAllDisplay = false

    //流畅滑动
    private var mScroller = Scroller(context)

    init {
        //设置默认的Header、Footer，这里是从构造来的，如果外部设置需要另外处理
        header = header ?: makeTextView(context, "Header")
        footer = footer ?: makeTextView(context, "Footer")

        //添加对应控件
        addView(header, 0)

        //这里还没有加入XML中的控件
        //Log.e("TAG", "init: childCount=$childCount", )
        addView(footer, 1)
    }

    //创建默认的Header\Footer
    private fun makeTextView(context: Context, textStr: String): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp2px(context, 30f))
            text = textStr
            gravity = Gravity.CENTER
            textSize = sp2px(context, 13f).toFloat()
            setBackgroundColor(Color.GRAY)

            //不设置isClickable的话，点击该TextView会导致mFirstTouchTarget为null，
            //致使onInterceptTouchEvent不会被调用，只有ACTION_DOWN能被收到，其他事件都没有
            //因为事件序列中ACTION_DOWN没有被消耗（返回true），整个事件序列被丢弃了
            //如果XML内是TextView也会造成同样情况，
            isFocusable = true
            isClickable = true

        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        //父容器给当前控件的宽高，默认值尽量设大一点
        val width = getSizeFromMeasureSpec(1080, widthMeasureSpec)
        val height = getSizeFromMeasureSpec(2160, heightMeasureSpec)

        //对子控件进行测量
        forEach { child ->
            //宽度给定最大值
            val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST)
            //高度不限定
            val childHeightMeasureSpec
                = MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED)

            //进行测量，不测量的话measuredWidth和measuredHeight会为0
            child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
            //Log.e("TAG", "onMeasure: child.measuredWidth=${child.measuredWidth}")
            //Log.e("TAG", "onLayout: child.measuredHeight=${child.measuredHeight}")
        }
        //设置测量高度为父容器最大宽高
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec))
    }

    private fun getSizeFromMeasureSpec(defaultSize: Int, measureSpec: Int): Int {
        //获取MeasureSpec内模式和尺寸
        val mod = MeasureSpec.getMode(measureSpec)
        val size = MeasureSpec.getSize(measureSpec)

        return when (mod) {
            MeasureSpec.EXACTLY -> size
            MeasureSpec.AT_MOST -> min(defaultSize, size)
            else -> defaultSize //MeasureSpec.UNSPECIFIED
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var curHeight = 0
        //Log.e("TAG", "onLayout: childCount=${childCount}")
        forEach { child ->
            //footer最后处理
            if (indexOfChild(child) != 1) {
                //Log.e("TAG", "onLayout: child.measuredHeight=${child.measuredHeight}")
                child.layout(left, top + curHeight, right,
                    top + curHeight + child.measuredHeight)
                curHeight += child.measuredHeight
            }
        }

        //处理footer
        val footer = getChildAt(1)
        //完全显示内容时不加载footer，header不算入内容
        if (measuredHeight < curHeight - header!!.height) {
            //设置全部显示flag
            isAllDisplay = false

            footer.layout(left, top + curHeight, right,top + curHeight + footer.measuredHeight)
            curHeight += footer.measuredHeight
        }

        //布局完成，滚动一段距离，隐藏header
        scrollBy(0, header!!.height)

        //设置总高度
        totalHeight = curHeight
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        //Log.e("TAG", "onInterceptTouchEvent: ev=$ev")
        ev?.let {
            when(ev.action) {
                MotionEvent.ACTION_DOWN -> mLastY = ev.y
                MotionEvent.ACTION_MOVE -> return true
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        //Log.e("TAG", "onTouchEvent: height=$height, measuredHeight=$measuredHeight")
        ev?.let {
            when(ev.action) {
                MotionEvent.ACTION_MOVE -> moveView(ev)
                MotionEvent.ACTION_UP -> stopMove()
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun moveView(e: MotionEvent) {
        //Log.e("TAG", "moveView: height=$height, measuredHeight=$measuredHeight")
        val dy = mLastY - e.y
        //更新点击的纵坐标
        mLastY = e.y
        //纵坐标的可滑动范围，0 到 隐藏部分高度，全部显示内容时是header高度
        val scrollMax = if (isAllDisplay) {
            header!!.height
        }else {
            totalHeight - height
        }
        //限定滚动范围
        if ((scrollY + dy) <= scrollMax &&  (scrollY + dy) >= 0) {
            //触发移动
            scrollBy(0, dy.toInt())
        }
    }

    private fun stopMove() {
        //Log.e("TAG", "stopMove: height=$height, measuredHeight=$measuredHeight")
        //如果滑动到显示了header，就通过动画隐藏header，并触发到达顶部回调
        if (scrollY < header!!.height) {
            mScroller.startScroll(0, scrollY, 0, header!!.height - scrollY)
            onReachHeadListener?.onReachHead()
        }else if(!isAllDisplay && scrollY > (totalHeight - height - footer!!.height)) {
            //如果滑动到显示了footer，就通过动画隐藏footer，并触发到达底部回调
            mScroller.startScroll(0, scrollY,0,
                 (totalHeight - height- footer!!.height) - scrollY)
            onReachFootListener?.onReachFoot()
        }

        invalidate()
    }

    //流畅地滑动
    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.currX, mScroller.currY)
            postInvalidate()
        }
    }

    //单位转换
    @Suppress("SameParameterValue")
    private fun dp2px(context: Context, dpVal: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dpVal, context.resources
                .displayMetrics
        ).toInt()
    }

    @Suppress("SameParameterValue")
    private fun sp2px(context: Context, spVal: Float): Int {
        val fontScale = context.resources.displayMetrics.scaledDensity
        return (spVal * fontScale + 0.5f).toInt()
    }

    interface OnReachHeadListener{
        fun onReachHead()
    }

    interface OnReachFootListener{
        fun onReachFoot()
    }
}