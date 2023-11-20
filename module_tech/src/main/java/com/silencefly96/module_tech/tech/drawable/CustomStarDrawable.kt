package com.silencefly96.module_tech.tech.drawable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class CustomStarDrawable: Drawable() {

    private val mPaint: Paint = Paint()

    init {
        mPaint.strokeWidth = 5f
        mPaint.flags = Paint.ANTI_ALIAS_FLAG
        mPaint.style = Paint.Style.FILL
        mPaint.color = Color.GRAY
    }

    override fun draw(canvas: Canvas) {
        // 绘制圆圈
        val num = level / 2000
        val radius = (bounds.bottom - bounds.top) / 4f
        val distance = (bounds.right - bounds.left) / 5f

        when(num) {
            1 -> mPaint.color = Color.RED
            2 -> mPaint.color = Color.YELLOW
            3 -> mPaint.color = Color.BLUE
            4 -> mPaint.color = Color.GREEN
            else -> mPaint.color = Color.GRAY
        }
        // canvas.drawRect(bounds, mPaint)
        for (i in 0 until  num) {
            canvas.drawCircle(distance * i + radius,  2 * radius, radius, mPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        mPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
    )
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }


    // 默认大小
    override fun getIntrinsicWidth(): Int {
        return 500
    }

    override fun getIntrinsicHeight(): Int {
        return 100
    }
}