@file:Suppress("unused")

package com.silencefly96.module_common.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.NestedScrollingParent
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
 */
class ScrollingCollapseTopLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): ViewGroup(context, attributeSet, defStyleAttr), NestedScrollingParent {

    //外部滑动距离
    private var mScrollHeight = 0f

    //两个部分
    private val header: Header = Header(context)

    //NestedScrollView只允许一个子view，这里放一个垂直的LinearLayout
    private val scrollArea: NestedScrollView = NestedScrollView(context).apply {
        layoutParams.width = LayoutParams.MATCH_PARENT
        layoutParams.height = LayoutParams.MATCH_PARENT
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams.width = LayoutParams.MATCH_PARENT
            layoutParams.height = LayoutParams.MATCH_PARENT
        })
    }

    init {
        addView(header)
        addView(scrollArea)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        //测量header
        header.onScroll(mScrollHeight.toInt())
        header.measure(widthMeasureSpec, heightMeasureSpec)

        //测量滑动区域
        //把当前控件全部view放到NestedScrollView内的LinearLayout内去
        (scrollArea.getChildAt(0) as ViewGroup).also { linear->
            forEach { child ->
                linear.addView(child)
            }
        }
        val leftHeight = MeasureSpec.getSize(heightMeasureSpec) - header.height
        scrollArea.measure(widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(leftHeight, MeasureSpec.EXACTLY))


        //直接占满宽高
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {

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
            //设置header垂直方向，宽度铺满，高度自适应
            orientation = VERTICAL
            layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }

            //添加两个header区域
            defaultArea = makeTextView(context, "Default area", 80)
            collapsingArea = makeTextView(context, "Collapsing area", 100)
            addView(defaultArea)
            addView(collapsingArea)
        }

        //低配Behavior.onNestedPreScroll，这里就处理下ScrollingHideTopLayout传过来的距离
        fun onScroll(scrollHeight: Int) {
            val expandHeight = collapsingArea.height - scrollHeight
            //这里就改一下背景色的透明度吧
            if (abs(expandHeight) <= collapsingArea.height) {
                val alpha = expandHeight.toFloat() / collapsingArea.height * 255
                defaultArea.setBackgroundColor(Color.alpha(alpha.toInt()))
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