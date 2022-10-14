@file:Suppress("unused")

package com.silencefly96.module_common.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
): ViewGroup(context, attributeSet, defStyleAttr) {

    init {
        addView(Header(context))
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
        fun onScroll(expandHeight: Int) {
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