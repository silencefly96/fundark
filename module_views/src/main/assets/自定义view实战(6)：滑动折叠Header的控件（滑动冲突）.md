##### 前言
上一篇文章直接通过安卓自定义view的知识手撕了一个侧滑栏，做的还不错，很有成就感。这篇文章的控件没有上一篇的复杂，比较简单，通过一个内容滚动造成header折叠的控件学习一下滑动事件冲突问题、更改view节点以及CoordinatorLayout事件传递（超低仿），基本都是一个引子，希望学完这个控件，要继续省略学习下涉及的内容。

### 需求
这里就是希望做一个滚动通过内容能够折叠header的控件，在XML内写的控件能够有滚动效果，header暂时默认实现。
核心思想：
 * 1、两部分，一个header和一个可以滚动的区域
 * 2、header有两种状态，一个是完全展开状态，一个是折叠状态
 * 3、在滚动区域向下滚动的时候，header会先滚动到折叠状态，header折叠后滚动区域才开始滚动
 * 4、在滚动区域向上滚动的时候，滚动区域先滚动，滚动区域到顶了才开始展开header
 * 5、低仿CoordinatorLayout，滚动区域效果通过自定义layoutParas向header传递

### 编写代码
```kotlin
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

/**
 * 内容滚动造成header折叠的控件
 */
class ScrollingCollapseTopLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): ViewGroup(context, attributeSet, defStyleAttr) {

    //外部滑动距离
    private var mScrollHeight = 0f

    //上次纵坐标
    private var mLastY = 0f

    //当前控件宽高
    private var mHeight = 0
    private var mWidth = 0

    //两个部分
    private val header: Header = Header(context).apply {
        //设置header垂直方向，宽度铺满，高度自适应
        orientation = LinearLayout.VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    //NestedScrollView只允许一个子view(和ScrollView一样)，这里放一个垂直的LinearLayout
    private val scrollArea: NestedScrollView = NestedScrollView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(LinearLayout(context).apply {
            setBackgroundColor(Color.LTGRAY)
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        })
    }

    //XML里面的view
    private val xmlViews: ArrayList<View> = ArrayList()

    //获取XML内view结束，没执行onMeasure
    override fun onFinishInflate() {
        super.onFinishInflate()
        //在这里获得所有子view，拦截添加到scrollArea去
        if (xmlViews.size == 0) {
            forEach { view ->
                xmlViews.add(view)
            }
        }

        //更换view的节点
        removeAllViewsInLayout()
        addView(header)
        addView(scrollArea)
        //把当前控件全部view放到NestedScrollView内的LinearLayout内去
        (scrollArea.getChildAt(0) as ViewGroup).also { linear->
            for(view in xmlViews) {
                linear.addView(view)
            }
        }
    }

    //在onSizeChanged才能获得正确的宽高，会在onMeasure后得到，这里只是学一下
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mHeight = h
        mWidth = w
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        //测量header
        header.onScroll(mScrollHeight.toInt())
        header.measure(widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec),MeasureSpec.AT_MOST))
        //先measure一下获得实际高度，再减去滑动的距离，也可以把header.measuredHeight写成全局变量
        if (header.measuredHeight != 0) {
            val scrolledHeight = header.measuredHeight + mScrollHeight
            val headerHeightMeasureSpec = MeasureSpec.makeMeasureSpec(scrolledHeight.toInt(),
                MeasureSpec.getMode(MeasureSpec.EXACTLY))
            //再次测量的目的是后面滚动部分要占满剩余高度
            header.measure(widthMeasureSpec, headerHeightMeasureSpec)
        }

        //测量滑动区域
        val leftHeight = MeasureSpec.getSize(heightMeasureSpec) - header.measuredHeight
        scrollArea.measure(widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(leftHeight, MeasureSpec.EXACTLY))
        Log.e("TAG", "onMeasure: leftHeight=$leftHeight")
        Log.e("TAG", "onMeasure: scrollArea.height=${scrollArea.height}")
        Log.e("TAG", "onMeasure: scrollArea.measuredHeight=${scrollArea.measuredHeight}")

        //直接占满宽高
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        //简单布局下，上下两部分
        header.layout(l, t, r, t + header.measuredHeight)
        scrollArea.layout(l, t + header.measuredHeight, r,b)
    }

    //事件冲突使用外部拦截
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        var isIntercepted = false
        ev?.let {
            when(ev.action) {
                //不拦截down事件
                MotionEvent.ACTION_DOWN -> mLastY = ev.y
                MotionEvent.ACTION_MOVE -> {
                    val dY = ev.y - mLastY

                    //如果折叠了，优先滚动折叠栏
                    val canScrollTop = scrollArea.canScrollVertically(-1)
                    val canScrollBottom = scrollArea.canScrollVertically(1)

                    //可以滚动
                    isIntercepted = if (canScrollTop || canScrollBottom) {
                        //手指向上移动时，没折叠前要拦截
                        val scrollUp = dY < 0 &&
                                mScrollHeight + dY > -header.collapsingArea.height.toFloat()
                        //手指向下移动时，没展开前且到顶了要拦截
                        val scrollDown = dY > 0 &&
                                mScrollHeight + dY < 0f &&
                                !canScrollTop

                        scrollUp || scrollDown
                    }else {
                        //不能滚动
                        true
                    }
                }
                //不拦截up事件
                //MotionEvent.ACTION_UP ->
            }
        }
        return isIntercepted
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when(ev.action) {
                //MotionEvent.ACTION_DOWN ->
                MotionEvent.ACTION_MOVE -> {
                    //累加滑动值，请求重新布局
                    val dY = ev.y - mLastY
                    if (mScrollHeight + dY <= 0 &&
                        mScrollHeight + dY >= -header.collapsingArea.height) {
                            mScrollHeight += dY
                            requestLayout()
                    }
                    mLastY = ev.y
                }
                //MotionEvent.ACTION_UP ->
            }
        }
        return super.onTouchEvent(ev)
    }

    //这里就做一个简单的折叠header，
    @Suppress("MemberVisibilityCanBePrivate")
    inner class Header @JvmOverloads constructor(
        context: Context,
        attributeSet: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ): LinearLayout(context, attributeSet, defStyleAttr){

        //两个区域
        val defaultArea: TextView
        val collapsingArea: TextView

        init {
            //添加两个header区域
            defaultArea = makeTextView(context, "Default area", 80)
            collapsingArea = makeTextView(context, "Collapsing area", 300)
            addView(defaultArea)
            addView(collapsingArea)
        }

        //低配Behavior.onNestedPreScroll，这里就处理下ScrollingHideTopLayout传过来的距离
        @SuppressLint("SetTextI18n")
        fun onScroll(scrollHeight: Int) {
            val expandHeight = collapsingArea.height + scrollHeight
            //这里就改一下背景色的透明度吧
            if (abs(expandHeight) <= collapsingArea.height) {
                val alpha = expandHeight.toFloat() / collapsingArea.height * 255
                defaultArea.text = "Default area:${alpha.toInt()}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    collapsingArea.setBackgroundColor(Color.argb(alpha.toInt(),88,88,88))
                }
            }
        }

        //创建TextView
        private fun makeTextView(context: Context, textStr: String, height: Int): TextView {
            //简单点height和textSize应该用dp和sp的，前面文章有
            return TextView(context).apply {
                layoutParams =
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
                text = textStr
                gravity = Gravity.CENTER
                textSize = 13f
                setBackgroundColor(Color.GRAY)
            }
        }
    }
}
```

