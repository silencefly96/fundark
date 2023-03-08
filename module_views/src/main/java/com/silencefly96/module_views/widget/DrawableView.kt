package com.silencefly96.module_views.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import java.io.*
import java.util.*
import kotlin.math.abs

interface IDrawableView {
    fun back()
    fun forward()
    fun clear()
    fun bitmap() : Bitmap

    @Throws(IOException::class)
    fun output(path: String)
}

@Suppress("RedundantVisibilityModifier")
class DrawableView : View, IDrawableView {

    private lateinit var mContext: Context

    //画笔宽度 px；
    public var mPaintWidth = 10f
        set(value) {
            field = value
            mPaint.strokeWidth = value
        }

    //画笔颜色
    public var mPaintColor: Int = Color.BLACK
        set(value) {
            field = value
            mPaint.color = value
        }

    //背景色
    public var mBackgroundColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            mCanvas.drawColor(value, PorterDuff.Mode.CLEAR)
            mCanvas.drawColor(value)
            var tmp = 0
            while (tmp <= index) {
                mCanvas.drawPath(pathList[tmp], mPaint)
                tmp++
            }
            invalidate()
        }

    //是否清除边缘空白
    public var isClearBlank: Boolean = false

    //手写画笔
    private val mPaint: Paint = Paint()

    //起点X
    private var mStartX = 0f

    //起点Y
    private var mStartY = 0f

    //路径
    private val mCurrentPath: Path = Path()

    //历史路径
    private val pathList = LinkedList<Path>()

    //当前操作位置
    private var index = -1

    //画布
    private lateinit var mCanvas: Canvas

    //生成的图片
    private lateinit var mBitmap: Bitmap

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)
    }

    private fun init(context: Context) {

        mContext = context
        //设置抗锯齿
        mPaint.isAntiAlias = true
        //设置签名笔画样式
        mPaint.style = Paint.Style.STROKE
        //设置笔画宽度
        mPaint.strokeWidth = mPaintWidth
        //设置签名颜色
        mPaint.color = mPaintColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        //创建画板bitmap
        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        //画板
        mCanvas = Canvas(mBitmap)
        //背景
        mCanvas.drawColor(mBackgroundColor)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        //画此次笔画之前的笔画
        canvas.drawBitmap(mBitmap, 0f, 0f, mPaint)
        //更新move过程中的笔画
        mCanvas.drawPath(mCurrentPath, mPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mStartX = event.x
                mStartY = event.y
                //画笔落笔起点
                mCurrentPath.moveTo(mStartX, mStartY)
            }

            MotionEvent.ACTION_MOVE -> {
                val previousX = mStartX
                val previousY = mStartY
                val dx = abs(event.x - previousX)
                val dy = abs(event.y - previousY)
                // 两点之间的距离大于等于3时，生成贝塞尔绘制曲线
                if (dx >= 3 || dy >= 3) {
                    // 设置贝塞尔曲线的操作点为起点和终点的一半
                    val cX = (event.x + previousX) / 2
                    val cY = (event.y + previousY) / 2
                    // 二阶贝塞尔，实现平滑曲线；previousX, previousY为操作点，cX, cY为终点
                    mCurrentPath.quadTo(previousX, previousY, cX, cY)
                    // 第二次执行时，第一次结束调用的坐标值将作为第二次调用的初始坐标值
                    mStartX = event.x
                    mStartY = event.y
                }
            }

            MotionEvent.ACTION_UP -> {
                //对当前笔画后的路径出栈
                var tmp = index + 1
                while (tmp < pathList.size) {
                    pathList.removeAt(tmp)
                    tmp++
                }

                //添加到历史笔画
                pathList.add(Path(mCurrentPath))
                index++

                //将路径画到bitmap中，即一次笔画完成才去更新bitmap，而手势轨迹是实时显示在画板上的。
                mCanvas.drawPath(mCurrentPath, mPaint)
                mCurrentPath.reset()
            }
        }

        // 更新绘制
        invalidate()
        return true
    }

    public override fun back() {
        if (index < 0) {
            Toast.makeText(mContext, "当前无旧操作可回退！", Toast.LENGTH_SHORT).show()
            return
        }

        //清空画布
        mCanvas.drawColor(mBackgroundColor, PorterDuff.Mode.CLEAR)
        mCanvas.drawColor(mBackgroundColor)

        //逐步添加路径，并绘制
        index--
        var tmp = 0
        while (tmp <= index) {
            mCanvas.drawPath(pathList[tmp], mPaint)
            tmp++
        }

        invalidate()
    }

    public override fun forward() {
        if (index >= pathList.size - 1) {
            Toast.makeText(mContext, "当前无旧操作可回退！", Toast.LENGTH_SHORT).show()
            return
        }

        //只需要画下一笔
        mCanvas.drawPath(pathList[++index], mPaint)

        invalidate()
    }

    /**
     * 清除画板
     */
    public override fun clear() {
        //更新画板信息
        mCanvas.drawColor(mBackgroundColor, PorterDuff.Mode.CLEAR)
        mCanvas.drawColor(mBackgroundColor)
        mPaint.color = mPaintColor
        pathList.clear()
        index = -1
        invalidate()
    }

    /**
     * 保存画板
     *
     */
    public override fun bitmap(): Bitmap {
        return mBitmap
    }

    /**
     * 保存画板
     * @param path       保存到路径
     *
     */
    @Throws(IOException::class)
    override fun output(path: String) {
        //配置是否去除边缘
        val bitmap = when(isClearBlank) {
            true -> clearBlank(mBitmap)
            false -> mBitmap
        }

        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        val buffer: ByteArray = bos.toByteArray()
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
        val outputStream: OutputStream = FileOutputStream(file)
        outputStream.write(buffer)
        outputStream.close()
    }

    /**
     * 逐行扫描 清楚边界空白。
     *
     * @param bitmap
     * @return
     */
    private fun clearBlank(bitmap: Bitmap): Bitmap {

        //扫描各边距不等于背景颜色的第一个点
        val top = getDifferentFromArray(0, bitmap.width, bitmap,
            0 until bitmap.height)

        var bottom = getDifferentFromArray(0, bitmap.width, bitmap,
            bitmap.height - 1 downTo 0)

        val left = getDifferentFromArray(1, bitmap.height, bitmap,
            0 until bitmap.width)

        var right = getDifferentFromArray(1, bitmap.height, bitmap,
            bitmap.width - 1 downTo 0)

        //防止创建null的bitmap  引发的崩溃
        if (left == 0 && top == 0 && right == 0 && bottom == 0) {
            right = 375
            bottom = 375
        }

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun getDifferentFromArray(type: Int, length: Int, bitmap: Bitmap, array: IntProgression): Int {
        val pixels = IntArray(length)
        for (i in array) {
            when(type) {
                //https://blog.csdn.net/tanmx219/article/details/81328315
                0 -> bitmap.getPixels(pixels, 0, length, 0, i, length, 1)  //获得一行
                1 -> bitmap.getPixels(pixels, 0, 1, i, 0, 1, length)  //获得一列
                else -> {}
            }

            for (j in pixels) {
                if (j != mBackgroundColor) {
                    return i
                }
            }
        }
        return 0
    }

}
