##### 前言
上一篇文章自定义了一个左滑删除的RecyclerView，把view事件分发三个函数dispatchTouchEvent、onInterceptTouchEvent、onTouchEvent实际运用了一下，一些原理通过出现的bug还是挺能加深印象，并且后面还在优化上用上了TouchSlop、VelocityTracker以及GestureDetector，但是真不配那个一个控件搞定安卓自定义view，所以我把上篇博客标题改了，并且希望在接下来的时间里，通过几个自定义view较全面的去学习自定义view的相关知识，话不多说，下面开始1

### 需求
上篇文章通过RecyclerView去实现了一个左滑的效果，后面突发奇想，既然能通过列表去实现item的左滑，那能不能通过item自己去实现左滑呢？这样我们把item内容写在自定义的layout里面就可以实现左滑了，听起来挺方便，于是就动手做了，少说多做总还是好的。

有了第一篇的内容，item的左滑还是简单多了，主要就是让item跟随滑动，右边自动添加一个删除按钮就够了吧，开始我是这么想的，并总结了三点核心思想：
- 一个容器，左右两部分，左边外部导入，右边删除框自动增加
- 在 View 右边追加一个删除框 ，需要在 View 内拦截事件，根据 x 轴滑动距离滑动
- 在 ConstraintLayout 内部添加一个删除框，左边对其 parent 右边

这里取巧了一下，继承的 ConstraintLayout，这样让添加的删除框对齐 ConstraintLayout的右边就行了。


### 编写代码
代码不多，就直接上代码了，注释写的很详细，后面再提下出现的主要问题：
```kotlin
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Scroller
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.abs

/**
 * 左划删除控件
 * 能在控件实现左滑吗？如何传入自定义的布局？
 * 思路：
 * 1、一个容器，左右两部分，左边外部导入，右边删除框 x 增加层级
 * 2、在 View 右边追加一个删除款 x 需要在 View 内拦截事件
 * 3、在 ConstraintLayout 内部添加一个删除框，左边对其 parent 右边
 *
 * @author silence
 * @date 2022-09-27
 */
class LeftDeleteItemLayout : ConstraintLayout {

    private val mDeleteView: View?

    var mDeleteClickListener: OnClickListener? = null

    //流畅滑动
    private var mScroller = Scroller(context)

    //上次事件的横坐标
    private var mLastX = -1f

    //控制控件结束的runnable
    private val stopMoveRunnable: Runnable = Runnable { stopMove() }

    constructor(context: Context) : this(context, null, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes)

    init {
        //kotlin的初始化函数
        mDeleteView = makeDeleteView(context)
        addView(mDeleteView)
    }

    //创建删除框，设置好位置对齐自身最右边
    private fun makeDeleteView(context: Context): View {
        val deleteView = TextView(context)

        //给当前控件一个id，用于删除控件约束
        this.id = generateViewId()

        //设置布局参数
        deleteView.layoutParams = LayoutParams(
            dp2px(context, 100f), 0
        ).apply {
            //设置约束条件
            leftToRight = id
            topToTop = id
            bottomToBottom = id
        }

        //设置其他参数
        deleteView.text = "删除"
        deleteView.gravity = Gravity.CENTER
        deleteView.setTextColor(Color.WHITE)
        deleteView.textSize = sp2px(context,18f).toFloat()
        deleteView.setBackgroundColor(Color.RED)

        //设置点击回调
        deleteView.setOnClickListener(mDeleteClickListener)

        return deleteView
    }

    //拦截事件
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            when(event.action) {
                //down事件记录x，不拦截，当move的时候才会用到
                MotionEvent.ACTION_DOWN -> mLastX = event.x
                //拦截本控件内的移动事件
                MotionEvent.ACTION_MOVE -> return true
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    //处理事件
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            when(event.action) {
                MotionEvent.ACTION_MOVE -> moveItem(event)
                MotionEvent.ACTION_UP -> stopMove()
            }
        }
        return super.onTouchEvent(event)
    }

    private fun moveItem(e: MotionEvent) {
        //Log.e("TAG", "moveItem: mLastX=$mLastX")
        //如果没有收到down事件，不应该移动
        if (mLastX == -1f) return

        val dx = mLastX - e.x
        //更新点击的横坐标
        mLastX = e.x
        //检查mItem移动后应该在[-deleteLength, 0]内
        val deleteWidth = mDeleteView!!.width
        if ((scrollX + dx) <= deleteWidth && (scrollX + dx) >= 0) {
            //触发移动
            scrollBy(dx.toInt(), 0)
        }

        //如果一段时间没有移动时间，mLastX还没被stopMove重置为-1，那就是移动到其他地方了
        //设置200毫秒没有新事件就触发stopMove
        removeCallbacks(stopMoveRunnable)
        postDelayed(stopMoveRunnable, 200)
    }

    private fun stopMove() {
        //如果移动过半了，应该判定左滑成功
        val deleteWidth = mDeleteView!!.width
        if (abs(scrollX) >= deleteWidth / 2f) {
            //触发移动至完全展开
            mScroller.startScroll(scrollX, 0, deleteWidth - scrollX, 0)
        }else {
            //如果移动没过半应该恢复状态，则恢复到原来状态
            mScroller.startScroll(scrollX, 0, - scrollX, 0)
        }

        invalidate()
        //清除状态
        mLastX = -1f
    }

    //流畅地滑动
    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.currX, mScroller.currY)
            postInvalidate()
        }
    }

    //单位转换
    @Suppress("SameParameterValue")
    private fun dp2px(context: Context, dpVal: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dpVal, context.resources
                .displayMetrics
        ).toInt()
    }

    @Suppress("SameParameterValue")
    private fun sp2px(context: Context, spVal: Float): Int {
        val fontScale = context.resources.displayMetrics.scaledDensity
        return (spVal * fontScale + 0.5f).toInt()
    }
```


