package com.silencefly96.module_common.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * 有header和footer的滚动控件
 * 核心思想：
 * 1、由header、container、footer三部分组成
 * 2、滚动中间控件时，上面有内容时header显示，下面有内容时footer显示
 * 3、完全可见的时候footer应该隐藏
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class HeaderFooterView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0,
    var header: View? = null,
    var container: View,
    var footer: View? = null
) : ViewGroup(context, attributeSet, defStyleAttr){

    //父容器的宽高，需要在onMeasure内获取
    private var mParentWidth: Int = 0
    private var mParentHeight: Int = 0

    init {
        //设置默认的Header、Footer
        header = header ?: makeTextView(context, "Header")
        footer = footer ?: makeTextView(context, "Footer")

        //添加对应控件
        addView(header)
        addView(container)
        addView(footer)

        //首先滚动一段距离，隐藏header
        scrollBy(0, header!!.height)
    }

    //创建默认的Header\Footer
    private fun makeTextView(context: Context, textStr: String): TextView {
        return TextView(context).apply {
            layoutParams.height = dp2px(context, 60f)
            layoutParams.width = LayoutParams.MATCH_PARENT
            text = textStr
            gravity = Gravity.CENTER
            textSize = sp2px(context,18f).toFloat()
            setBackgroundColor(Color.GRAY)
        }
    }

    //自定义view需要写出自身宽高
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        MeasureSpec.getMode(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    //ViewGroup为抽象类，必须实现onLayout方法
    override fun onLayout(p0: Boolean, p1: Int, p2: Int, p3: Int, p4: Int) {

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
}