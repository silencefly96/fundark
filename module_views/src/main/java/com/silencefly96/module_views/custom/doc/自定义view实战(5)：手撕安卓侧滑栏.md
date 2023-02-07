##### 前言
上一篇文章学了下自定义View的onDraw函数及自定义属性，做出来的滚动选择控件还算不错，就是逻辑复杂了一些。这篇文章打算利用自定义view的知识，直接手撕一个安卓侧滑栏，涉及到自定义LayoutParams、带padding和margin的measure和layout、利用requestLayout实现动画效果等，有一定难度，但能重新学到很多知识！

### 需求
这里类似旧版QQ（我特别喜欢之前的侧滑栏），有两层页面，滑动不是最左侧才触发的，而是从中间页面滑动就触发，滑动的时候主页面和侧滑栏页面会以不同速度滑动，核心思路如下：
- 1、两部分，主内容和左边侧滑栏，侧滑栏不完全占满主内容
- 2、在主内容页面向右滑动展现侧滑栏，同时主内容以更慢的速度向右滑动
- 3、侧滑栏完全显示时不再左滑
- 4、类似侧滑栏，通过自定义属性来指定侧滑栏页面，其他view为主内容
- 5、侧滑栏就一个view，容器内其他view作为主内容，view摆放类似垂直方向LinearLayout