### 主要问题
##### NestedScrollView的使用
要想中间内容能够滚动，并且和当前控件造成滑动冲突，就只能引入新的滑动控件了，这里使用了NestedScrollView，和ScrollView类似。NestedScrollView只允许有一个子view，至于为什么可以看下源码，内容不多。我这是直接创建了一个NestedScrollView，并往里面加个一个垂直的LinearLayout，后面更改xml里面的view节点，往LinearLayout里面放。

##### 修改xml内view的节点
上一篇文章里面，侧滑栏在xml里面的位置会影响绘制的层级，我是在onLayout里面通过移除再添加的方式做的，那如果要把view改到其他view里面去该怎么办。一开始我觉得很简单嘛，直接在onMeasure里面得到所有xml里面的view，再添加到其他viewgroup里面不就行了！想法很简单，试一下结果出我问题了。

第一个问题是view添加到其他viewgroup必须先移除，那我就直接就removeViewInLayout，结果就出了第二个问题OverStackError，大致就是一直measure，试了下是addView导致的，逻辑还是有问题。后面想想不应该在onMeasure里面实现的，应该在viewgroup加载xml里面子view时拦截处理的。

于是找了下api，发现viewgroup提供了一个onFinishInflate方法，会在加载xml里面view完成时调用，关键是它只会调用一次，onMeasure会调用多次，正好符合了我们的需求。修改节点就简单了，for循环一下就ok。

