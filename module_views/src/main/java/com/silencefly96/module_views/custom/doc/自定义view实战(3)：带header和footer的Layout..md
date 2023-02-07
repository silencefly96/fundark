##### 前言
上两篇文章对安卓自定义view的事件分发做了一些应用，但是对于自定义view来讲，并不仅仅是事件分发这么简单，还有一个很重要的内容就是view的绘制流程。接下来我这通过带header和footer的Layout，来学习一下ViewGroup的自定义流程，并对其中的MeasureSpec、onMeasure以及onLayout加深理解。

### 需求
这里就是一个有header和footer的滚动控件，可以在XML中当Layout使用，核心思想如下：
- 1、由header、XML内容、footer三部分组成
- 2、滚动中间控件时，上面有内容时header不显示，下面有内容时footer不显示
- 3、滑动到header和footer最大值时不能滑动，释放的时候需要回弹
- 4、完全显示时隐藏footer


### 编写代码
编写代码这部分还真让我头疼了一会，主要就是MeasureSpec的运用，如何让控件能够超出给定的高度，如何获得实际高度和控件高度，真是纸上得来终觉浅，绝知此事要躬行，看书那么多遍，实际叫自己写起来真的费劲，不过最终写完，才真的敢说自己对measure和layout有一定了解了。

老习惯，先看代码，再讲问题吧！
```kotlin
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Scroller
import android.widget.TextView
import androidx.core.view.forEach
import kotlin.math.min

/**
 * 有header和footer的滚动控件
 * 核心思想：
 * 1、由header、container、footer三部分组成
 * 2、滚动中间控件时，上面有内容时header不显示，下面有内容时footer不显示
 * 3、滑动到header和footer最大值时不能滑动，释放的时候需要回弹
 * 4、完全显示时隐藏footer
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class HeaderFooterView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0,
    var header: View? = null,
    var footer: View? = null
): ViewGroup(context, attributeSet, defStyleAttr){

    var onReachHeadListener: OnReachHeadListener? = null
    var onReachFootListener: OnReachFootListener? = null

    //上次事件的横坐标
    private var mLastY = 0f

    //总高度
    private var totalHeight = 0

    //是否全部显示
    private var isAllDisplay = false

    //流畅滑动
    private var mScroller = Scroller(context)

    init {
        //设置默认的Header、Footer，这里是从构造来的，如果外部设置需要另外处理
        header = header ?: makeTextView(context, "Header")
        footer = footer ?: makeTextView(context, "Footer")

        //添加对应控件
        addView(header, 0)

        //这里还没有加入XML中的控件
        //Log.e("TAG", "init: childCount=$childCount", )
        addView(footer, 1)
    }

    //创建默认的Header\Footer
    private fun makeTextView(context: Context, textStr: String): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp2px(context, 30f))
            text = textStr
            gravity = Gravity.CENTER
            textSize = sp2px(context, 13f).toFloat()
            setBackgroundColor(Color.GRAY)

            //不设置isClickable的话，点击该TextView会导致mFirstTouchTarget为null，
            //致使onInterceptTouchEvent不会被调用，只有ACTION_DOWN能被收到，其他事件都没有
            //因为事件序列中ACTION_DOWN没有被消耗（返回true），整个事件序列被丢弃了
            //如果XML内是TextView也会造成同样情况，
            isFocusable = true
            isClickable = true

        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        //父容器给当前控件的宽高，默认值尽量设大一点
        val width = getSizeFromMeasureSpec(1080, widthMeasureSpec)
        val height = getSizeFromMeasureSpec(2160, heightMeasureSpec)

        //对子控件进行测量
        forEach { child ->
            //宽度给定最大值
            val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST)
            //高度不限定
            val childHeightMeasureSpec
                = MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED)

            //进行测量，不测量的话measuredWidth和measuredHeight会为0
            child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
            //Log.e("TAG", "onMeasure: child.measuredWidth=${child.measuredWidth}")
            //Log.e("TAG", "onLayout: child.measuredHeight=${child.measuredHeight}")
        }
        //设置测量高度为父容器最大宽高
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec))
    }

    private fun getSizeFromMeasureSpec(defaultSize: Int, measureSpec: Int): Int {
        //获取MeasureSpec内模式和尺寸
        val mod = MeasureSpec.getMode(measureSpec)
        val size = MeasureSpec.getSize(measureSpec)

        return when (mod) {
            MeasureSpec.EXACTLY -> size
            MeasureSpec.AT_MOST -> min(defaultSize, size)
            else -> defaultSize //MeasureSpec.UNSPECIFIED
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var curHeight = 0
        //Log.e("TAG", "onLayout: childCount=${childCount}")
        forEach { child ->
            //footer最后处理
            if (indexOfChild(child) != 1) {
                //Log.e("TAG", "onLayout: child.measuredHeight=${child.measuredHeight}")
                child.layout(left, top + curHeight, right,
                    top + curHeight + child.measuredHeight)
                curHeight += child.measuredHeight
            }
        }

        //处理footer
        val footer = getChildAt(1)
        //完全显示内容时不加载footer，header不算入内容
        if (measuredHeight < curHeight - header!!.height) {
            //设置全部显示flag
            isAllDisplay = false

            footer.layout(left, top + curHeight, right,top + curHeight + footer.measuredHeight)
            curHeight += footer.measuredHeight
        }

        //布局完成，滚动一段距离，隐藏header
        scrollBy(0, header!!.height)

        //设置总高度
        totalHeight = curHeight
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        //Log.e("TAG", "onInterceptTouchEvent: ev=$ev")
        ev?.let {
            when(ev.action) {
                MotionEvent.ACTION_DOWN -> mLastY = ev.y
                MotionEvent.ACTION_MOVE -> return true
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        //Log.e("TAG", "onTouchEvent: height=$height, measuredHeight=$measuredHeight")
        ev?.let {
            when(ev.action) {
                MotionEvent.ACTION_MOVE -> moveView(ev)
                MotionEvent.ACTION_UP -> stopMove()
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun moveView(e: MotionEvent) {
        //Log.e("TAG", "moveView: height=$height, measuredHeight=$measuredHeight")
        val dy = mLastY - e.y
        //更新点击的纵坐标
        mLastY = e.y
        //纵坐标的可滑动范围，0 到 隐藏部分高度，全部显示内容时是header高度
        val scrollMax = if (isAllDisplay) {
            header!!.height
        }else {
            totalHeight - height
        }
        //限定滚动范围
        if ((scrollY + dy) <= scrollMax &&  (scrollY + dy) >= 0) {
            //触发移动
            scrollBy(0, dy.toInt())
        }
    }

    private fun stopMove() {
        //Log.e("TAG", "stopMove: height=$height, measuredHeight=$measuredHeight")
        //如果滑动到显示了header，就通过动画隐藏header，并触发到达顶部回调
        if (scrollY < header!!.height) {
            mScroller.startScroll(0, scrollY, 0, header!!.height - scrollY)
            onReachHeadListener?.onReachHead()
        }else if(!isAllDisplay && scrollY > (totalHeight - height - footer!!.height)) {
            //如果滑动到显示了footer，就通过动画隐藏footer，并触发到达底部回调
            mScroller.startScroll(0, scrollY,0,
                 (totalHeight - height- footer!!.height) - scrollY)
            onReachFootListener?.onReachFoot()
        }

        invalidate()
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

    interface OnReachHeadListener{
        fun onReachHead()
    }

    interface OnReachFootListener{
        fun onReachFoot()
    }
}
```


