@file:Suppress("unused")

package com.example.module_views.custom

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec.*
import androidx.core.animation.addListener
import com.example.module_views.R
import kotlin.math.abs
import kotlin.math.min

/**
 * 滚动选择文字控件
 * 核心思想
 * 1、有两层不同大小及透明度的选项，选中项放在中间
 * 2、接受一个列表的数据，最多显示三个值，三层五个值有点麻烦
 * 3、滑动会造成三个选项滚动，大小透明度发生变化
 * 4、滚动一定距离后，判定是否选中一个项目，并触发动画滚动到选定项
 * 5、尝试做出循环滚动效果
 *
 * @author silence
 * @date 2022-10-10
 */
class ScrollSelectView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attributeSet, defStyleAttr){

    //默认字体透明度，大小不应该指定，应该根据view的高度按百分比适应
    companion object{
        const val DEFAULT_MAIN_TRANSPARENCY = 255
        const val DEFAULT_SECOND_TRANSPARENCY = (255 * 0.5f).toInt()

        //三种item类型
        const val ITEM_TYPE_MAIN = 1
        const val ITEM_TYPE_SECOND = 2
        const val ITEM_TYPE_NEW = 3
    }

    //两层字体大小及透明度
    private var mainSize: Float = 0f
    private var secondSize: Float = 0f

    private val mainAlpha: Int
    private val secondAlpha: Int

    //主次item高度，由主item所占比例决定
    private val mainItemPercent: Float
    private var mainHeight: Float = 0f
    private var secondHeight: Float = 0f


    //字体相对于框的缩放比例
    private val textScanSize: Int

    //切换项目的y轴滑动距离门限值
    private var itemChangeYCapacity: Float

    //释放滑动动画效果间隔
    private var afterUpAnimatorPeriod: Int

    //数据
    @Suppress("MemberVisibilityCanBePrivate")
    var mData: List<String>? = null

    //选择数据index
    @Suppress("MemberVisibilityCanBePrivate")
    var mCurrentIndex: Int = 0

