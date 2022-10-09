@file:Suppress("unused")

package com.silencefly96.module_common.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec.*
import com.silencefly96.module_common.R
import kotlin.math.min

/**
 * 滚动选择文字控件
 * 核心思想
 * 1、有两层不同大小及透明度的选项，选中项放在中间
 * 2、接受一个列表的数据，最多显示三个值，三层五个值有点麻烦
 * 3、滑动会造成三个选项滚动，大小透明度发生变化
 * 4、滚动一定距离后，判定是否选中一个项目，并触发动画滚动到选定项
 * 5、尝试做出循环滚动效果
 */
class ScrollSelectView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attributeSet, defStyleAttr){

    //默认字体透明度，大小不应该指定，应该根据view的高度按百分比适应
    companion object{
        const val DEFAULT_BIG_TRANSPARENCY = 255
        const val DEFAULT_SMALL_TRANSPARENCY = (255 * 0.5f).toInt()
    }

    //两层字体大小及透明度
    private var mainSize: Float = 0f
    private var secondSize: Float = 0f

    private val mainAlpha: Int
    private val secondAlpha: Int

    //数据
    var mData: List<String>? = null
    //选择数据index
    var mCurrentIndex: Int = 0
    //单次事件序列累计滑动值
    private var mScrollY: Float = 0f
    //上次事件纵坐标
    private var mLastY: Float = 0f

    //画笔
    private val mPaint: Paint

    init {
        //读取XML参数，设置相关属性
        val attrArr = context.obtainStyledAttributes(attributeSet, R.styleable.ScrollSelectView)
        //三层字体透明度设置，未设置使用默认值
        mainAlpha = attrArr.getInteger(R.styleable.ScrollSelectView_mainAlpha,
            DEFAULT_BIG_TRANSPARENCY)
        secondAlpha = attrArr.getInteger(R.styleable.ScrollSelectView_secondAlpha,
            DEFAULT_SMALL_TRANSPARENCY)
        //回收
        attrArr.recycle()

        //设置画笔，在构造中初始化，不要写在onDraw里面，onDraw会不断触发
        mPaint = Paint().apply {
            flags = Paint.ANTI_ALIAS_FLAG
            style = Paint.Style.FILL
            //该方法即为设置基线上那个点究竟是left,center,还是right
            textAlign = Paint.Align.CENTER
            color = Color.BLACK
        }
    }

    //设置控件的默认大小，实际viewgroup不需要
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        //根据父容器给定大小，设置自身宽高，wrap_content需要用到默认值
        val width = getSizeFromMeasureSpec(300, widthMeasureSpec)
        val height = getSizeFromMeasureSpec(200, heightMeasureSpec)
        //设置测量宽高，一定要设置，不然错给你看
        setMeasuredDimension(width, height)

        //得到自身高度后，就可以按比例设置字体大小了
        setFontSize(height)
    }

    private fun setFontSize(totalHeight: Int) {
        //按6：3：1的比例设置字体大小
        mainSize = totalHeight * 6 / 10f
        secondSize = totalHeight / 10f
    }

    //根据MeasureSpec确定默认宽高，MeasureSpec限定了该view可用的大小
    private fun getSizeFromMeasureSpec(defaultSize: Int, measureSpec: Int): Int {
        //获取MeasureSpec内模式和尺寸
        val mod = getMode(measureSpec)
        val size = getSize(measureSpec)

        return when (mod) {
            EXACTLY -> size
            AT_MOST -> min(defaultSize, size)
            else -> defaultSize //MeasureSpec.UNSPECIFIED
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        //绘制中间项
        drawMainItem(mPaint, canvas)
        //绘制其他两项
        drawSecondItem(mPaint, canvas)
    }

    private fun drawMainItem(paint: Paint, canvas: Canvas?) {
        paint.textSize = mainSize
        paint.alpha = mainAlpha

        //中心点，但是绘制是从基线开始的，在中心点下方
        val x = measuredWidth / 2f
        val y = measuredHeight / 2f
        //绘制字体的参数，受字体大小样式影响
        val fmi = paint.fontMetricsInt
        //top为基线到字体上边框的距离（负数），bottom为基线到字体下边框的距离（正数）
        //基线中间点的y轴计算公式，即中心点加上字体高度的一半，基线中间点x就是中心点x
        val baseline = y - (fmi.top + fmi.bottom) / 2f
        val mainStr = if (mCurrentIndex - 1 < 0 || mCurrentIndex + 1 >= mData!!.size) ""
            else mData!![mCurrentIndex - 1]
        canvas?.drawText(mainStr, x, baseline, mPaint)
    }

    private fun drawSecondItem(paint: Paint, canvas: Canvas?) {
        paint.textSize = secondSize
        paint.alpha = secondAlpha

        //绘制上面项目
        //中心点，上面项目的高度占1/4，再求中心点y即是1/8
        var x = measuredWidth / 2f
        var y = measuredHeight / 8f
        val fmi = paint.fontMetricsInt
        var baseline = y - (fmi.top + fmi.bottom) / 2f
        val topStr = if (mCurrentIndex - 1 < 0) "" else mData!![mCurrentIndex - 1]
        canvas?.drawText(topStr, x, baseline, mPaint)

        //绘制下面项目
        x = measuredWidth / 2f
        y = measuredHeight  * 7 / 8f
        baseline = y - (fmi.top + fmi.bottom) / 2f
        val bottomStr = if (mCurrentIndex + 1 >= mData!!.size) "" else mData!![mCurrentIndex + 1]
        canvas?.drawText(bottomStr, x, baseline, mPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            when(event.action) {
                MotionEvent.ACTION_DOWN -> {
                    //按下开始计算滑动距离
                    mScrollY = 0f
                    mLastY = it.y
                }
                MotionEvent.ACTION_MOVE -> move(event)
                MotionEvent.ACTION_UP -> stopMove(event)
            }
        }
        //view是最末端了，应该拦截touch事件，不然事件序列将舍弃
        return true
    }

    private fun move(e: MotionEvent) {
        val dy = mLastY - e.y
        //累加滑动距离
        mScrollY += dy

        //计算得到偏移值和放大倍速

    }

    private fun stopMove(event: MotionEvent) {

    }
}