### 主要问题
##### 动态生成TextView
这个主要就是通过代码生成一个TextView，不是很难，提一下。

##### 将TextView对齐到当前容器右端
这里利用ConstraintLayout取巧做的还是不错的，因为如果要自己去实现一个在屏幕外的对齐，至少要在onMeasure中获得宽度，再去onLayout里面摆放到右侧屏幕外。

这里也有一些问题，首先是设置动态生成的TextView参数，然后是设置ConstraintLayout内的约束条件，因为约束标记必须要用到id，还得为当前控件生成一个id，最后就是做一个回调接口了。

##### 滑动出界问题
还有一个没有预料到的问题是当滑动超过当前view的范围时，ACTION_MOVE和ACTION_UP都无法接收到，这就没法知道移动是否结束了。这里因为我们的自定义view是一个viewgroup，所以没法消耗ACTION_DOWN事件，所以后续的事件序列并不会交到当前的item上，这就麻烦了，所以这个需求本质上就是不合理的，但是还是要解决问题吧！

这里我通过View类的postDelayed，延迟运行一个runnable去停止滑动，当每次滑动的时候又去停止这个runnable。整个逻辑运行起来就是，滑动没有出界，移动的时候先移除延迟的停止逻辑，再发送延迟的停止逻辑，直到ACTION_UP触发停止，若滑动出界了，没有去移除延迟的停止逻辑，就会在一端时间后自动触发停止。

有点绕，但是还是挺简单的，里面的原理也简单讲一下。实际上View的postDelayed会通过主线程的handler去延迟执行，如果有了解handler机制，可以知道handler并不仅仅可以发送message，同样也可以发送runnable，类似移除message，同样也可以移除runnable。

##### 滑动开始判定
另一个预料之外的问题是当滑动从其他item移动到当前item的时候，即使没有收到ACTION_DOWN事件，也会触发滑动，这个很不符合逻辑。我这就在stopMove里面将mLastX改为了-1，初始值也是-1，如果在moveItem中值是-1，就说明没有被ACTION_DOWN事件设定mLastX，即按下的时候并不在当前item，应当舍弃滑动。