    //绘制的item列表
    private var mItemList: MutableList<TextItem> = ArrayList()

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
            DEFAULT_MAIN_TRANSPARENCY
        )
        secondAlpha = attrArr.getInteger(R.styleable.ScrollSelectView_secondAlpha,
            DEFAULT_SECOND_TRANSPARENCY
        )

        textScanSize = attrArr.getInteger(R.styleable.ScrollSelectView_textScanSize, 2)
        //取到的值限定为dp值，需要转换
        itemChangeYCapacity =
            attrArr.getDimension(R.styleable.ScrollSelectView_changeItemYCapacity, 0f)
        itemChangeYCapacity = dp2px(context, itemChangeYCapacity).toFloat()

        afterUpAnimatorPeriod =
            attrArr.getInteger(R.styleable.ScrollSelectView_afterUpAnimatorPeriod, 300)

        //获取主item所占比例，在onMeasure中计算得到主次item高度
        mainItemPercent = attrArr.getFraction(
            R.styleable.ScrollSelectView_mainItemPercent, 1,1,0.5f)

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

        //创建四个TextItem
        mItemList.apply {
            add(TextItem(ITEM_TYPE_SECOND, true))
            add(TextItem(ITEM_TYPE_MAIN))
            add(TextItem(ITEM_TYPE_SECOND))
            add(TextItem(ITEM_TYPE_NEW))
        }
    }

    //设置控件的默认大小，实际viewgroup不需要
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        //根据父容器给定大小，设置自身宽高，wrap_content需要用到默认值
        val width = getSizeFromMeasureSpec(300, widthMeasureSpec)
        val height = getSizeFromMeasureSpec(200, heightMeasureSpec)
        //Log.e("TAG", "onMeasure: height=$height")
        //设置测量宽高，一定要设置，不然错给你看
        setMeasuredDimension(width, height)

        //如果滑动距离门限值没有确定，应该根据view大小设定，默认高度的二分之一
        itemChangeYCapacity = if(itemChangeYCapacity == 0f) height / 2f
            else itemChangeYCapacity

        //有比例计算主次item高度
        mainHeight = height * mainItemPercent
        secondHeight = height * (1 - mainItemPercent) / 2

        //得到自身高度后，就可以按比例设置字体大小了
        mainSize = mainHeight / textScanSize
        secondSize = secondHeight / textScanSize
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
        //绘制显示的text，最多同时显示4个
        //Log.e("TAG", "onDraw: mScrollY=$mScrollY")
        for (item in mItemList) {
            item.draw(mScrollY / itemChangeYCapacity ,mPaint, canvas)
        }
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
                MotionEvent.ACTION_UP -> stopMove()
            }
        }
        //view是最末端了，应该拦截touch事件，不然事件序列将舍弃
        return true
    }

    private fun move(e: MotionEvent) {
        val dy = mLastY - e.y
        //更新mLastY值
        mLastY = e.y

        //设置滑动范围，到达顶部不能下拉，到达底部不能上拉
        if (dy == 0f) return    //为什么会有两次0？？？
        if (dy < 0 && mCurrentIndex - 1 == 0) return
        if (dy > 0 && mCurrentIndex + 1 == mData!!.size - 1) return

        //累加滑动距离
        mScrollY += dy
        //如果滑动距离切换了选中值，重绘前修改选中值
        if (mScrollY >= itemChangeYCapacity) changeItem(mCurrentIndex + 1)
        else if (mScrollY <= -itemChangeYCapacity) changeItem(mCurrentIndex - 1)
        //滑动后触发重绘，绘制时处理滑动效果
        //Log.e("TAG", "move: mScrollY=$mScrollY")
        invalidate()
    }

    //统一修改mCurrentIndex，各个TextItem需要复位
    private fun changeItem(index: Int) {
        mCurrentIndex = index
        //消耗滑动距离
        mScrollY = 0f

        //对各个TextItem复位，重新调用setup即可
        for (item in mItemList) {
            item.setup()
        }
    }

    private fun stopMove() {
        //结束滑动后判定，滑动距离超过itemChangeYCapacity一半就切换了选中项
        val terminalScrollY: Float = when {
            mScrollY > itemChangeYCapacity / 2f -> itemChangeYCapacity
            mScrollY < -itemChangeYCapacity / 2f -> -itemChangeYCapacity
            //滑动没有达到切换选中项效果，应该恢复到原先状态
            else -> 0f
        }

        //这里使用ValueAnimator处理剩余的距离，模拟滑动到需要的位置
        val animator = ValueAnimator.ofFloat(mScrollY, terminalScrollY)
        //Log.e("TAG", "stopMove: mScrollY=$mScrollY, terminalScrollY=$terminalScrollY")
        animator.addUpdateListener { animation ->
            //Log.e("TAG", "stopMove: " + animation.animatedValue as Float)
            mScrollY = animation.animatedValue as Float
            invalidate()
        }

        //动画结束时要更新选中的项目
        animator.addListener (onEnd = {
            if (mScrollY == itemChangeYCapacity) changeItem(mCurrentIndex + 1)
            else if (mScrollY == -itemChangeYCapacity) changeItem(mCurrentIndex - 1)
        })

        //滑动动画总时间应该和距离有关
        val percent = terminalScrollY / (itemChangeYCapacity / 2f)
        animator.duration = (afterUpAnimatorPeriod * abs(percent)).toLong()
        //animator.duration = afterUpAnimatorPeriod.toLong()
        animator.start()
    }

    //单位转换
    @Suppress("SameParameterValue")
    private fun dp2px(context: Context, dpVal: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dpVal, context.resources
                .displayMetrics
        ).toInt()
    }

    //依赖于控件宽高及数据，需要在onMeasure之后初始化一下
    inner class TextItem(
        private val type: Int,
        //上下两个次item移动时是对称的，要创建时确定
        private val isTopSecond: Boolean = false
    ){
        private var index: Int = 0
        private var x: Float = 0f
        private var y: Float = 0f
        private var textSize: Float = 0f
        private var alpha: Int = 0
        private var height: Float = 0f
        private var dSize = 0f
        private var dAlpha = 0
        private var dY = 0f

        private var isInit = false
        fun setup() {
            //x为中心即可
            x = measuredWidth / 2f

            //根据类型设置属性
            when(type) {
                ITEM_TYPE_MAIN -> {
                    index = mCurrentIndex
                    y = measuredHeight / 2f
                    textSize = mainSize
                    alpha = mainAlpha
                    height = mainHeight
                }
                ITEM_TYPE_SECOND -> {
                    index = if (isTopSecond) mCurrentIndex - 1
                        else mCurrentIndex + 1
                    y = if (isTopSecond) secondHeight / 2f
                        else measuredHeight - secondHeight / 2f
                    textSize = secondSize
                    alpha = secondAlpha
                    height = secondHeight
                }
                else -> {
                    index = mCurrentIndex + 2
                    //初始化时未确定位置
                    y = 0F
                    textSize = 0f
                    alpha = 0
                    height = 0f
                }
            }
        }

        private fun calculate(delta: Float) {
            //根据类型得到变化值
            when(type) {
                ITEM_TYPE_MAIN -> {
                    //无论向那边移动都应该是变小
                    dSize = (mainSize - secondSize) * -abs(delta)
                    dAlpha = ((mainAlpha - secondAlpha) * -abs(delta)).toInt()
                    //主次item中线之间的距离，delta>0页面上移，y减小
                    dY = (mainHeight + secondHeight) / 2f * -delta
                }
                ITEM_TYPE_SECOND -> {
                    //以上面为准，下面对称即可
                    if (isTopSecond && delta > 0 || !isTopSecond && delta < 0) {
                        //项目变为消失，值变小
                        dSize = secondSize * -abs(delta)
                        dAlpha = (secondAlpha * -abs(delta)).toInt()
                        //消失的高度为次item的高度一半
                        //上面item消失时y减小，下面item消失时y增加
                        dY = secondHeight / 2f *
                                if (isTopSecond) -abs(delta) else abs(delta)
                    }else {
                        //项目变为选中，值变大
                        dSize = (mainSize - secondSize) * abs(delta)
                        dAlpha = ((mainAlpha - secondAlpha) * abs(delta)).toInt()
                        //上面item变为选中时y增加，下面item变为选中时y减小
                        dY = (mainHeight + secondHeight) / 2f *
                                if (isTopSecond) abs(delta) else -abs(delta)
                    }
                }
                else -> {
                    //新项目终态就是次item，无论怎么移动值都是变大的
                    dSize = secondSize * abs(delta)
                    dAlpha = (secondAlpha * abs(delta)).toInt()
                    //从边沿移动到次item位置，即次item高度一半
                    //delta>0从下面出现，y应该变小，delta<0从上面出现，y变大
                    dY = secondHeight / 2f * -delta

                    //移动时才确定新item的y和index
                    y = if (delta > 0) measuredHeight.toFloat() else 0f
                    index = if (delta > 0) mCurrentIndex + 2 else mCurrentIndex - 2
                }
            }
        }

        fun draw(delta: Float, paint: Paint, canvas: Canvas?) {
            //确保在onMeasure后初始化
            if (!isInit) {
                setup()
                isInit = true
            }

            //计算属性变化
            calculate(delta)

            //修改画笔并绘制，注意变化的值都是相对于原来的值，不要去修改原来的值
            paint.textSize = textSize + dSize
            paint.alpha = alpha + dAlpha
            canvas?.drawText(getText(), x, getBaseline(paint, y + dY), paint)
        }

        private fun getText(): String {
            //判定范围
            if (index < 0 || index >= mData!!.size) return ""
            return mData!![index]
        }

        private fun getBaseline(paint: Paint, tempY: Float): Float {
            //绘制字体的参数，受字体大小样式影响
            val fmi = paint.fontMetricsInt
            //top为基线到字体上边框的距离（负数），bottom为基线到字体下边框的距离（正数）
            //基线中间点的y轴计算公式，即中心点加上字体高度的一半，基线中间点x就是中心点x
            return tempY - (fmi.top + fmi.bottom) / 2f
        }
    }
}