##### onSizeChanged函数
上面用到了onFinishInflate方法，找资料的时候看到自定义view里面常用重写的方法还有一个onSizeChanged函数。其实用的也多，主要是自定义view时用来获取控件宽高的，当控件的Size发生变化，如measure结束，onSizeChanged被调用，这时候才能拿到宽高，不然拿到的height和width就是0。

##### 滑动事件冲突处理
我觉得滑动事件冲突的处理都应该根据实际情况去处理，知识的话可以去看看《安卓开发艺术探讨》里面的相关知识，主要解决办法就是内部拦截法和外部拦截法。我这就是简单的外部拦截法，本来想写复杂点，看看能不能多学点东西，结果根据需求，最后的代码很简单。

外部拦截法原理就是在onInterceptTouchEvent方法中，通过根据场景判断是内部滚动还是外部滚动，外部滚动就直接拦截，内部是否能滚动可以通过canScrollVertically/canScrollHorizontally方法判断。我这逻辑很简单，首先判断下内部是否能滚动，内部不能滚动就直接交给外部处理；然后又分两种情况，一个是手指向上移动时，没折叠前要拦截，另一个就是手指向下移动时，没展开前且到内部顶了要拦截。无论真么处理，还是得根据情景，

##### 模仿CoordinatorLayout
本来还想模仿CoordinatorLayout做一个滑动状态传递的，这里滚动控件用的NestedScrollingChild，想让当前控件继承NestedScrollingParent处理滑动冲突，后面觉得还是简单点自己在onInterceptTouchEvent方法中处理能学点东西。当然读者有兴趣可借机学习一下NestedScrollingChild和NestedScrollingParent。

对于CoordinatorLayout，我也是学习了一下其中原理，私以为大致就是CoordinatorLayout的LayoutParams内有一个Behavior属性，Behavior作用就是构建两个子控件的关联关系（在CoordinatorLayout的onMeasure中），建立关联关系后，当一个view变化就会造成关联的view跟着变化（CoordinatorLayout控制），当然原理没这么简单，还是要去看源码。

本来我也想按这个逻辑模仿一下的，首先就是给当前控件的LayoutParams加一个Behavior属性，当滚动控件设置这个Behavior属性时，Header类在measure的时候就创建一个Behavior属性的私有变量，当前控件通过NestedScrollingChild接受滚动事件，并交给Header类的Behavior属性的私有变量去处理，一套逻辑下来，总感觉有脱裤子放屁的感觉，毕竟我这个控件就两个子控件。CoordinatorLayout的目的是协调多 View 之间的联动，重点在多，我这真没必要。

其实说到底，CoordinatorLayout就是一个协调功能，关联两个控件，比如我这就是滚动控件发出滚动消息，当前控件收到滚动消息，传递到Header里面处理，就这么简单，多了倒是可以按上面逻辑处理。

##### header折叠效果
这里的header的折叠效果是从onMeasure里面得到的！在测量时，根据滑动值，修改header的heightMeasureSpec，把header的高度设置为原有高度减去滑动高度，测量完header之后，把剩余的高度给到滑动区域，onLayout的时候将两个控件挨着就行。滑动的时候，请求重新layout，header和滚动区域每次都会获得不一样的高度，看起来就有了折叠效果。
