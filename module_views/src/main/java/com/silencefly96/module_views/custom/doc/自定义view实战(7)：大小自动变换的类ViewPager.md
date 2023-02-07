##### 前言
上一篇做了一个滑动折叠的Header控件，主要就是练习了一下滑动事件冲突的问题，控件和文章写的都不怎么样。本来想通过这篇文章的控件，整合一下前面六篇文章的内容的，结果写的太复杂了，就算了，没有新的技术知识，功能也和之前的安卓广东选择控件类似，不过在写的过程还是有点难度的，用来熟悉自定义view知识还是很不错的。

### 需求
这里我也不知道应该怎么描述这个控件，标题里用的大小自动变换的类ViewPager，一开始我把它叫做模仿桌面切换的多页面切换控件。大致就是和电视那种切换页面时，中间页面大，边上页面小，切换到中间会有变大的动画效果，我是觉得这样的控件和炫酷。

核心思想如下：
- 1、类似viewpager，但同时显示两种页面，中间为主页面，左右为小页面，小页面大小一样，间距排列
- 2、左右滑动可以将切换页面，超过页面数量大小不能滑动，滑动停止主界面能自动移动到目标位置

### 效果图
![效果图](https://img-blog.csdnimg.cn/b2e810d362984de18bed072edbacb4f5.gif#pic_center)

### 编写代码
这里代码写的还是挺简单的，没有用到ViewPager那样的Adapter，也没有处理预加载问题，滑动起来不是特别流畅，页面放置到顶层时切换很突兀，但是还是达到了一开始的设计要求吧！

```koltin
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.core.view.children
import com.silencefly96.module_common.R
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt


/**
 * @author silence
 * @date 2022-10-20
 */
class DesktopLayerLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attributeSet, defStyleAttr) {

    companion object{
        // 方向
        const val ORIENTATION_VERTICAL = 0
        const val ORIENTATION_HORIZONTAL = 1

        // 状态
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2

        // 默认padding值
        const val DEFAULT_PADDING_VALUE = 50

        // 竖向默认主界面比例
        const val DEFAULT_MAIN_PERCENT_VERTICAL = 0.8f

        // 横向默认主界面比例
        const val DEFAULT_MAIN_PERCENT_HORIZONTAL = 0.6f

        // 其他页面相对主界面页面最小的缩小比例
        const val DEFAULT_OTHER_VIEW_SCAN_SIZE = 0.5f
    }

    /**
     * 当前主页面的index
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var curIndex = 0

    // 由于将view提高层级会搞乱顺序，需要记录原始位置信息
    private var mInitViews = ArrayList<View>()

    // view之间的间距
    private var mGateLength = 0

    // 滑动距离
    private var mDxLen = 0f

    // 系统最小移动距离
    private val mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // 控件状态
    private var mState = SCROLL_STATE_IDLE

    // 当前设置的属性动画
    private var mValueAnimator: ValueAnimator? = null

    // 实际布局的左右坐标值
    private var mRealLeft = 0
    private var mRealRight = 0

    // 上一次按下的横竖坐标
    private var mLastX = 0f

    // 方向，从XML内获得
    private var mOrientation: Int

    // 是否对屏幕方向自适应，从XML内获得
    private val isAutoFitOrientation: Boolean

    // padding，从XML内获得，如果左右移动，则上下要有padding，但左右没有padding
    private val mPaddingValue: Int

    // 竖向主内容比例，从XML内获得，剩余两边平分
    private val mMainPercentVertical: Float

    // 横向主内容比例，从XML内获得，剩余两边平分
    private val mMainPercentHorizontal: Float

    // 其他页面相对主界面页面最小的缩小比例
    private val mOtherViewScanMinSize: Float

    init {
        // 获取XML参数
        val typedArray =
            context.obtainStyledAttributes(attributeSet, R.styleable.DesktopLayerLayout)

        mOrientation = typedArray.getInteger(R.styleable.DesktopLayerLayout_mOrientation,
            ORIENTATION_VERTICAL)

        isAutoFitOrientation =
            typedArray.getBoolean(R.styleable.DesktopLayerLayout_isAutoFitOrientation, true)

        mPaddingValue = typedArray.getInteger(R.styleable.DesktopLayerLayout_mPaddingValue,
            DEFAULT_PADDING_VALUE)

        mMainPercentVertical =
            typedArray.getFraction(R.styleable.DesktopLayerLayout_mMainPercentVertical,
            1, 1, DEFAULT_MAIN_PERCENT_VERTICAL)

        mMainPercentHorizontal =
            typedArray.getFraction(R.styleable.DesktopLayerLayout_mMainPercentHorizontal,
            1, 1, DEFAULT_MAIN_PERCENT_HORIZONTAL)

        mOtherViewScanMinSize =
            typedArray.getFraction(R.styleable.DesktopLayerLayout_mOtherViewScanMinSize,
            1, 1, DEFAULT_OTHER_VIEW_SCAN_SIZE)

        typedArray.recycle()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        // 获得所有xml内的view，保留原始顺序
        mInitViews.addAll(children)
    }

    // 屏幕方向变化并不会触发，初始时会触发，自适应
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Log.e("TAG", "onSizeChanged: w=$w, h=$h")
        // 根据屏幕变化修改方向，自适应
        if (isAutoFitOrientation) {
            mOrientation = if (w > h) ORIENTATION_HORIZONTAL else ORIENTATION_VERTICAL
            requestLayout()
        }
    }

    // 需要在manifest中注册捕捉事件类型，android:configChanges="orientation|keyboardHidden|screenSize"
    public override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            mOrientation = ORIENTATION_VERTICAL
            requestLayout()
        }else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mOrientation = ORIENTATION_HORIZONTAL
            requestLayout()
        }
    }

    // 排列规则：初始化第一个放中间，其他向右排列，中间最大，中心在左右边上的最小，不可见的也是最小
    // view的大小应该只和它在可见页面的位置有关，不应该和curIndex有关，是充分不必要关系
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // 获取默认尺寸，考虑背景大小
        val width = max(getDefaultSize(0, widthMeasureSpec), suggestedMinimumWidth)
        val height = max(getDefaultSize(0, heightMeasureSpec), suggestedMinimumHeight)

        // 设置间距
        mGateLength = width / 4

        // 中间 view 大小
        val maxWidth: Int
        val maxHeight: Int

        // 不同方向尺寸不同
        if (mOrientation == ORIENTATION_HORIZONTAL) {
            maxWidth = (width * mMainPercentHorizontal).toInt()
            maxHeight = height - 2 * mPaddingValue
        }else {
            maxWidth = (width * mMainPercentVertical).toInt()
            maxHeight = height - 2 * mPaddingValue
        }

        // 两侧 view 大小，第三排
        val minWidth = (maxWidth * mOtherViewScanMinSize).toInt()
        val minHeight = (maxHeight * mOtherViewScanMinSize).toInt()

        var childWidth: Int
        var childHeight: Int
        for (i in 0 until childCount) {
            val child = mInitViews[i]
            val scanSize = getViewScanSize(i, scrollX)
            childWidth = minWidth + ((maxWidth - minWidth) * scanSize).toInt()
            childHeight = minHeight + ((maxHeight - minHeight) * scanSize).toInt()
            // Log.e("TAG", "onMeasure($i): childWidth=$childWidth, childHeight=$childHeight")
            child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY))
        }

        setMeasuredDimension(width, height)
    }

    // 选中view为最大，可见部分会缩放，不可见部分和第三排一样大
    private fun getViewScanSize(index: Int, scrolledLen: Int): Float {
        var scanSize = 0f

        // 开始时当前view未测量，不计算
        if (measuredWidth == 0) return scanSize

        // 初始化的时候，第一个放中间，所以index移到可见范围为[2+index, index-2]，可见!=可移动
        val scrollLeftLimit = (index - 2) * mGateLength
        val scrollRightLimit = (index + 2) * mGateLength

        // 先判断child是否可见
        if (scrolledLen in scrollLeftLimit..scrollRightLimit) {
            // 根据二次函数计算比例
            scanSize = scanByParabola(scrollLeftLimit, scrollRightLimit, scrolledLen).toFloat()
        }

        return scanSize
    }

    // 根据抛物线计算比例，y属于[0, 1]
    // 映射关系：(form, 0) ((from + to) / 2, 0) (to, 0) -> (0, 0) (1, 1) (2, 0)
    @Suppress("SameParameterValue")
    private fun scanByParabola(from: Int, to: Int, cur: Int): Double {
        // 公式：val y = 1 - (x - 1).toDouble().pow(2.0)
        // Log.e("TAG", "scanByParabola:from=$from, to=$to, cur=$cur ")
        val x = ((cur - from) / (to - from).toFloat() * 2).toDouble()
        return 1 - (x - 1).pow(2.0)
    }

    // layout 按顺序间距排列即可，大小有onMeasure控制,开始位置在中心，也和curIndex无关
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val startX = (r + l) / 2
        // 排列布局
        for (i in 0 until childCount) {
            val child = mInitViews[i]

            // 中间减去间距，再减去一半的宽度，得到左边坐标
            val left = startX + mGateLength * i - child.measuredWidth / 2
            val top = (b + t) / 2 - child.measuredHeight / 2
            val right = left + child.measuredWidth
            val bottom = top + child.measuredHeight

            // Log.e("TAG", "onLayout($i): left=$left, right=$right")
            child.layout(left, top, right, bottom)
        }

        // 修改大小，布局完成后移动
        scrollBy(mDxLen.toInt(), 0)
        mDxLen = 0f

        // 完成布局及移动后，绘制之前，将可见view提高层级
        val targetIndex = getCurrentIndex()
        for (i in 2 downTo 0) {
            val preIndex = targetIndex - i
            val aftIndex = targetIndex + i

            // 逐次提高层级，注意在mInitViews拿就可以，不可见不管
            if (preIndex in 0..childCount) {
                bringChildToFront(mInitViews[preIndex])
            }

            if (aftIndex != preIndex && aftIndex in 0 until childCount) {
                bringChildToFront(mInitViews[aftIndex])
            }
        }
    }

    // 根据滚动距离获得当前index
    private fun getCurrentIndex()= (scrollX / mGateLength.toFloat()).roundToInt()

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when(it.action) {
                MotionEvent.ACTION_DOWN -> {
                    mLastX = ev.x
                    if(mState == SCROLL_STATE_IDLE) {
                        mState = SCROLL_STATE_DRAGGING
                    }else if (mState == SCROLL_STATE_SETTLING) {
                        mState = SCROLL_STATE_DRAGGING
                        // 去除结束监听，结束动画
                        mValueAnimator?.removeAllListeners()
                        mValueAnimator?.cancel()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    // 若ACTION_DOWN是本view拦截，则下面代码不会触发，要在onTouchEvent判断
                    val dX = mLastX - ev.x
                    return checkScrollInView(scrollX + dX)
                }
                MotionEvent.ACTION_UP -> {}
            }
        }
        return super.onInterceptHoverEvent(ev)
    }

    // 根据可以滚动的范围，计算是否可以滚动
    private fun checkScrollInView(length : Float): Boolean {
        // 一层情况
        if (childCount <= 1) return false
        // 左右两边最大移动值，即把最后一个移到中间
        val leftScrollLimit = 0
        val rightScrollLimit = (childCount - 1) * mGateLength

        return (length >= leftScrollLimit && length <= rightScrollLimit)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when(it.action) {
                // 防止点击空白位置或者子view未处理touch事件
                MotionEvent.ACTION_DOWN -> return true
                MotionEvent.ACTION_MOVE -> {
                    // 如果是本view拦截的ACTION_DOWN，要在此判断
                    val dX = mLastX - ev.x
                    if(checkScrollInView(scrollX + dX)) {
                        move(ev)
                    }
                }
                MotionEvent.ACTION_UP -> moveUp()
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun move(ev: MotionEvent) {
        val dX = mLastX - ev.x

        // 修改mScrollLength，重新measure及layout，再onLayout的最后实现移动
        mDxLen += dX
        if(abs(mDxLen) >= mTouchSlop) {
            requestLayout()
        }

        // 更新值
        mLastX = ev.x
    }

    private fun moveUp() {
        // 赋值
        val targetScrollLen = getCurrentIndex() * mGateLength
        // 不能使用scroller，无法在移动的时候进行测量
        // mScroller.startScroll(scrollX, scrollY, (targetScrollLen - scrollX), 0)

        // 这里使用ValueAnimator处理剩余的距离，模拟滑动到需要的位置
        val animator = ValueAnimator.ofFloat(scrollX.toFloat(), targetScrollLen.toFloat())
        animator.addUpdateListener { animation ->
            // Log.e("TAG", "stopMove: " + animation.animatedValue as Float)
            mDxLen = animation.animatedValue as Float - scrollX
            requestLayout()
        }

        // 在动画结束时修改curIndex
        animator.addListener (onEnd = {
            curIndex = getCurrentIndex()
            mState = SCROLL_STATE_IDLE
        })

        // 设置状态
        mState = SCROLL_STATE_SETTLING

        animator.duration = 300L
        animator.start()
    }
}
```

### 主要问题
这里用到的知识之前六篇文章都已经讲过了，主要就是有几点实现起来复杂了一些，下面讲讲。

##### 页面的自动缩放
讲解页面的缩放之前，需要先将一下页面的摆放。这里以四分之一为间距来摆放来自XML的view，第一个view放在中间，其他都在其右边按顺序排列。

所以页面的缩放，只和view的位置有关，而view的位置又只和当前控件左右滑动的距离有关，变量就是当前控件横坐标上的滑动值scrollX。根据view的原始index可以得到每个view可见时的滑动值范围，在通过这个范围和实际的滑动值scrollX，进行映射换算得到其缩放比例。这里用到了抛物线进行换算：
```
// 公式：y = 1 - (x - 1).toDouble().pow(2.0)
// 映射关系：(form, 0) ((from + to) / 2, 0) (to, 0) -> (0, 0) (1, 1) (2, 0)
```

##### 滑动范围的限定
滑动范围的限定和上面类似，边界就是第一个或者最后一个view移动到正中间的范围，只要实际的滑动值scrollX在这个范围内，那滑动就是有效的。

##### 页面层级提升与恢复
页面层级的提升在我之前文章：[手撕安卓侧滑栏](https://blog.csdn.net/lfq88/article/details/127304059)也有用到，就是自己把view放到children的最后去，实际上ViewGroup提供了类似的功能：bringChildToFront，但是原理是一样的。
```java
    @Override
    public void bringChildToFront(View child) {
        final int index = indexOfChild(child);
        if (index >= 0) {
            removeFromArray(index);
            addInArray(child, mChildrenCount);
            child.mParent = this;
            requestLayout();
            invalidate();
        }
    }
```

这里的提升view不止一个了，而且后面还要恢复，即不能打乱children的顺序。所以我在onFinishInflate中用一个数组保存下这些子view的原始顺序，使用的时候用这个数组就行，children里面的顺序不用管，只要让需要显示的view放在最后就行。我这里因为间距是四分之一的宽度，最多可以显示五个view，所以在onLayout的最后将这五个view得到，并按顺序放到children的最后。

##### onDraw探讨
这里我还想对onDraw探讨一下，一开始我以为既然onMeasure、onLayout中都需要去调用child的measure和layout，那能不能在onDraw里面自己去绘制child，不用自带的，结果发现这是不行的。onDraw实际是View里面的一个空方法，实际对页面的绘制是在控件的draw方法中，那重写draw方法自己去绘制child呢？实际也不行，当把draw方法里面的super.draw时提示报错：
![tips](https://img-blog.csdnimg.cn/e0bbabe49a6049948fde70dc6331b101.png)

也就是说必须继承super.draw这个方法，点开源码发现，super.draw已经把child绘制了，而且onDraw方法也是从里面传出来的。所以没办法，乖乖用bringChildToFront放到children最后去，来提升层级吧，不然也不会提供这一个方法来是不是？