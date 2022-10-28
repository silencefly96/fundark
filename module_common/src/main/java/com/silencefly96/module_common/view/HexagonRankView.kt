@file:Suppress("unused")

package com.silencefly96.module_common.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
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
 * 5、单指滚动，双指移动，三指放大
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

    // 字体大小
    private val mTextSize: Float

    // 画笔粗细
    private val mStrokeWidth: Float

    // 填充透明度
    private val mFillAlpha: Int

    // 半径占边框中最小值的比例
    private val mRadiusPercent: Float

    // 各个点的半径
    private val mPointRadius: Float

    // 起始相位
    private val mStartPhase: Int

    // 文字距离顶点的值
    private val mTextMargin: Float

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

        mOutPointColor = typedArray.getColor(R.styleable.HexagonRankView_outPointColor,
            Color.BLACK)

        mOutLineColor = typedArray.getColor(R.styleable.HexagonRankView_outLineColor,
            Color.DKGRAY)

        mInPointColor = typedArray.getColor(R.styleable.HexagonRankView_inPointColor,
            Color.BLUE)

        mInLineColor =typedArray.getColor(R.styleable.HexagonRankView_inLineColor,
            Color.GREEN)

        mInFillColor = typedArray.getColor(R.styleable.HexagonRankView_inFillColor,
            Color.YELLOW)

        mDottedLineColor = typedArray.getColor(R.styleable.HexagonRankView_dottedLineColor,
            Color.LTGRAY)

        mTextSize = typedArray.getDimension(R.styleable.HexagonRankView_textSize, 40f)

        mStrokeWidth = typedArray.getDimension(R.styleable.HexagonRankView_strokeWidth, 5f)

        mFillAlpha = typedArray.getInt(R.styleable.HexagonRankView_fillAlpha, 50)

        mRadiusPercent = typedArray.getFraction(R.styleable.HexagonRankView_radiusPercent,
            1, 1, 0.8f)

        mPointRadius = typedArray.getDimension(R.styleable.HexagonRankView_pointRadius, 10f)

        mStartPhase = typedArray.getInt(R.styleable.HexagonRankView_startPhase, -90)

        mTextMargin = typedArray.getDimension(R.styleable.HexagonRankView_textMargin, 50f)

        typedArray.recycle()

        // 初始化画笔
        mPaint = Paint().apply {
            // 内容参考：https://blog.csdn.net/qq_27061049/article/details/102574020
            /******* 常用方法 *******/
            // 颜色
            color = Color.BLACK

            // 粗细，设置为0时无论怎么放大 都是1像素
            strokeWidth = mStrokeWidth

            // 透明度[0, 255]
            alpha = 255

            // 带透明度画笔
            setARGB(255, 255, 255,255)

            // 抗锯齿
            flags = Paint.ANTI_ALIAS_FLAG

            // 设置填充模式，FILL、STROKE、FILL_AND_STROKE(更大一些)
            style = Paint.Style.STROKE


            /******* 线条样式 *******/
            // 线条连接处样式，BEVEL(斜角)、MITER(平斜接)、ROUND(圆角)
            strokeJoin = Paint.Join.ROUND

            // 斜接模式延长线长度限制(MITER样式下)，miter = len / width = 1 / sin ( θ / 2 )
            // 默认值为4，越大角度越小，比这个角度的角度，交界地方的超长三角形会被截断移除
            strokeMiter = 4f

            // 落笔和结束时那点(point)的样式，BUTT(不添加)、ROUND(添加半圆)、SQUARE(添加矩形)
            strokeCap = Paint.Cap.ROUND

            // 设置路径效果：
            // 直线，segmentLength: 分段长度，deviation: 偏移距离
            // pathEffect = DiscretePathEffect(float segmentLength, float deviation)

            // 圆角，参数为连接处的半径
            // pathEffect = CornerPathEffect(20f)

            // 虚线，intervals：必须为偶数，用于控制显示和隐藏的长度; phase：相位
            // pathEffect = DashPathEffect(float intervals[], float phase)

            // 使用 path 绘制虚线，shapePath(构成shape的path)，advance(两个shape之间距离)，phase(相位)
            // 指定拐弯改变的时候 shape 的转换方式，TRANSLATE：位移、ROTATE：旋转、MORPH：变体(压缩变小)
            // pathEffect = PathDashPathEffect(shapePath, advance, phase, PathDashPathEffectStyle.TRANSLATE);

            // 设置线条随机偏移(变得乱七八糟)，segmentLength: 分段长度，deviation: 偏移距离
            // pathEffect = DiscretePathEffect(float segmentLength, float deviation)

            // 两种线条模式都执行(一条线变两条线)
            // pathEffect = SumPathEffect(dashEffect, discreteEffect)

            // 线条组合模式(一条线两种模式)
            // pathEffect = ComposePathEffect(dashEffect, discreteEffect)

            /******* 着色渐变及渲染 *******/
            // 渐变
            // LinearGradient 线性渐变
            // (x0，y0)(x1，y1) 两点确定线性方向，color0、color1渐变两颜色(在两点上), 渐变模式: Shader.TileMode.MIRROR
            // shader = LinearGradient(float x0, float y0, float x1, float y1, int color0, int color1, TileMode tile)

            // 三种颜色以上模式，positions：颜色的位置(比例)
            // shader = LinearGradient(float x0, float y0, float x1, float y1, int colors[], float positions[], TileMode tile)

            // RadialGradient 径向渐变(从圆心沿极径变化)
            // x、y：中心点坐标, radius：渐变半径, color0：起始颜色, color1：结束颜色, TileMode：渐变模式
            // shader = RadialGradient(float x, float y, float radius, int color0, int color1, TileMode tile)
            // 三种颜色以上模式，positions：颜色的位置(比例)
            // shader = RadialGradient(float x, float y, float radius, int colors[], float positions[], TileMode tile)

            // SweepGradient 扫描渐变(随角度变化颜色)
            // cx、cy：圆点坐标, color0：起始颜色, color1：结束颜色
            // shader = SweepGradient(float cx, float cy, int color0, int color1)
            // 多种颜色的扫描渐变, positions：颜色的位置（比例）
            // shader = SweepGradient(float cx, float cy, int colors[], float positions[])

            // BitmapShader 位图渐变(使用图片填充)
            // bitmap 位图，TileMode 横纵坐标上的模式
            // shader = BitmapShader(Bitmap bitmap, TileMode tileX, TileMode tileY)

            // Shader.TileMode 渐变模式
            // TileMode.CLAMP 模式不平铺
            // TileMode.REPEAT 模式表示平铺
            // TileMode.MIRROR 模式也表示平铺，但是交错的位图是彼此的镜像，方向相反

            // ComposeShader 混合渐变(对上面四种渐变进行混合)
            // 混合模式 PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            // ComposeShader(Shader shaderA, Shader shaderB, Xfermode mode)
            // 位图运算(16种): PorterDuff.Mode.XO
            // ComposeShader(Shader shaderA, Shader shaderB, Mode mode)


            /******* 颜色效果处理 *******/
            // LightingColorFilter 设定基本色素(过滤颜色), mul 用来和目标像素相乘，add 用来和目标像素相加
            // colorFilter = LightingColorFilter(0x00ffff, 0x000000); //去掉红色
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
        mRadius = ((if (mCenterX < mCenterY) mCenterX else mCenterY) * mRadiusPercent).toInt()
        
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
            angle = 60 * i + mStartPhase
            radians = Math.toRadians(angle.toDouble())

            // 计算横纵坐标
            data[i].x = (mRadius * cos(radians)).toFloat() + mCenterX
            data[i].y = (mRadius * sin(radians)).toFloat() + mCenterY

            // 计算分数对应的坐标
            val scan = data[i].rank / 100f
            data[i].curX = (mRadius * scan * cos(radians)).toFloat() + mCenterX
            data[i].curY = (mRadius * scan * sin(radians)).toFloat() + mCenterY
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

        // 绘制标题，在切线方向绘制
        // 顶点在最上面时,(60 * i + mStartPhase) -> (-90) => -90 - (60 * i + mStartPhase)
        val startAngle = -90 - mStartPhase
        canvas.rotate(startAngle.toFloat(), mCenterX.toFloat(), mCenterY.toFloat())

        mPaint.textAlign = Paint.Align.CENTER
        mPaint.textSize = mTextSize
        // 绘制字体要先设置为0
        mPaint.strokeWidth = 0f
        // 要画出切线效果，移动的是画布，每次在最上面横着画就行
        val x = mCenterX.toFloat()
        val y = mCenterY - mRadius - mTextMargin

        // 循环绘制
        for (i in 0..5) {
            // 绘制标题
            canvas.drawText(data[i].name, x, getBaseline(mPaint, y), mPaint)
            // 旋转60度画下一个
            canvas.rotate(-60f, mCenterX.toFloat(), mCenterY.toFloat())
        }
        // 绘制标题结束，恢复画布
        canvas.rotate(-startAngle.toFloat(), mCenterX.toFloat(), mCenterY.toFloat())

        // 循环绘制
        mPaint.color = mOutPointColor
        mPaint.strokeWidth = mStrokeWidth
        for (point in data) {
            // 绘制点
            canvas.drawCircle(point.x, point.y, mPointRadius, mPaint)
            // 绘制外边
            path.lineTo(point.x, point.y)
        }

        // 封闭
        path.close()
        mPaint.color = mOutLineColor
        canvas.drawPath(path, mPaint)
    }

    private fun getBaseline(paint: Paint, tempY: Float): Float {
        //绘制字体的参数，受字体大小样式影响
        val fmi = paint.fontMetricsInt
        //top为基线到字体上边框的距离（负数），bottom为基线到字体下边框的距离（正数）
        //基线中间点的y轴计算公式，即中心点加上字体高度的一半，基线中间点x就是中心点x
        return tempY - (fmi.top + fmi.bottom) / 2f
    }

    // 里面多边形
    private fun drawInner(canvas: Canvas) {
        // 里面多边形路径
        val path = Path()
        path.moveTo(data[0].curX, data[0].curY)

        // 循环绘制
        mPaint.color = mInPointColor
        for (point in data) {
            // 绘制点
            canvas.drawCircle(point.curX, point.curY, mPointRadius, mPaint)

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
        mPaint.alpha = mFillAlpha
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

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when(ev.action) {
            MotionEvent.ACTION_DOWN -> {

            }
            MotionEvent.ACTION_POINTER_DOWN -> {

            }
            MotionEvent.ACTION_MOVE -> {}
            MotionEvent.ACTION_UP -> {}
        }
        performClick()
        return super.onTouchEvent(ev)
    }

    override fun performClick(): Boolean {

        return super.performClick()
    }

    // 数据类，标题、分数、外边点坐标、分数点坐标
    data class PointInfo(var name: String, var rank: Int,
                         var x: Float = 0f, var y: Float = 0f,
                         var curX: Float = 0f, var curY: Float = 0f)
}