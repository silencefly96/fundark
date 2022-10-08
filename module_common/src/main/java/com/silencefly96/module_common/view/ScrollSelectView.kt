package com.silencefly96.module_common.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.min

/**
 * 滚动选择文字控件
 */
class ScrollSelectView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attributeSet, defStyleAttr){

    //设置控件的默认大小，实际viewgroup不需要
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = getSizeFromMeasureSpec(360, widthMeasureSpec)
        val height = getSizeFromMeasureSpec(720, heightMeasureSpec)
        setMeasuredDimension(width, height);
    }

    //根据MeasureSpec确定默认宽高，MeasureSpec限定了该view可用的大小
    private fun getSizeFromMeasureSpec(defaultSize: Int, measureSpec: Int): Int {
        //获取MeasureSpec内模式和尺寸
        val mod = View.MeasureSpec.getMode(measureSpec)
        val size = View.MeasureSpec.getSize(measureSpec)

        return when (mod) {
            View.MeasureSpec.EXACTLY -> size
            View.MeasureSpec.AT_MOST -> min(defaultSize, size)
            else -> defaultSize //MeasureSpec.UNSPECIFIED
        }
    }
}