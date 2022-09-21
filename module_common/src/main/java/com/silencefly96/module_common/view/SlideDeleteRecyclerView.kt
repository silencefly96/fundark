@file:Suppress("unused")

package com.silencefly96.module_common.view
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
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

    //流畅滑动
    private var mScroller = Scroller(context)
    //当前选中item
    private var mItem: ViewGroup? = null
    //上次按下横坐标
    private var mLastX = 0f

    override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
        e?.let {
            when(e.action) {
               MotionEvent.ACTION_DOWN -> {
                   //获取点击位置
                   getSelectItem(e)
                   //设置点击的横坐标
                   mLastX = e.x
               }
               MotionEvent.ACTION_MOVE -> {
                   //判断是否拦截
                   return moveItem(e)
               }
//               MotionEvent.ACTION_UP -> {
//                   //判断结果
//                   stopMove()
//               }
           }
        }
        return super.onInterceptTouchEvent(e)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent?): Boolean {
        e?.let {
            when(e.action) {
                //拦截了ACTION_MOVE后，后面一系列event都会交到本view处理
                MotionEvent.ACTION_MOVE -> {
                    //移动控件
                    moveItem(e)
                    //更新点击的横坐标
                    mLastX = e.x
                }
                MotionEvent.ACTION_UP -> {
                    //判断结果
                    stopMove()
                }
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
            if (abs(it.scrollX) >= deleteWidth / 2f) {
                //触发移动至完全展开
                mScroller.startScroll(it.scrollX, 0, deleteWidth - it.scrollX,0)
                invalidate()
            }else {
                //如果移动没过半应该恢复状态
                mScroller.startScroll(it.scrollX, 0, -it.scrollX,0)
                invalidate()
            }

            //清除状态
            mLastX = 0f
            //不能为null，后续流畅滑动要用到
            //mItem = null
        }
    }

    //移动item
    //版本一：绝对值小于删除按钮长度随便移动，大于则不移动
    //问题：移动方向反了，而且左右可以滑动，没有限定住范围，mLast只是记住down的位置
    //版本二：通过整体移动的数值，和每次更新的数值，判断是否在范围内，再移动
    //问题：onInterceptTouchEvent的ACTION_MOVE只触发一次
    //版本三：放在onTouchEvent内执行，并且在onInterceptTouchEvent给出一个拦截判断
    private fun moveItem(e: MotionEvent): Boolean {
        mItem?.let {
            val dx = mLastX - e.x
            //检查mItem移动后应该在[-deleteLength, 0]内
            val deleteWidth = it.getChildAt(it.childCount - 1).width
            if ((it.scrollX + dx) <= deleteWidth && (it.scrollX + dx) >= 0) {
                //触发移动
                it.scrollBy(dx.toInt(), 0)
                return true
            }
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