### 主要问题
##### 父容器给当前控件的宽高
这里就是MeasureSpec的理解了，onMeasure中给了两个参数：widthMeasureSpec和heightMeasureSpec，里面包含了父控件给当前控件的宽高，根据模式的不同可以取出给的数值，根据需要设定自身的宽高，需要注意setMeasuredDimension函数设定后，measuredWidth和measuredHeight才有值。

##### 对子控件进行测量
这里很容易忽略的是，当继承viewgroup的时候，我们要手动去调用child的measure函数，去测量child的宽高。一开始我也没注意到，当我继承LineaLayout的时候是没问题的，后面改成viewgroup后就出问题了，看了下LineaLayout的源码，里面的onMeasure函数中实现了对child的测量。

对子控件的测量时，MeasureSpec又有用了，比如说我们希望XML中的内容不限高度或者高度很大，这时候MeasureSpec.UNSPECIFIED就有用了，而宽度我们希望最大就是控件宽度，就可以给个MeasureSpec.AT_MOST，注意我们给子控件的MeasureSpec也是有两部分的，需要通过makeMeasureSpec创建。

##### 子控件的摆放
由于我们的footer和header是在构造里面创建并添加到控件中的，这时候XML内的view还没加进来，所以需要注意下footer实际在控件中是第二个，摆放的时候根据index要特殊处理一下。

其他控件我们根据左上右下的顺序摆放就行了，注意onMeasure总对子控件measure了才有宽高。

##### 控件总高度和控件高度
因为需求，我们的控件要求是中间可以滚动，所以在onMeasure总，我们用到了MeasureSpec.UNSPECIFIED，这时候控件的高度和实际总高度就不一致了。这里我们需要在onLayout中累加到来，实际摆放控件的时候也要用到这个高度，顺势而为了。

##### header和footer的初始化显示与隐藏
这里希望在开始的时候隐藏header，所以需要在onLayout完了的时候，向上滚动控件，高度为header的高度。

根据需求，完全显示内容的时候，我们不希望显示footer，这里也要在onLayout里面实现，根据XML内容的高度和控件高度一比较就知道需不需要layout footer了。

##### header和footer的动态显示与隐藏
这里就和前面两篇文章类似了，就是在纵坐标上滚动控件，限定滚动范围，在ACTION_UP事件时判定滚动后的状态，动态去显示和隐藏header和footer，思路很明确，逻辑可能复杂一点。

##### 使用
这里简单说下使用吧，就是作为Layout，中间可以放控件，中间控件可以指定特别大的高度，也可以wrap_content，但是内容很高。
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <com.silencefly96.module_common.view.HeaderFooterView
        android:id="@+id/hhView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/teal_700"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent">

        <TextView
            android:text="@string/test_string"
            android:focusable="true"
            android:clickable="true"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
             />

    </com.silencefly96.module_common.view.HeaderFooterView>

</androidx.constraintlayout.widget.ConstraintLayout>
```
这里的test_string特别长，滚动起来header和footer可以拉出来，释放会缩回去。还可以在代码中获得控件增加触底和触顶的回调。

##### 中间为TextView时不触发ACTION_MOVE事件
上面XML布局中，如果不加clickable=true的话，控件中只会收到一个ACTION_DOWN事件，然后就没有然后了，即使是dispatchTouchEvent中也没有事件了。经查，原来不设置isClickable的话，点击该TextView会导致mFirstTouchTarget为null，致使onInterceptTouchEvent不会被调用，因为事件序列中ACTION_DOWN没有被消耗（未返回true），整个事件序列被丢弃了。


##### 结语
实际上这个控件写的并不是很好，拿去用的话还是不太行的，但是用来学习的话还是能理解很多东西。