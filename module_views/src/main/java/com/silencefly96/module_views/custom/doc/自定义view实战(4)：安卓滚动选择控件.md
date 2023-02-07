##### 前言
上篇文章通过一个有header和footer的滚动控件（Viewgroup）学了下MeasureSpec、onMeasure以及onLayout，接下来就用一个滚动选择的控件（View）来学一下onDraw的使用，并且了解下在XML自定义控件参数。

### 需求
这里就是一个滚动选择文字的控件，还是挺常见的，之前用别人的，现在选择手撕一个，核心思想如下：
- 1、有三层不同大小及透明度的选项，选中项放在中间
- 2、接受一个列表的数据，静态时显示三个值，滚动时显示四个值
- 3、滑动会造成三个选项滚动，大小透明度发生变化，会有一个新的选项出现
- 4、滚动一定距离后，判定是否选中一个项目，并触发动画滚动到选定项


### 编写代码
老实说下面写的代码并不好，特别是TextItem的绘制，本来是可以通过一个数学函数，根据滑动距离来映射缩放比例及位置的，如有需要可以参考[这个控件](https://github.com/silencefly96/Utils/blob/master/utils/src/main/java/com/run/utils/view/PickerView.java)，忘了是几年前从哪里抄的了，里面对文字的控制写的很好。

下面是我手撕的代码：
```kotlin
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
import com.silencefly96.module_common.R
import kotlin.math.abs
import kotlin.math.min

/**
 * 滚动选择文字控件
 * 核心思想
 * 1、有三层不同大小及透明度的选项，选中项放在中间
 * 2、接受一个列表的数据，静态时显示三个值，滚动时显示四个值
 * 3、滑动会造成三个选项滚动，大小透明度发生变化，会有一个新的选项出现
 * 4、滚动一定距离后，判定是否选中一个项目，并触发动画滚动到选定项
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
            DEFAULT_MAIN_TRANSPARENCY)
        secondAlpha = attrArr.getInteger(R.styleable.ScrollSelectView_secondAlpha,
            DEFAULT_SECOND_TRANSPARENCY)

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
```


### 主要问题
##### 自定义XML参数
这个网上应该有很多教程了，主要就是要创建一个value里面的xml来定义属性，在XML使用的使用引入命名空间，并使用这些属性，最后在控件代码中读取参数值。下面是这个控件的自定义属性：
>res->value->scrolll_select_view_style.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <declare-styleable name="ScrollSelectView">
        <attr name="mainAlpha" format="integer"/>
        <attr name="secondAlpha" format="integer"/>
        <attr name="textScanSize" format="integer"/>
        <attr name="changeItemYCapacity" format="dimension"/>
        <attr name="afterUpAnimatorPeriod" format="integer"/>
        <attr name="mainItemPercent" format="fraction"/>
    </declare-styleable>
</resources>
```
这里有六个控制属性，mainAlpha和secondAlpha是设定中间和第二层文字的透明度，值为[0,255]；textScanSize是文字相对于文字框的缩放比例，比如设置为2的话文字大小就是文字框的一半；changeItemYCapacity是滑动导致item切换的距离，单位为dp，即手指滑过这么长的距离就切换了选中项；afterUpAnimatorPeriod是手指抬起时，恢复到默认状态或者跳转到指定状态的最大时间间隔，动画时间会根据滑动距离占changeItemYCapacity的比例计算得到；mainItemPercent是中间item占整个控件的高度比例，大小为[0,100]，第二层的item的高度为剩下高度的一半。

##### Paint的初始化
Paint的初始化也是一个老生常谈的问题了，不能在onDraw里面创建，因为onDraw会频繁被调用，同类对象也不应该在onDraw里面创建。

##### 控件的默认大小
自定义view需要在onMeasure设定默认大小，不然使用wrap_content的时候会出问题，前几篇文章和注释都写的很清楚了。

##### 和宽高有关的默认属性设置
所有和控件宽高有关的默认属性都需要在onMeasure中设置，另外TextItem的setup中用到了measuredWidth和measuredHeight，也需要在onMeasure后初始化，这里就在第一次绘制的时候初始化了。

##### 文字绘制到中心位置
通过paint要将文字绘制到中心位置，需要结合textAlign（Paint.Align.CENTER）和fontMetricsInt计算得到绘制的纵坐标位置。

##### 滑动逻辑处理
因为这个继承的是View，所以在onTouchEvent中消耗事件即可，一定要对ACTION_DOWN事件返回true。

移动时做了三步操作，一是判定是否能够移动，二是累加移动dy并触发重绘，三是判断滑动距离是否切换了选中值，达到切换条件时，应该修改选中的index、累加的滑动距离，并将各个item重置到原来的位置及状态（ps.好像就是改了个index。。。）。这里有点不太好理解，就是每当滑动达到切换条件后，就修改选中，重置各个item，逻辑上是一个跳跃性修改，但是对于draw来说，只是绘制的新的图像罢了。

##### 滑动结束后触发动画滚动到选定项
当滑动结束后应该将控件滑动到一个选中项，即让选中项放置到中间，有三种情况，一个是没有切换，一个是上一个index，一个是后一个index。这里利用了ValueAnimator去模拟滑动，首先计算到最终状态的滑动距离，然后从当前滑动距离不断更新，最后到达最终状态的滑动距离。只要更新累加的滑动值，触发重绘，在onDraw中会和move一样进行处理。最后在动画结束后，要和move一样切换选中值及一些其他操作。

##### 滑动导致的文字的绘制
文字的绘制我封装到了TextItem这个内部类里，还是比较复杂，可能有三层item吧，不应该搞太多的。

绘制逻辑主要分成两部分，一部分是静态的位置及状态，一部分是滑动导致的变化值计算。静态的位置及状态写在setup里面，还是比较简单的，就是根据type去确定。

滑动导致的变化值计算就复杂多了，主要就是根据滑动距离和滑动切换项目的门限值确定一个百分比delta，再根据delta去计算变换的属性值。实际上想简单点，不就是属性变大或者属性变小的问题么，首先根据delta的正负值确定滑动的方向，再通过滑动方向去确定该类型的item应该属性变大或者属性变小，一个一个去设置就行了，就是代码比较多而已。

写到这里我发现前面我说应该用一个数学函数去映射的，我这abs(delta)不就是一个映射函数么。。果然写代码和写文章更能让直接深刻理解内容。这里abs(delta)换成抛物线确实可能会好看些，有机会改改。

##### 使用
使用起来很简单，直接设定数据和初始index就可以，暂时没有动态切换，可以重写下setter函数，invalidate()就可以了。
>xml
```xml
    <com.silencefly96.module_common.view.ScrollSelectView
        android:id="@+id/hhView"
        android:layout_width="match_parent"
        android:layout_height="300dp"
        android:background="@color/teal_700"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent"/>
```
>code
```kotlin
        binding.hhView.mData = ArrayList<String>().apply{
            add("第一个")
            add("第二个")
            add("第三个")
            add("第四个")
            add("第五个")
        }
        binding.hhView.mCurrentIndex = 2
```

##### 范围限定偶尔不生效
实际运行起来我发现对范围限定偶尔会不生效，暂时还没有解决，应该是动画没有完成就滑动造成的，不太好办，当然这个控件主要目的是学习onDraw，写的这么复杂了，目的已经达到了，要学习绘制的话后面再写几个控件吧。