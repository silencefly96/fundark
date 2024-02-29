##### 前言
上一篇做了一个简单的六边形评分控件，主要对paint的api熟悉了一下，本来还想对六边形控件加入放大旋转的功能，但是paint的api内容够多了，就算了。今天把上一篇的放大旋转功能加到了这篇文章的扇形图里面，对安卓的多点触控学习了一下。

### 需求
这里就是用传入的数据画一个扇形图，但是画完扇形图后，能够支持多点触控，使用一只手指滑动时触发旋转，两只手指时触发缩放，三指手指时触发移动，四只手指以上时触发重置。核心思想如下：
- 1、外面传入数据，实现扇形图展示
- 2、扇形图能够单指旋转、二指放大、三指移动，四指以上同时按下进行复位
- 3、旋转、放大、平移效果能够叠加

### 效果图
这里效果图并不是很好，因为我这手机是淘宝买的屏幕自己换的，会断触。而且我发现多个手指这样配合功能上也是有冲突的，多个手指逐步离开屏幕时会到不同的状态，任何微小的移动，都会触发MOVE事件，进而造成不正常的变化，不知道其他有这种功能的控件怎么处理的，等有时间得研究研究。
![图片](https://img-blog.csdnimg.cn/9156e276a6b1460795bbdb3fb301b1f4.gif#pic_center)


### 代码
```kotlin
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * 多点触控饼状图
 *
 * @author silence
 * @date 2022-11-04
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attributeSet, defStyleAttr) {

    companion object{
        // 没有手指按下状态
        const val STATE_NORMAL = 0
        // 单指按下，进行旋转
        const val STATE_ROTATING = 1
        // 双指按下，进行缩放
        const val STATE_SCALING = 2
        // 三指按下，进行移动
        const val STATE_MOVING = 3
        // 三指以上按下，进行复位
        const val STATE_RESETTING = 4
    }

    /**
     * 数据，所占比例根据单位占总数计算
     */
    var data: MutableList<Int>? = null
    set(value) {
        field = value
        calculatePercent()
    }

    // 用于图表绘制的数据
    private val mPieData: MutableList<Triple<Int, Float, Int>> = ArrayList()

    // 半径占最小边框的比例
    private val mRadiusPercent = 0.8f

    // 画笔
    private val mPaint: Paint = Paint().apply {
        // 颜色
        color = Color.BLACK
        // 粗细，设置为0时无论怎么放大 都是1像素
        strokeWidth = 5f
        // 抗锯齿
        flags = Paint.ANTI_ALIAS_FLAG
        // 填充模式
        style = Paint.Style.FILL
    }

    // 矩形, 绘制弧形需要用到
    private var mRectF: RectF = RectF()

    // 中点坐标
    private var mCenterX: Int = 0
    private var mCenterY: Int = 0

    // 圆的半径
    private var mRadius: Float = 0f

    // 状态
    private var mState = STATE_NORMAL

    // 单指情况
    // 开始坐标
    private var mLastX = 0f
    private var mLastY = 0f
    private var mDegree = 0f
    private var mCountDegree = 0f

    // 双指情况
    // 第二个手指按下时两指间的距离，即初始距离，放大比例以此为基准
    private var mFirstDistance = 1f
    private var mCurrentDistance = 1f

    // 三指情况
    // 上一次三点中心
    private var mLastTripleCenterX = 0f
    private var mLastTripleCenterY = 0f
    // 本次移动值
    private var mMoveX = 0f
    private var mMoveY = 0f
    // 累计移动值
    private var mCountMoveX = 0f
    private var mCountMoveY = 0f


    // 计算比例
    private fun calculatePercent() {
        var count = 0
        val colors = arrayOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN)
        // 计算总数
        for (i in data!!) {
            count += i
        }
        // 填入比例
        var color = colors[0]
        var lastColor = colors[1]
        for (i in data!!) {
            // 避免相邻颜色相同
            while (color == lastColor) {
                color = colors[(Math.random() * colors.size).toInt()]
            }
            mPieData.add(Triple(i, i / count.toFloat(), color))
            lastColor = color
        }

        Log.e("TAG", "calculatePercent: mPieData=$mPieData")
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
        mRadius = (if (mCenterX < mCenterY) mCenterX else mCenterY) * mRadiusPercent

        // 绘制的矩形
        mRectF.set(mCenterX - mRadius, mCenterY - mRadius,
            mCenterX + mRadius, mCenterY + mRadius)

        setMeasuredDimension(width, height)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when(ev.actionMasked) {
            // 第一个点按下
            MotionEvent.ACTION_DOWN -> {
                //Log.e("TAG", "ACTION_DOWN --> 1")
                // 一只手指时旋转
                mState = STATE_ROTATING
                mLastX = ev.x
                mLastY = ev.y
            }
            // 第二个或者以上的点按下
            MotionEvent.ACTION_POINTER_DOWN -> {
                when (ev.pointerCount) {
                    2 -> {
                        //Log.e("TAG", "ACTION_POINTER_DOWN --> 2")
                        mState = STATE_SCALING
                        // 两指时计算初始距离
                        mFirstDistance = getDistance(
                            ev.getX(0), ev.getY(0),
                            ev.getX(1), ev.getY(1))
                        mCurrentDistance = mFirstDistance
                    }
                    3 -> {
                        Log.e("TAG", "ACTION_POINTER_DOWN --> 3")
                        mState = STATE_MOVING
                        // 三指时计算三点的中心
                        val pair = getTripleCenter(
                            ev.getX(0), ev.getY(0),
                            ev.getX(1), ev.getY(1),
                            ev.getX(2), ev.getY(2))
                        mLastTripleCenterX = pair.first
                        mLastTripleCenterY = pair.second
                        Log.e("TAG", "ACTION_POINTER_DOWN --> ($mLastTripleCenterX, $mLastTripleCenterY)")
                    }
                    else -> {
                        //Log.e("TAG", "ACTION_POINTER_DOWN --> 4+")
                        mState = STATE_RESETTING
                        // 更改状态，刷新即可
                        invalidate()
                    }
                }
            }
            // 所有的点移动
            MotionEvent.ACTION_MOVE -> {
                // 移动时处理，如果需要跟踪手指，需要用到actionIndex、PointerId、PointerIndex
                if (ev.pointerCount == 1) {
                    //Log.e("TAG", "ACTION_MOVE --> 1")
                    // 当前点和上一次的点计算角度
                    mDegree = getDegree(mLastX, mLastY, ev.x, ev.y).toFloat()
                    mLastX = ev.x
                    mLastY = ev.y
                    invalidate()
                }else if (ev.pointerCount == 2) {
                    //Log.e("TAG", "ACTION_MOVE --> 2")
                    val newDistance = getDistance(
                        ev.getX(0), ev.getY(0),
                        ev.getX(1), ev.getY(1))

                    // 缩小放大，限定些范围减少调用，不用touchSlop值太小了
                    if (abs(mCurrentDistance - newDistance) > 5) {
                        // 更新
                        mCurrentDistance = newDistance
                        // 不使用scaleX和scaleY，直接onDraw里面自己处理
                        invalidate()
                    }
                }else if(ev.pointerCount == 3) {
                    //Log.e("TAG", "ACTION_MOVE --> 3")
                    // 三指时计算三点的中心
                    val pair = getTripleCenter(
                        ev.getX(0), ev.getY(0),
                        ev.getX(1), ev.getY(1),
                        ev.getX(2), ev.getY(2))

                    mMoveX = pair.first - mLastTripleCenterX
                    mMoveY = pair.second - mLastTripleCenterY
                    // 限定移动范围
                    val maxLengthX = width / 2f - mRadius
                    val maxLengthY = height / 2f - mRadius
                    if ((mCountMoveX + mMoveX) >= -maxLengthX ||
                        (mCountMoveX + mMoveX) <= maxLengthX ||
                        (mCountMoveY + mMoveY) >= -maxLengthY ||
                        (mCountMoveY + mMoveY) <= maxLengthY) {
                        mLastTripleCenterX = pair.first
                        mLastTripleCenterY = pair.second
                        //Log.e("TAG", "ACTION_POINTER_DOWN --> ($mLastTripleCenterX, $mLastTripleCenterY)")
                        invalidate()
                    }else{
                        mMoveX = 0f
                        mMoveY = 0f
                    }
                }
            }
            // 非最后一个点抬起
            MotionEvent.ACTION_POINTER_UP -> {
                //Log.e("TAG", "ACTION_POINTER_UP --> ${ev.pointerCount}")
                // 经过测试，手指抬起时还未更改pointerCount
                when (ev.pointerCount) {
                    2 -> mState = STATE_ROTATING
                    3 -> mState = STATE_SCALING
                    4 -> mState = STATE_MOVING
                }
            }
            // 最后一个点抬起
            MotionEvent.ACTION_UP -> {
                //Log.e("TAG", "ACTION_UP --> 1")
                mState = STATE_NORMAL
            }
        }

        // 注意拦截事件，至少拦截ACTION_DOWN
        return true
    }

    private fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        // 平方和公式
        @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
        return sqrt((Math.pow((x1 - x2).toDouble(), 2.0)
                + Math.pow((y1 - y2).toDouble(), 2.0)).toFloat())
    }

    private fun getTripleCenter(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float)
        : Pair<Float, Float> {
        val x = (x1 + x2 + x3) / 3
        val y = (y1 + y2 + y3) / 3
        return Pair(x, y)
    }

    private fun getDegree(x1: Float, y1: Float, x2: Float, y2: Float): Double {
        // 带上移动偏移量的圆心
        val centerX = mCenterX + mCountMoveX
        val centerY = mCenterY + mCountMoveY

        // 起点角度
        val radians1 = atan2(y1 - centerY, x1 - centerX).toDouble()
        // 终点角度
        val radians2 = atan2(y2 - centerY, x2 - centerX).toDouble()
        // 从弧度转换成角度
        // Log.e("TAG", "getDegree: $degree")
        return Math.toDegrees(radians2 - radians1)
    }

    override fun onDraw(canvas: Canvas) {
        // 混合效果
        if (mState != STATE_RESETTING) {
            // 对canvas进行旋转，注意每次canvas都会复位，所以要用累加值
            if (mState == STATE_ROTATING) {
                mCountDegree += mDegree
            }
            canvas.rotate(mCountDegree, mCenterX.toFloat() + mCountMoveX,
                mCenterY.toFloat() + mCountMoveY)


            // 对canvas进行缩放
            val scale = mCurrentDistance / mFirstDistance
            canvas.scale(scale, scale, mCenterX.toFloat(), mCenterY.toFloat())


            // 对canvas进行移动
            if(mState == STATE_MOVING) {
                mCountMoveX += mMoveX
                mCountMoveY += mMoveY
            }
            canvas.translate(mCountMoveX, mCountMoveY)
        }else {
            // 重置
            mCountMoveX = 0f
            mCurrentDistance = mFirstDistance
            mCountMoveX = 0f
            mCountMoveY = 0f
        }

        // 绘制圆弧
        var angleCount = 0f
        for (peer in mPieData) {
            val angle = 360 * peer.second
            mPaint.color = peer.third
            canvas.drawArc(mRectF, angleCount, angle, true, mPaint)
            angleCount += angle
        }
    }
}
```

### 主要问题
扇形图的功能就没什么好说的了，下面主要讲讲多点触控以及绘制的问题。

##### MotionEvent的action和actionMasked
在处理TouchEvent的时候，我们一般时通过MotionEvent的action来判断事件类型，可是使用这种方法获得的事件类型只有DOWN、MOVE、UP三种事件，当需要多点触控的时候就不能用action来判断了，而是应该用actionMasked。使用actionMasked才能拿到ACTION_POINTER_DOWN和ACTION_POINTER_UP事件。

下面是两种方法get的源码：
```
    public final int getAction() {
        return nativeGetAction(mNativePtr);
    }

    public final int getActionMasked() {
        return nativeGetAction(mNativePtr) & ACTION_MASK;
    }
```

##### ACTION_DOWN和ACTION_POINTER_DOWN
在使用actionMasked后，我们就能拿到ACTION_POINTER_DOWN事件，这个事件是在第二个或者以上的手指按下的时候才触发。但是第一个手指触发还是在ACTION_DOWN事件，所以处理多个手指时，还得在两个事件中都要处理下。

##### ACTION_MOVE
多点触控的按下事件和抬起事件都会另外处理，但是ACTION_MOVE是不会另外处理的，所有的MOVE事件都会在ACTION_MOVE中触发。

##### MotionEvent的pointerCount
由于上面ACTION_MOVE的原因，我们如何在其中判断是几个手指呢？这里就要用到pointerCount了，它能够获得当前按下手指的个数，所以当你不需要跟踪手指，只需要判断有几个的问题时，用它就可以了，如果还要跟踪手指那就要看下面的几个参数了。

##### actionIndex、PointerId、PointerIndex
这里可以看看我上一篇转载的文章：[Android多点触控详解](https://blog.csdn.net/lfq88/article/details/127633178)，里面写的还不错。

我这就简单讲讲，actionIndex就是一个会复用的action的下标，还会自动改变值保持顺序(中间抬起，紧挨着的后面补上)，但是没法在ACTION_MOVE中使用。PointerId是唯一的，每一个手指事件序列的PointerId是不会变的(actionIndex是会改变的)，但是新增的PointerId会填充前面空缺的位置。PointerIndex的作用就是在ACTION_MOVE中替代actionIndex，因为在ACTION_MOVE中actionIndex一直是0，但还是要有一个按下手指的下标的，那就是PointerIndex了。

所以如果要跟踪手指的移动情况，就需要用到pointerId和pointerIndex了，配合下面两个方法就能处理了。
```
ev.findPointerIndex(pointerId)
ev.getPointerId(pointerIndex)
```

##### 一指旋转
上面多点触控讲的差不多了，要判断一指的移动事件，只需要在ACTION_MOVE处理pointerCount等于1时的情况。旋转最重要的就是旋转角度，这里只需要知道起始两点相对于X坐标的夹角就行，两个角度一减就是旋转角度了。算的角度后，使用invalidate()触发更新，在onDraw里对canvas rotate就行了。

这里要注意下每次canvas都会复位，所以要自己统计一个累加值。

##### 双指放大
和旋转类似，只不过要在ACTION_POINTER_DOWN的pointerCount等于2时获得初始的距离，之后再ACTION_MOVE中再获得新的距离，并触发更新，在onDraw里对canvas放大就行。

##### 三指移动
这个也类似双指放大，在ACTION_POINTER_DOWN中获得三个点时的中心，在ACTION_MOVE中更新中心，触发移动，在onDraw里对canvas进行translate。(ps. 我发现范围限定没有用，不知道怎么回事)

##### 四指以上重置
本来我以为重置会很复杂的，后面发现每次canvas都会复位，所以只有在onDraw中判断下是否是STATE_RESETTING事件，是的话把累加值全部置零就可以了。

##### 多种状态累加
在多种状态累加的时候，状态这种东西就有用了。在onDraw中，当属于自己状态的时候累加值，当不是自己状态时，按旧的累加值处理canvas就行了。