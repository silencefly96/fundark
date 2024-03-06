package com.silencefly96.module_tech.drawable.animation

import android.content.Context
import android.graphics.Camera
import android.util.AttributeSet
import android.view.animation.Animation
import android.view.animation.Transformation

class Custom3dIkunAnimation @JvmOverloads constructor(
    val context: Context,
    attrs: AttributeSet? = null
) : Animation(context, attrs) {

    // 背景属性
    private var mWidth: Int = 0
    private var mHeight: Int = 0
    private var mDensity: Float = 1f
    private lateinit var mCamera: Camera

    override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
        super.initialize(width, height, parentWidth, parentHeight)
        mWidth = width
        mHeight = height
        mCamera = Camera()
        mDensity = context.resources.displayMetrics.density
    }

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
        super.applyTransformation(interpolatedTime, t)

        // 图形矩阵
        val matrix = t.matrix

        // 唱
        val toZ = -100 + 200 * interpolatedTime
        // 跳
        val toY = -50 + 100 * interpolatedTime
        // rip
        val toX = -100 + 200 * interpolatedTime
        // 篮球
        val toDegree = 0 + 360f * interpolatedTime

        // 保存状态
        mCamera.save()

        // 执行操作，只支持平移和旋转
        mCamera.translate(toX, toY, toZ)
        mCamera.rotateY(toDegree)
        // 取得变换后的矩阵
        mCamera.getMatrix(matrix)

        // 恢复camera状态
        mCamera.restore()

        // 修复旋转时弹出屏幕问题
        val mValues = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        matrix.getValues(mValues)
        mValues[6] = mValues[6] / mDensity
        matrix.setValues(mValues)

        // 这两行代码的目的是在旋转动画中将矩阵的原点（坐标系的原点）移动到旋转轴心点的位置，
        // 然后在旋转完成后将原点移回到原来的位置，以确保旋转动画的正确性。(缺一不可，前面改变轴点，后者稳定位置)
        //
        // pre是把数据矩阵放前面和变化矩阵相乘，preTranslate方法的效果是在应用其他变换之前改变坐标系的原点位置。
        matrix.preTranslate(-mWidth/2f, -mHeight/2f)
        // post是把数据矩阵放后面和变化矩阵相乘，postTranslate方法的效果是在应用其他变换之后改变坐标系的原点位置。
        matrix.postTranslate(mWidth/2f, mHeight/2f)
    }
}