@file:Suppress("unused")

package com.silencefly96.module_views.custom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.*
import android.widget.Scroller
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs


/**
 * 左划删除控件
 *
 * 核心思想
 * 1、在 down 事件中，判断在列表内位置,得到对应 item
 * 2、拦截 move 事件，item 跟随滑动，最大距离为删除按钮长度
 * 3、在 up 事件中，确定最终状态，固定 item 位置
 *
 * @author silence
 * @date 2022-09-21
 */
class SlideDeleteRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    //系统最小移动距离
    private val mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    //最小有效速度
    private val mMinVelocity = 600

    //增加手势控制，双击快速完成侧滑，还是为了练习
    private var mGestureDetector: GestureDetector
        = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener(){
            override fun onDoubleTap(e: MotionEvent): Boolean {
                getSelectItem(e)
                mItem?.let {
                    val deleteWidth = it.getChildAt(it.childCount - 1).width
                    //触发移动至完全展开deleteWidth
                    if (it.scrollX == 0) {
                        mScroller.startScroll(0, 0, deleteWidth, 0)
                    }else {
                        mScroller.startScroll(it.scrollX, 0, -it.scrollX, 0)
                    }
                    invalidate()
                    return true
                }

                //不进行拦截，只是作为工具判断下双击
                return false
            }
        })

    //使用速度控制器，增加侧滑速度判定滑动成功，主要为了是练习
    //VelocityTracker 由 native 实现，需要及时释放内存
    private var mVelocityTracker: VelocityTracker? = null

    //流畅滑动
    private var mScroller = Scroller(context)

    //当前选中item
    private var mItem: ViewGroup? = null

    //上次事件的横坐标
    private var mLastX = 0f
    private var mLastY = 0f
    // 滑动模式，左滑模式(-1)，初始化(0)，上下模式(1)
    private var mScrollMode = 0

    //当前RecyclerView被上层viewGroup分发到事件，所有事件都会通过dispatchTouchEvent给到
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        //
        mGestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    //viewGroup对子控件的事件拦截，一旦拦截，后续事件序列不会再调用onInterceptTouchEvent
    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                //这里的优化会阻止双击滑动的使用，实际也没什么好优化的
//                    //防止快速按下情况出问题
//                    if (!mScroller.isFinished) {
//                        mScroller.abortAnimation()
//                    }

                //获取点击位置
                getSelectItem(e)
                //设置点击的横坐标
                mLastX = e.x
                // 对滑动冲突处理
                mLastY = e.y
                mScrollMode = 0
            }
            MotionEvent.ACTION_MOVE -> {
                //判断是否拦截
                //如果拦截了ACTION_MOVE，后续事件就不触发onInterceptTouchEvent了
                // 不能拦截事件，会造成列表项中onclick事件失效
//                return moveItem(e)
            }
            //拦截了ACTION_MOVE，ACTION_UP也不会触发
