package com.silencefly96.module_common.view

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
 *
 * @author silence
 * @date 2022-10-12
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