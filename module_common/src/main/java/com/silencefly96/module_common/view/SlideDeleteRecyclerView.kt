@file:Suppress("unused")

package com.silencefly96.module_common.view
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Scroller
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
                   //不管左右都应该让item跟随滑动
                   moveItem(e)
                   //拦截事件
                   return true
               }
               MotionEvent.ACTION_UP -> {
                   //判断结果
                   stopMove(e)
               }
           }
        }
        return super.onInterceptTouchEvent(e)
    }

    //滑动结束
    //版本一：判断一下结束的位置，补充或恢复位置
    private fun stopMove(e: MotionEvent) {
        mItem?.let {
            val dx = e.x - mLastX
            //如果移动过半了，应该判定左滑成功
            val deleteWidth = it.getChildAt(it.childCount - 1).width
            if (abs(dx) >= deleteWidth / 2) {
                //触发移动
                val left = if (dx > 0) {
                    deleteWidth - dx
                }else {
                    - deleteWidth + dx
                }
                mScroller.startScroll(0, 0, left.toInt(),0)
                invalidate()
            }else {
                //如果移动没过半应该恢复状态
                mScroller.startScroll(0, 0, - dx.toInt(),0)
                invalidate()
            }

            //清除状态
            mLastX = 0f
            mItem = null
        }
    }

    //移动item
    //版本一：绝对值小于删除按钮长度随便移动，大于则不移动
    private fun moveItem(e: MotionEvent) {
        mItem?.let {
            val dx = e.x - mLastX
            //这里默认最后一个view是删除按钮
            if (abs(dx) < it.getChildAt(it.childCount - 1).width) {
                //触发移动
                mScroller.startScroll(0, 0, dx.toInt(), 0)
                invalidate()
            }
        }
    }

    //获取点击位置
    //版本一：通过点击的y坐标除于item高度得出
    private fun getSelectItem(e: MotionEvent) {
        val firstChild = getChildAt(0)
        firstChild?.let {
            val pos = (e.x / firstChild.height).toInt()
            mItem = getChildAt(pos) as ViewGroup
        }
    }

    //流畅地滑动
    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mItem?.scrollBy(mScroller.currX, mScroller.currY)
            postInvalidate()
        }
    }
}