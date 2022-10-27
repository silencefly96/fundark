@file:Suppress("unused")

package com.silencefly96.module_common.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.silencefly96.module_common.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * 六边形评分view
 *
 * 核心思想：
 * 1、六个顶点连成六边形作为边界，顶点上需要有字提示数据类型
 * 2、六个数据作为得分，在中心到顶点连线上，六个评分再围成六边形
 * 3、边界六边形为空心，内部六边形为实心
 * 4、中心和顶点用虚线连接，再内部有虚线构成参考六边形
 *
 * @author silence
 * @date 2022-10-26
 */
class HexagonRankView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attributeSet, defStyleAttr){

    /**
     * 六个数据
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val data = ArrayList<PointInfo>(6)

    // 边界六顶点颜色
    private val mOutPointColor: Int

    // 边界六边的颜色
    private val mOutLineColor: Int

    // 内部六顶点颜色
    private val mInPointColor: Int

    // 内部六顶点颜色
    private val mInLineColor: Int

    // 内部填充颜色
    private val mInFillColor: Int

    // 虚线颜色
    private val mDottedLineColor: Int

    // 画笔
    private val mPaint: Paint

    // 中点坐标
    private var mCenterX: Int = 0
    private var mCenterY: Int = 0

    // 六边形半径
    private var mRadius: Int = 0

    init {
        // 读取XML参数
        val typedArray =
            context.obtainStyledAttributes(attributeSet, R.styleable.HexagonRankView)

        mOutPointColor = typedArray.getColor(R.styleable.HexagonRankView_mOutPointColor,
            Color.BLACK)

        mOutLineColor = typedArray.getColor(R.styleable.HexagonRankView_mOutLineColor,
            Color.DKGRAY)

        mInPointColor = typedArray.getColor(R.styleable.HexagonRankView_mInPointColor,
            Color.BLUE)

        mInLineColor =typedArray.getColor(R.styleable.HexagonRankView_mInLineColor,
            Color.GREEN)

        mInFillColor = typedArray.getColor(R.styleable.HexagonRankView_mInFillColor,
            Color.YELLOW)

        mDottedLineColor = typedArray.getColor(R.styleable.HexagonRankView_mDottedLineColor,
            Color.LTGRAY)

        typedArray.recycle()

        // 初始化画笔
        mPaint = Paint().apply {
            // 抗锯齿
            flags = Paint.ANTI_ALIAS_FLAG
            // 设置填充模式
            style = Paint.Style.STROKE
            // 粗细
            strokeWidth = 3f
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // 自定义view要设置好默认大小
        val width = getDefaultSize(100, widthMeasureSpec)
        val height = getDefaultSize(100, heightMeasureSpec)

        // 由控件宽高获得中心点坐标
        mCenterX = width / 2
        mCenterY = height / 2

        // 半径,设置为最小宽度的80%
        mRadius = ((if (mCenterX < mCenterY) mCenterX else mCenterY) * 0.8f).toInt()
        
        // 计算数据坐标
        calculateLocation()

        setMeasuredDimension(width, height)
    }
    
    private fun calculateLocation() {
        // 以中点为圆心，每隔60度绘制一个顶点
        var angle: Int
        var radians: Double

        // 循环绘制
        for (i in 0..5) {
            angle = 60 * i - 90
            radians = Math.toRadians(angle.toDouble())

            // 计算横纵坐标
            data[i].x = (mRadius * cos(radians)).toFloat() + mCenterX
            data[i].y = (mRadius * sin(radians)).toFloat() + mCenterY

            // 计算分数对应的坐标
            val scan = data[i].rank / 100
            data[i].curX = (mRadius * scan * cos(radians)).toFloat()
            data[i].curY = (mRadius * scan * sin(radians)).toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 外面的边和顶点以及标题
        drawOuter(canvas)

        // 里面多边形
        drawInner(canvas)

        // 辅助的虚线
        drawDottedLine(canvas)
    }

    // 外面的顶点
    private fun drawOuter(canvas: Canvas) {
        // 外边路径
        val path = Path()
        path.moveTo(data[0].x, data[0].y)

        // 绘制标题
        for (point in data) {
            // 绘制标题
            mPaint.textAlign = Paint.Align.LEFT
            mPaint.textSize = 30f

            canvas.drawText(point.name, point.x + 30, point.y - 30, mPaint)
        }

        // 循环绘制
        mPaint.color = mOutPointColor
        for (point in data) {
            // 绘制点
            canvas.drawCircle(point.x, point.y, 5f, mPaint)
            // 绘制外边
            path.lineTo(point.x, point.y)
        }

        // 封闭
        path.close()
        mPaint.color = mOutLineColor
        canvas.drawPath(path, mPaint)
    }

    // 里面多边形
    private fun drawInner(canvas: Canvas) {
        // 里面多边形路径
        val path = Path()

        // 循环绘制
        mPaint.color = mInPointColor
        for (point in data) {
            // 绘制点
            canvas.drawCircle(point.curX, point.curY, 5f, mPaint)

            // 添加外边路径到path
            path.lineTo(point.curX, point.curY)
        }
        // 封闭
        path.close()

        // 绘制路径
        mPaint.color = mInLineColor
        canvas.drawPath(path, mPaint)

        // 绘制内部填充
        mPaint.color = mInFillColor
        mPaint.style = Paint.Style.FILL
        canvas.drawPath(path, mPaint)
        // 恢复style
        mPaint.style = Paint.Style.STROKE
    }

    // 辅助的虚线，这里将半径三等分，画三个虚线六边形
    private fun drawDottedLine(canvas: Canvas) {
        var x: Float
        var y: Float

        // 路径
        val path = Path()
        mPaint.color = mDottedLineColor
        val array = FloatArray(2)
        array[0] = 5f
        array[1] = 5f
        mPaint.pathEffect =
            DashPathEffect(array, 5f)

        // 两层层虚线六边形
        for (i in 1..2) {
            path.reset()

            // 循环一遍获得路径
            for (point in data) {
                // 利用两点坐标计算等距离的点
                x = (point.x - mCenterX) / 3 * i + mCenterX
                y = (point.y - mCenterY) / 3 * i + mCenterY

                if (data.indexOf(point) == 0) path.moveTo(x, y)
                path.lineTo(x, y)
            }
            // 封闭
            path.close()

            // 绘制虚线六边形
            canvas.drawPath(path, mPaint)
        }

        // 绘制连接中点和顶点的虚线
        path.reset()
        for (point in data) {
            canvas.drawLine(mCenterX.toFloat(), mCenterY.toFloat(), point.x, point.y, mPaint)
        }
    }

    // 数据类，标题、分数、外边点坐标、分数点坐标
    data class PointInfo(var name: String, var rank: Int,
                         var x: Float = 0f, var y: Float = 0f,
                         var curX: Float = 0f, var curY: Float = 0f)
}