### 编写代码
代码有点长，而且有些没用的代码没用注释，不过我希望的是能通过这些没用的代码来说明思路的不正确性。就像移动时的动画，本来我以为主内容和侧滑栏一起scrollTo就解决了，结果并不是。下面时代码：
```kotlin
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.core.animation.addListener
import androidx.core.view.forEach
import com.silencefly96.module_common.R
import kotlin.math.abs

/**
 * 类似旧版QQ，两层页面，切换的使用有互相移动动画
 * 核心思路
 * 1、两部分，主内容和左边侧滑栏，侧滑栏不完全占满主内容
 * 2、在主内容页面向右滑动展现侧滑栏，同时主内容以更慢的速度向右滑动
 * 3、侧滑栏完全显示时不再左滑
 * 4、类似侧滑栏，通过自定义属性来指定侧滑栏页面，其他view为主内容
 * 5、侧滑栏就一个view，容器内其他view作为主内容，view摆放类似垂直方向LinearLayout
 */
@Suppress("unused")
class TwoLayerSlideLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): ViewGroup(context, attributeSet, defStyleAttr){

    @Suppress("unused")
    companion object{
        //侧滑共有四个方向，一个不设置的属性，暂时只实现GRAVITY_TYPE_LEFT
        const val GRAVITY_TYPE_NULL = -1
        const val GRAVITY_TYPE_LEFT = 0
        const val GRAVITY_TYPE_TOP = 1
        const val GRAVITY_TYPE_RIGHT = 2
        const val GRAVITY_TYPE_BOTTOM = 3

        //滑动状态
        const val SLIDE_STATE_TYPE_CLOSED = 0
        const val SLIDE_STATE_TYPE_MOVING = 1
        const val SLIDE_STATE_TYPE_OPENED = 2
    }

    //侧滑栏控件
    private var mSlideView: View? = null

    //滑动状态
    private var mState = SLIDE_STATE_TYPE_CLOSED

    //最大滑动长度
    private var maxScrollLength: Float

    //最大动画使用时间
    private var maxAnimatorPeriod: Int

    //上次事件的横坐标
    private var mLastX = 0f

    //累计的滑动距离
    private var mScrollLength: Float = 0f

    //侧滑栏所占比例
    private var mSidePercent: Float = 0.75f

    //切换到目标状态的属性动画
    private var mAnimator: ValueAnimator? = null

    init {
        //读取XML参数
        val attrArr = context.obtainStyledAttributes(attributeSet, R.styleable.TwoLayerSlideLayout)

        //获得XML里面设置的最大滑动长度，没有的话需要在onMeasure后根据控件宽度设置
        maxScrollLength = attrArr.getDimension(R.styleable.TwoLayerSlideLayout_maxScrollLength,
            0f)

        //最大动画时间
        maxAnimatorPeriod = attrArr.getInteger(R.styleable.TwoLayerSlideLayout_maxAnimatorPeriod,
            300)

        //侧滑栏所占比例
        mSidePercent = attrArr.getFraction(R.styleable.TwoLayerSlideLayout_mSidePercent,
            1,1,0.75f)
        attrArr.recycle()
    }

    //测量会进行多次
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        //用默认方法，计算出所有的childView的宽和高，带padding不带margin
        //measureChildren(widthMeasureSpec, heightMeasureSpec)

        //getDefaultSize会根据默认值、模式、spec的值给到结果，建议点进去看看
        val width = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)

        //类似垂直方向LinearLayout，统计一下垂直方向高度使用情况
        //var widthUsed = 0
        var heightUsed = paddingTop
        var childWidthMeasureSpec: Int
        var childHeightMeasureSpec: Int
        forEach { child->
            //获取设定的gravity，用于判定是否是侧滑栏view，只要最后一个
            val childLayoutParams = child.layoutParams as LayoutParams
            val gravity = childLayoutParams.gravity
            if (gravity != GRAVITY_TYPE_NULL) {
                //暂不支持除左滑以外的情况
                if (gravity != GRAVITY_TYPE_LEFT)
                    throw IllegalArgumentException("function not support")

                //取到侧滑栏，多个时取最后一个
                mSlideView = child

                //侧滑栏大小另外测量，高度铺满父容器，宽度设置为父容器的四分之三
                childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                    (width * mSidePercent).toInt(), MeasureSpec.EXACTLY)
                //高度不限定
                childHeightMeasureSpec =
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)

                //侧滑栏不带padding和margin
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
            }else {

                //宽按需求申请，所以应该用AT_MOST，并向下层view传递
                childWidthMeasureSpec =
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST)
                childHeightMeasureSpec =
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)

                //heightUsed会在getChildMeasureSpec中用到，MATCH_PARENT时占满剩余size
                //WRAP_CONTENT时，会带着MeasureSpec.AT_MOST及剩余size向下层传递

                //带padding和margin的测量，推荐看看measureChildWithMargins
                //里面用到的getChildMeasureSpec函数，加深对MeasureSpec理解
                measureChildWithMargins(child, widthMeasureSpec, 0,
                    heightMeasureSpec, heightUsed)

                //计算的时候要加上child的margin值
                //widthUsed += child.measuredWidth
                heightUsed += child.measuredHeight +
                        childLayoutParams.topMargin + childLayoutParams.bottomMargin
            }
        }

        //最后加上本控件的paddingBottom，最终计算得到最终高度
        heightUsed += paddingBottom

        //设置最大滑动长度为宽度的三分之一
        if (maxScrollLength == 0f) {
            maxScrollLength = width / 3f
        }

        //设置测量参数，这里不能用heightUsed，因为虽然主内容可能未用完height，但是侧滑栏用完了height
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        //滑动时的偏移值，计算dx的时候时前面减后面，这里偏移值应该是后面减前面，所以取负
        val mainOffset = -mScrollLength / maxScrollLength * measuredWidth * (1 - mSidePercent)
        val slideOffset = -mScrollLength / maxScrollLength * (measuredWidth * mSidePercent)

        //不要忘记了paddingTop和paddingLeft，不然内容会被padding的背景覆盖
        var curHeight = paddingTop

        //布局
        var layoutParams: LayoutParams
        var gravity: Int
        var cTop: Int
        var cRight: Int
        var cLeft: Int
        var cBottom: Int
        forEach { child ->
            //获取设定的gravity，用于判定是否是侧滑栏view，只要最后一个
            layoutParams = child.layoutParams as LayoutParams
            gravity = layoutParams.gravity

            //布局主内容中view
            if (gravity == GRAVITY_TYPE_NULL) {
                //其他view带上累加高度布局
                cTop = layoutParams.topMargin + curHeight
                cLeft = paddingLeft + layoutParams.leftMargin + mainOffset.toInt()
                cRight = cLeft + child.measuredWidth
                cBottom = cTop + child.measuredHeight
                //布局
                child.layout(cLeft, cTop, cRight, cBottom)

                //累加高度
                curHeight = cBottom + layoutParams.bottomMargin
            }
        }

        //最后绘制侧滑栏，使其在最顶层？？？这里直接layout是没用的，绘想想看，绘制是onDraw的职责,这里有两个种办法
        //一是在XML中将侧滑栏放到最后去，二是将mSlideView放到children的最后去，onDraw内应该是for循环绘制的
        mSlideView?.let {
            //下面方法是专门在onLayout方法中使用的，不会触发requestLayout
            removeViewInLayout(mSlideView)
            addViewInLayout(mSlideView!!, childCount, mSlideView!!.layoutParams)

            //这里还有一个问题，当当前view设置padding的时候，侧滑栏会被裁切,设置不裁切padding内容
            this.layoutParams.apply {
                //不裁切孙view在父view超出的部分，让孙view在爷爷view中正常显示，这里不需要
                //clipChildren = false
                clipToPadding = false
            }

            //在页面左边
            cTop = 0
            cRight = slideOffset.toInt()
            cLeft = cRight - mSlideView!!.measuredWidth
            cBottom = cTop + mSlideView!!.measuredHeight

            //布局
            mSlideView!!.layout(cLeft, cTop, cRight, cBottom)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when(ev.action) {
                MotionEvent.ACTION_DOWN -> preMove(ev)
                MotionEvent.ACTION_MOVE -> return true
            }
        }
        return super.onInterceptTouchEvent(ev)
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when(ev.action) {
                //如果子控件未拦截ACTION_DOWN事件或者点击在view没有子控件的地方，onTouchEvent要处理
                MotionEvent.ACTION_DOWN -> {
                    //preMove(ev)
                    return true
                }
                MotionEvent.ACTION_MOVE -> moveView(ev)
                MotionEvent.ACTION_UP -> stopMove()
            }
        }

        return super.onTouchEvent(ev)
    }

    private fun preMove(e: MotionEvent) {
        mLastX = e.x
        if (mState == SLIDE_STATE_TYPE_MOVING) {
            //要取消结束监听，防止错误修改状态，把当前位置交给接下来的滑动处理
            mAnimator?.removeAllListeners()
            mAnimator?.cancel()
        }else {
            //关闭和展开时，点击滑动应该切换状态
            mState = SLIDE_STATE_TYPE_MOVING
        }
    }

    private fun moveView(e: MotionEvent) {
        //没有侧滑栏不移动，避免多次请求布局
        if (mSlideView == null) return

        //注意前面减去后面，就是页面应该scroll的值
        val dx = mLastX - e.x
        mLastX = e.x

        //Log.e("TAG", "moveView: mScrollLength=$mScrollLength")

        //设定滑动范围，注意mScrollLength和scrollX是不一样的，我们要实现不同的滑动效果
        //注意滑动的是窗口，view是窗口下的内容，手指向右滑动，页面（即主内容）向左移动，窗口向右移动
        if ((mScrollLength + dx) >= -maxScrollLength && (mScrollLength + dx) <= 0) {

            //范围内，叠加差值
            mScrollLength += dx

            //手指向右滑动，主内容向左缓慢滑动，侧滑栏向右滑动
            //要体现更慢的速度，主内容就移动侧滑栏所占比例的剩余值
            //val mainDx =  dx / maxScrollLength * measuredWidth * (1 - mSidePercent)
            //scrollBy(mainDx.toInt(), 0)

            //侧滑栏速度更大，这里根据最大滑动距离和侧滑栏的宽度做个映射
            //val sideDx = dx / maxScrollLength * (measuredWidth * mSidePercent)

            //侧滑栏的移动不能使用scrollTo和scrollBy，因为仅仅移动的是其中的内容，并不会移动整个view
            //可以理解成scrollTo和scrollBy只是在该对象的原有位置移动，即使移动了也不会在其范围之外显示（draw）
            //属性动画可以实现在父容器里面对子控件的移动，但是也是通过修改属性值重新布局实现的
            //sideView!!.scrollTo(sideView!!.scrollX + sideDx.toInt(), 0)


            //这里累加mScrollLength后直接请求重新布局，在onLayout里面去处理移动
            requestLayout()
        }
    }

    private fun stopMove() {
        //停止后，使用动画移动到目标位置
        val terminalScrollX: Float = if (abs(mScrollLength) >= maxScrollLength / 2f) {
            //触发移动至完全展开，mScrollLength是个负数
            -maxScrollLength
        }else {
            //如果移动没过半应该恢复状态，则恢复到原来状态
            0f
        }

        //这里使用ValueAnimator处理剩余的距离，模拟滑动到需要的位置
        mAnimator = ValueAnimator.ofFloat(mScrollLength, terminalScrollX)
        mAnimator!!.addUpdateListener { animation ->
            mScrollLength = animation.animatedValue as Float
            //请求重新布局
            requestLayout()
        }

        //动画结束时要更新状态
        mAnimator!!.addListener (onEnd = {
            mState = if(mScrollLength == 0f) SLIDE_STATE_TYPE_CLOSED else SLIDE_STATE_TYPE_OPENED
        })

        //滑动动画总时间应该和距离有关
        val percent = 1 - abs(mScrollLength / maxScrollLength)
        mAnimator!!.duration = (maxAnimatorPeriod * abs(percent)).toLong()
        //mAnimator.duration = maxAnimatorPeriod.toLong()
        mAnimator!!.start()
    }

    //自定义的LayoutParams，子控件使用的是父控件的LayoutParams，所以父控件可以增加自己的属性，在子控件XML中使用
    @Suppress("MemberVisibilityCanBePrivate")
    class LayoutParams : MarginLayoutParams {
        //侧滑栏方向，不设置就是null
        var gravity: Int = GRAVITY_TYPE_NULL

        //三个构造
        constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
            //读取XML参数，设置相关属性，这里有个很烦的warning，样式必须是外部类加layout结尾
            val attrArr =
                context.obtainStyledAttributes(attrs, R.styleable.TwoLayerSlideLayout_Layout)
            gravity = attrArr.getInteger(
                R.styleable.TwoLayerSlideLayout_Layout_slide_gravity, GRAVITY_TYPE_NULL)
            //回收
            attrArr.recycle()
        }
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.LayoutParams) : super(source)
    }

    //重写下面四个函数，在布局文件被填充为对象的时候调用的
    override fun generateLayoutParams(attrs: AttributeSet): ViewGroup.LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams {
        return LayoutParams(p)
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
        return LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is LayoutParams
    }
}
```
下面是配合使用的XML属性代码：
> res->value->two_layer_slide_layout_style.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <declare-styleable name ="TwoLayerSlideLayout">
        <attr name="maxScrollLength" format="dimension"/>
        <attr name="maxAnimatorPeriod" format="integer"/>
        <attr name="mSidePercent" format="fraction"/>
    </declare-styleable>
    <declare-styleable name ="TwoLayerSlideLayout.Layout">
        <attr name ="slide_gravity">
            <enum name ="left" value="0" />
            <enum name ="top" value="1" />
            <enum name ="right" value="2" />
            <enum name ="bottom" value="3" />
        </attr >
    </declare-styleable>
