@file:Suppress("MemberVisibilityCanBePrivate")

package com.silencefly96.module_views.game.airplane

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.RelativeLayout
import android.widget.TextView

class GameInfoView(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    // 生命值
    private val liveSizeTextView: TextView
    var liveSize: Int = 0
        @SuppressLint("SetTextI18n")
        set(value) {
            field = value
            liveSizeTextView.text = "生命值：$value"
        }

    // 得分
    private val scoreTextView: TextView
    var score: Int = 0
        @SuppressLint("SetTextI18n")
        set(value) {
            field = value
            scoreTextView.text = "得分：$value"
        }

    init {
        liveSizeTextView = makeTextView(context!!, liveSize.toString())
        var params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        params.leftMargin = dp2px(context, 20f)
        addView(liveSizeTextView, params)

        scoreTextView = makeTextView(context, score.toString())
        params = LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.MATCH_PARENT)
        // 向右边对齐
        params.addRule(ALIGN_PARENT_RIGHT, TRUE)
        params.rightMargin = dp2px(context, 20f)
        addView(scoreTextView, params)
    }

    private fun makeTextView(context: Context, textStr: String): TextView {
        return TextView(context).apply {
            text = textStr
            gravity = Gravity.CENTER
            textSize = sp2px(context, 15f).toFloat()
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = getDefaultSize(480, widthMeasureSpec)
        // 在顶部横向铺满
        setMeasuredDimension(width, dp2px(context, 50f))
    }

    fun dp2px(context: Context, dpVal: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dpVal, context.resources
                .displayMetrics
        ).toInt()
    }

    fun sp2px(context: Context, spVal: Float): Int {
        val fontScale = context.resources.displayMetrics.scaledDensity
        return (spVal * fontScale + 0.5f).toInt()
    }
}