//                MotionEvent.ACTION_UP -> {
//                    //判断结果
//                    stopMove()
//                }
        }
        return super.onInterceptTouchEvent(e)
    }

    //拦截后对事件的处理，或者子控件不处理，返回到父控件处理，在onTouch之后，在onClick之前
    //如果不消耗，则在同一事件序列中，当前View无法再次接受事件
    //performClick会被onTouchEvent拦截，我们这不需要点击，全都交给super实现去了
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                return true
            }
            //拦截了ACTION_MOVE后，后面一系列event都会交到本view处理
            MotionEvent.ACTION_MOVE -> {
                // Log.e("TAG", "onTouchEvent: ACTION_MOVE")
                if (mScrollMode == 0) {
                    val deltaX = abs(e.x - mLastX)
                    val deltaY = abs(e.y - mLastY)
                    // 异常情况忽略了
                    if (deltaX == deltaY && deltaX == 0f) return super.onTouchEvent(e)
                    // 判断模式，进入左滑状态(-1)，上下滑动(1)
                    mScrollMode = if (deltaX > deltaY) -1 else 1
                }

                // 左滑模式下交给当前控件处理，其他情况由RecyclerView去滑动
                if (mScrollMode < 0) {
                    //移动控件
                    moveItem(e)
                    //更新点击的横坐标
                    mLastX = e.x
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                //判断结果
                stopMove()
            }
        }
        return super.onTouchEvent(e)
    }

    //滑动结束
    //版本一：判断一下结束的位置，补充或恢复位置
    //问题：mLast不应该是down的位置
    //版本二：改进结果判断
    //问题：onInterceptTouchEvent的ACTION_UP不触发
    //版本三：改进补充或恢复位置的逻辑
    private fun stopMove() {
        mItem?.let {
            //如果移动过半了，应该判定左滑成功
            val deleteWidth = it.getChildAt(it.childCount - 1).width
            //如果整个移动过程速度大于600，也判定滑动成功
            //注意如果没有拦截ACTION_MOVE，mVelocityTracker是没有初始化的
            var velocity = 0f
            mVelocityTracker?.let { tracker ->
                tracker.computeCurrentVelocity(1000)
                velocity = tracker.xVelocity
            }
            //判断结束情况,移动过半或者向左速度很快都展开
            if ( (abs(it.scrollX) >= deleteWidth / 2f) || (velocity < - mMinVelocity) ) {
                //触发移动至完全展开
                mScroller.startScroll(it.scrollX, 0, deleteWidth - it.scrollX, 0)
                invalidate()
            }else {
                //如果移动没过半应该恢复状态，或者向右移动很快则恢复到原来状态
                mScroller.startScroll(it.scrollX, 0, -it.scrollX, 0)
                invalidate()
            }

            //清除状态
            mLastX = 0f
            //不能为null，后续mScroller要用到
            //mItem = null
            //mVelocityTracker由native实现，需要及时释放
            mVelocityTracker?.apply {
                clear()
                recycle()
            }
            mVelocityTracker = null
        }
    }

    //移动item
    //版本一：绝对值小于删除按钮长度随便移动，大于则不移动
    //问题：移动方向反了，而且左右可以滑动，没有限定住范围，mLast只是记住down的位置
    //版本二：通过整体移动的数值，和每次更新的数值，判断是否在范围内，再移动
    //问题：onInterceptTouchEvent的ACTION_MOVE只触发一次
    //版本三：放在onTouchEvent内执行，并且在onInterceptTouchEvent给出一个拦截判断
    @SuppressLint("Recycle")
    private fun moveItem(e: MotionEvent): Boolean {
        mItem?.let {
            val dx = mLastX - e.x
            //最小的移动距离应该舍弃，onInterceptTouchEvent不拦截，onTouchEvent内才更新mLastX
            //if(abs(dx) > mTouchSlop) {
                //检查mItem移动后应该在[-deleteLength, 0]内
                val deleteWidth = it.getChildAt(it.childCount - 1).width
                if ((it.scrollX + dx) <= deleteWidth && (it.scrollX + dx) >= 0) {
                    //触发移动
                    it.scrollBy(dx.toInt(), 0)
                    //触发速度计算
                    //这里Recycle不存在问题，一旦返回true，就会拦截事件，就会到达ACTION_UP去回收
                    mVelocityTracker = mVelocityTracker ?: VelocityTracker.obtain()
                    mVelocityTracker!!.addMovement(e)
                    return true
                }
            //}
        }
        return false
    }

    //获取点击位置
    //版本一：通过点击的y坐标除于item高度得出
    //问题：没考虑列表项的可见性、列表滑动的情况，并且x和屏幕有关不仅仅是列表
    //版本二：通过遍历子view检查事件在哪个view内，得到点击的item
    //问题：没有问题，成功拿到了mItem
    private fun getSelectItem(e: MotionEvent) {
        //获得第一个可见的item的position
        val frame = Rect()
        //防止点击其他地方，保持上一个item
        mItem = null
        forEach {
            if (it.visibility != GONE) {
                it.getHitRect(frame)
                if (frame.contains(e.x.toInt(), e.y.toInt())) {
                    mItem = it as ViewGroup
                }
            }
        }
    }

    //流畅地滑动
    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mItem?.scrollTo(mScroller.currX, mScroller.currY)
            postInvalidate()
        }
    }
}