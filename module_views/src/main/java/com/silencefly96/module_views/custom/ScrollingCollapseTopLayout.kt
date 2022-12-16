@file:Suppress("unused")

package com.silencefly96.module_views.custom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

/**
 * 内容滚动造成header折叠的控件
 * 核心思想：
 * 1、两部分，一个header和一个可以滚动的区域
 * 2、header有两种状态，一个是完全展开状态，一个是折叠状态
 * 3、在滚动区域向下滚动的时候，header会先滚动到折叠状态，header折叠后滚动区域才开始滚动
 * 4、在滚动区域向上滚动的时候，滚动区域先滚动，滚动区域到顶了才开始展开header
 * 5、低仿CoordinatorLayout，滚动区域效果通过自定义layoutParas向header传递
 *
 * @author silence
 * @date 2022-10-17
 */
class ScrollingCollapseTopLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): ViewGroup(context, attributeSet, defStyleAttr) {

    //外部滑动距离
    private var mScrollHeight = 0f

    //上次纵坐标
    private var mLastY = 0f

    //当前控件宽高
    private var mHeight = 0
    private var mWidth = 0

    //两个部分
    private val header: Header = Header(context).apply {
        //设置header垂直方向，宽度铺满，高度自适应
        orientation = LinearLayout.VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    //NestedScrollView只允许一个子view(和ScrollView一样)，这里放一个垂直的LinearLayout
    private val scrollArea: NestedScrollView = NestedScrollView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(LinearLayout(context).apply {
            setBackgroundColor(Color.LTGRAY)
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        })
    }

    //XML里面的view
    private val xmlViews: ArrayList<View> = ArrayList()

    //获取XML内view结束，没执行onMeasure
    override fun onFinishInflate() {
        super.onFinishInflate()
        //在这里获得所有子view，拦截添加到scrollArea去
        if (xmlViews.size == 0) {
            forEach { view ->
                xmlViews.add(view)
            }
        }

        //更换view的节点
        removeAllViewsInLayout()
        addView(header)
        addView(scrollArea)
        //把当前控件全部view放到NestedScrollView内的LinearLayout内去
        (scrollArea.getChildAt(0) as ViewGroup).also { linear->
            for(view in xmlViews) {
                linear.addView(view)
            }
        }
    }

    //在onSizeChanged才能获得正确的宽高，会在onMeasure后得到，这里只是学一下
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mHeight = h
        mWidth = w
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        //测量header
        header.onScroll(mScrollHeight.toInt())
        header.measure(widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec),MeasureSpec.AT_MOST))
        //先measure一下获得实际高度，再减去滑动的距离，也可以把header.measuredHeight写成全局变量
        if (header.measuredHeight != 0) {
            val scrolledHeight = header.measuredHeight + mScrollHeight
            val headerHeightMeasureSpec = MeasureSpec.makeMeasureSpec(scrolledHeight.toInt(),
                MeasureSpec.getMode(MeasureSpec.EXACTLY))
            //再次测量的目的是后面滚动部分要占满剩余高度
            header.measure(widthMeasureSpec, headerHeightMeasureSpec)
        }

        //测量滑动区域
        val leftHeight = MeasureSpec.getSize(heightMeasureSpec) - header.measuredHeight
        scrollArea.measure(widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(leftHeight, MeasureSpec.EXACTLY))
        Log.e("TAG", "onMeasure: leftHeight=$leftHeight")
        Log.e("TAG", "onMeasure: scrollArea.height=${scrollArea.height}")
        Log.e("TAG", "onMeasure: scrollArea.measuredHeight=${scrollArea.measuredHeight}")

        //直接占满宽高
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        //简单布局下，上下两部分
        header.layout(l, t, r, t + header.measuredHeight)
        scrollArea.layout(l, t + header.measuredHeight, r,b)
    }

    //事件冲突使用外部拦截
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        var isIntercepted = false
        ev?.let {
            when(ev.action) {
                //不拦截down事件
                MotionEvent.ACTION_DOWN -> mLastY = ev.y
                MotionEvent.ACTION_MOVE -> {
                    val dY = ev.y - mLastY

                    //如果折叠了，优先滚动折叠栏
                    val canScrollTop = scrollArea.canScrollVertically(-1)
                    val canScrollBottom = scrollArea.canScrollVertically(1)

                    //可以滚动
                    isIntercepted = if (canScrollTop || canScrollBottom) {
                        //手指向上移动时，没折叠前要拦截
                        val scrollUp = dY < 0 &&
                                mScrollHeight + dY > -header.collapsingArea.height.toFloat()
                        //手指向下移动时，没展开前且到顶了要拦截
                        val scrollDown = dY > 0 &&
                                mScrollHeight + dY < 0f &&
                                !canScrollTop

                        scrollUp || scrollDown
                    }else {
                        //不能滚动
                        true
                    }
                }
                //不拦截up事件
                //MotionEvent.ACTION_UP ->
            }
        }
        return isIntercepted
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when(ev.action) {
                //MotionEvent.ACTION_DOWN ->
                MotionEvent.ACTION_MOVE -> {
                    //累加滑动值，请求重新布局
                    val dY = ev.y - mLastY
                    if (mScrollHeight + dY <= 0 &&
                        mScrollHeight + dY >= -header.collapsingArea.height) {
                            mScrollHeight += dY
                            requestLayout()
                    }
                    mLastY = ev.y
                }
                //MotionEvent.ACTION_UP ->
            }
        }
        return super.onTouchEvent(ev)
    }

    //这里就做一个简单的折叠header，
    @Suppress("MemberVisibilityCanBePrivate")
    inner class Header @JvmOverloads constructor(
        context: Context,
        attributeSet: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ): LinearLayout(context, attributeSet, defStyleAttr){

        //两个区域
        val defaultArea: TextView
        val collapsingArea: TextView

        init {
            //添加两个header区域
            defaultArea = makeTextView(context, "Default area", 80)
            collapsingArea = makeTextView(context, "Collapsing area", 300)
            addView(defaultArea)
            addView(collapsingArea)
        }

        //低配Behavior.onNestedPreScroll，这里就处理下ScrollingHideTopLayout传过来的距离
        @SuppressLint("SetTextI18n")
        fun onScroll(scrollHeight: Int) {
            val expandHeight = collapsingArea.height + scrollHeight
            //这里就改一下背景色的透明度吧
            if (abs(expandHeight) <= collapsingArea.height) {
                val alpha = expandHeight.toFloat() / collapsingArea.height * 255
                defaultArea.text = "Default area:${alpha.toInt()}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    collapsingArea.setBackgroundColor(Color.argb(alpha.toInt(),88,88,88))
                }
            }
        }

        //创建TextView
        private fun makeTextView(context: Context, textStr: String, height: Int): TextView {
            //简单点height和textSize应该用dp和sp的，前面文章有
            return TextView(context).apply {
                layoutParams =
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
                text = textStr
                gravity = Gravity.CENTER
                textSize = 13f
                setBackgroundColor(Color.GRAY)
            }
        }
    }
}