</resources>
```
使用时在XML里面的例子，kotlin代码几乎不用写了，注意命名空间是app，res-auto引入了我们的属性：
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <com.silencefly96.module_common.view.TwoLayerSlideLayout
        android:id="@+id/hhView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/teal_700"
        android:padding="50dp"
        app:mSidePercent="75%"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent">

        <LinearLayout
            app:slide_gravity="left"
            android:background="@color/teal_200"
            android:orientation="vertical"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
                android:text="@string/test_string"
                android:layout_width="match_parent"
                android:layout_height="match_parent"/>

        </LinearLayout>

        <TextView
            android:background="@color/purple_200"
            android:layout_marginTop="10dp"
            android:text="@string/app_name"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"/>

        <TextView
            android:background="@color/purple_200"
            android:layout_marginTop="10dp"
            android:layout_marginLeft="20dp"
            android:layout_marginRight="20dp"
            android:text="@string/app_name"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"/>

        <TextView
            android:background="@color/purple_200"
            android:layout_marginTop="50dp"
            android:text="@string/test_string"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"/>

    </com.silencefly96.module_common.view.TwoLayerSlideLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

### 主要问题
说起这个控件，问题可就很多了，当然学到的东西也特别多，下面好好讲讲。

##### 自定义XML中Fraction的使用
上一篇文章实际也用到了这个类型的属性，即百分比，可是我没测试下。在这个控件里面自己设置了一下，发现这个并不是像我想象填小数或者100内的整数，而是填完百分比后还要自己加一个百分号“%”！至于getFraction里面的base和pbase可以自己搜一下，我这就不展开讲了，毕竟主要内容是自定义view。

##### View提供的getDefaultSize
前面都是自己写一个getSizeFromMeasureSpec函数来根据MeasureSpec模式获得size，没想到View中已经提供了一个一模一样的功能，尴尬了。

##### 自定义LayoutParams
这个是这篇文章的重头戏了，没学习之前，我是万万没想到一个View的LayoutParams属性居然是父viewgroup的LayoutParams类型，而且自定义Viewgroup的同时还得自定义自身的LayoutParams，不然LayoutParams就一个height和一个width参数。话不多说，下面大致讲讲，详细的还是找资料再补充下！

###### 理解

关于一个View的LayoutParams属性居然是父viewgroup的LayoutParams类型的描述，其实也很好理解，想想经常用到的ConstraintLayout，我能不就是在它的子view中设置约束属性么。所以我们要实现一个Layout，那子view不就是使用Layout的LayoutParams么。更何况哪面试经常问的问题来说，一个view的宽高受什么影响，不就是父viewgroup的MeasureSpec和子view的LayoutParams决定的么，子view要对父view进行约束，那不就得知道父view需要控制什么属性么！

好了上面是我的理解，下面开始说明怎么使用。

###### LayoutParams需求

首先我们这里要实现一个类似官方侧滑栏的功能，相信大家都用过DrawerLayout，在DrawerLayout里面我们通过指定一个子view的layout_gravity就能让它成为侧滑栏，没错，我们这也想实现这样的效果。一开始我就直接写嘛，app:layout_gravity不就是官方的么，可是我在XML中输入这样一个属性，在onMeasure里面读取不就可以判定了。结果代码中的LayoutParams只有height和width两个参数，这麻烦了，找了下资料，原来要自己定义Viewgroup的LayoutParams！

###### 自定义LayoutParams
这里就大致讲下思路，代码里面注释写的很清楚，分三步吧。第一步是要在代码中创建一个自定义的LayoutParams，这里我就直接写成内部类了，实现其中几个构造函数，并在构造里面读取到要用的参数；第二步就是自定义参数了，需要创建一个xml文件来定义参数，这里用到了枚举类型的属性，并且代码里面也要定义好各种type，LayoutParams类中定义一个变量来储存这个属性；第三步就是重写在布局文件被填充为对象的时候调用的几个函数，就大功告成了。

使用的时候要自己强制转换一下，就能从子view的LayoutParams中拿到自定义的属性了。

##### 带padding和margin的测量
侧滑栏应该占满屏幕，不应该带padding和margin，另外测量就行，很简单。主内容部分我们要实现类似LinearLayout的效果，就得带上带padding和margin进行测量。

这里用到了measureChildWithMargins这个函数，他会接收child、MeasureSpec及宽高的使用情况对child进行带padding和margin的测量，可以点进去看看这个函数，里面又会调用getChildMeasureSpec去获得child的MeasureSpec，根据MeasureSpec的三种类型及LayoutParams.layout_width/height的三种形式（确切值、wrap_content、match_parent），会产生九种不同的组合。

不过可以理解的是，控件如果设置了值那就是设置的值（三种情况）；如果控件是match_parent，那EXACTLY和AT_MOST的值都会被该view用完（两种情况），如果是UNSPECIFIED就要特殊处理了（一种情况）；如果控件是wrap_content，在EXACTLY和AT_MOST里面，都会用给的值和AT_MOST生成一个新的MeasureSpec，并向下层传递下去，即wrap_content不知道要多大，但是知道最大有多大，下层的view按需索求（两种情况），在UNSPECIFIED里也是特殊处理下（一种情况）。

这里还有个heightUsed要注意下，累加的高度应该是父容器的padding，加上子控件的margin及高度共同构成的。我这里只统计了高度，宽度上也是同理。在这里的setMeasuredDimension函数中，用的是整个控件最大的高度，而不是heightUsed，因为侧滑栏占满了控件的高度。但是如果我们仅仅是实现一个LinarLayout的话，就应该用这个heightUsed了。

##### 带padding和margin的布局
这里和上面测量类似，要带上父容器的padding和子控件的margin以及子控件的宽高进行摆放。这里暂时不涉及动画的话，就是要把各个child的left、top、right、bottom四个值计算清楚，同时注意curHeight的累加就行了。

##### 侧滑栏被主内容里面控件覆盖显示问题
这里有个很奇怪的问题，就是侧滑栏会被主内容里面控件覆盖显示，侧滑栏可以覆盖主内容的背景，但是主内容里面的控件会在侧滑栏上面绘制。这里我把侧滑栏的view从XML第一个移到最后一个就没事了，可是这不符合我们的逻辑，我又在onLayout里面最后去layout侧滑栏，结果还是不行。后面想想绘制应该是在draw里面吧，可能是直接for循环绘制的，我用iterator移除再添加到最后去不就行了，后面发现children的iterator并未提供删除的功能，最后还是发现了removeViewInLayout和addViewInLayout两个函数，是专门在onLayout里面使用的，按前面的逻辑试一下，果然就好了。

##### 设置padding被裁切的问题
这里如果在我们的TwoLayerSlideLayout上设置padding，那就会出现很神奇的效果，侧滑栏也有padding了，但是仔细看，侧滑栏的内容位置是没错的，就是有padding的位置，侧滑栏的内容会被主内容的背景覆盖。查了下资料，又学了几个东西，主要就是viewgroup的layoutParams里面有个clipToPadding属性，默认为true，会将padding部分的子view进行裁切，我们在侧滑栏layout前把它设置为false就行了。

##### 滑动不生效问题
如果看了我前面的文章，在带header和footer的滚动控件中，中间滚动的控件是TextView，也是无法移动，在那里我是通过设置clickable为true让TextView也会消耗ACTION_DOWN事件，从而保证viewgroup能收到move事件。在写当前控件的时候，不仅是里面的TextView不会消耗ACTION_DOWN事件了，而且因为我们view还有很多是没有子view的空隙，点击在这些空隙里面同样不会消耗ACTION_DOWN事件，导致事件序列被丢弃，ACTION_MOVE事件也没了。

后面想想，好像还挺好解决的，之前没思考光去考虑TextView了，如果子控件没消耗耗ACTION_DOWN事件，事件会交到它的父控件的onTouchEvent处理，面试过的都知道，办法补救在这里吗？无论是子控件未消耗，还是点击在空隙上，最终都会把ACTION_DOWN事件交到当前控件的onTouchEvent方法内，我们在这里return true就可以了。

##### 侧滑栏的移动
前面几篇文章都做过移动的处理了，这个view我开始也是照搬代码，使用scrollBy去移动，侧滑栏在主内容移动的基础上继续通过scrollBy移动，结果想法很好，还计算了一系列值，最后发现只有主内容会移动。实际想了想，我调用侧滑栏的scrollBy去移动，移动的也只是侧滑栏的内容啊，也就是说移动是在侧滑栏内部进行的，又继续看了下滑动效果，果然侧滑栏虽然没有被scrollBy滑动覆盖主内容，但是侧滑栏里面的内容确实是以我设计的速度进行的。

写道这里我又想到了上面的clipToPadding属性，viewgroup的layoutParams还有一个clipChildren属性，就是不裁切不裁切孙view在父view超出的部分，可是就算侧滑栏里面的控件移动到了主内容上面，效果也还是不对的，因为侧滑栏的背景并没有移动，也就是说这是不可行的。

这里我想到了属性动画，属性动画是可以让整个view移动的，但是在每一个move事件里面去创建一个属性动画，每次移动一小部分吗？好像不太好，而且既然属性动画是根据属性去修改位置的，我们直接去修改布局不就行了。这里根据滑动值，计算出主内容和侧滑栏的偏移，然后使用requestLayout重新布局就可以了，布局的时候加上偏移，代码很简单。

##### 滑动停止切换到目标位置
这里和前面几个view一样，用ValueAnimator来模拟继续滑动，但是上一篇文章中滚动选择控件会因为动画没结束有继续滑动导致出现滑出界的问题，这里解决下。主要就是增加了一个状态的判定，分三个状态，如果动画没有结束，就点击进行滑动，在ACTION_DOWN事件时就把动画停了，并移除结束监听回调，这时候并不会修改mScrollLength，可以继续交给新的滑动接管整个滑动过程，这样用起来就流畅多了！

##### 滑动速度问题
```kotlin
val mainOffset = -mScrollLength / maxScrollLength * measuredWidth * (1 - mSidePercent)
val slideOffset = -mScrollLength / maxScrollLength * (measuredWidth * mSidePercent)
```
上面是我们主内容和侧滑栏偏移的计算代码，逻辑是我们设定一个让侧滑栏展开的最大滑动距离，滑动的时候侧滑栏按滑动距离占最大滑动距离的比例去展开侧滑栏，也就是说滑动距离等于最大滑动距离时就展开了，中间按比例移动；对于主内容，我们就让它移动的最大距离为侧滑栏所占屏幕宽度的剩余值，也就是说滑动距离等于最大滑动距离时主内容就移动了侧滑栏占屏幕宽度的剩余值，中间同样时按比例移动。稍微理解下，很简单，如果侧滑栏占屏幕宽度的比例大于一半，那侧滑栏速度就比主内容大，反之主内容速度大，实际上这样也很合理！