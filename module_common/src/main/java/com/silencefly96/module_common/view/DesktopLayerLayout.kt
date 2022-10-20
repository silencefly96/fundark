@file:Suppress("unused")

package com.silencefly96.module_common.view

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import com.silencefly96.module_common.R

/**
 * 模仿桌面切换的多页面切换控件
 * 核心思想：
 * 1、类似viewpager，但同时显示三个页面，中间为主页面，左右为小页面，主页面
 * 2、左右滑动可以将切换页面，超过页面数量大小不能滑动
 * 3、页面向上移动后，进入拖拽模式，可以将页面拖拽移动到其他页面位置，进行位置调整
 * 4、需要处理滑动冲突，左右和上下的滑动冲突都需要处理
 * 5、页面最底部有小圆圈提示当前页面位置
 *
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

        // 预加载页面限制,-1为不限制
        const val OFFSCREEN_PAGE_LIMIT_DEFAULT = -1

        // 默认padding值
        const val DEFAULT_PADDING_VALUE = 50

        // 默认主界面比例
        const val DEFAULT_MAIN_PERCENT = 0.8f

        // 默认主界面比例
        const val DEFAULT_DRAG_MODE_UP_Percent_PERCENT = 0.15f

    }

    /**
     * 当前主页面的index
     */
    var curIndex = 0
    set(value) {
        field = value
        //TODO changeCurrentPage(value)

    }

    // 页面适配器
    var mAdapter: LayerAdapter? = null

    // 方向，从XML内获得
    private var mOrientation: Int

    // 是否对屏幕方向自适应，从XML内获得
    private val isAutoFitOrientation: Boolean

    // padding，从XML内获得，如果左右移动，则上下要有padding，但左右没有padding
    private val mPaddingValue: Int

    // 主内容比例，从XML内获得，剩余两边平分
    private val mMainPercent: Float

    // 向上滑动进入拖拽模式的向上高度百分比，从XML内获得
    private val mDragModeUpPercent: Float

    // 预加载页面间隔，从XML内获得，只有间隔内的页面才会被加载，<= size / 2
    private val mOffscreenPageLimit: Int

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

        mMainPercent = typedArray.getFraction(R.styleable.DesktopLayerLayout_mMainPercent,
            1, 1, DEFAULT_MAIN_PERCENT)

        mDragModeUpPercent =
            typedArray.getFraction(R.styleable.DesktopLayerLayout_mDragModeUpPercent,
                1, 1, DEFAULT_DRAG_MODE_UP_Percent_PERCENT)

        mOffscreenPageLimit =
            typedArray.getInteger(R.styleable.DesktopLayerLayout_mOffscreenPageLimit,
                OFFSCREEN_PAGE_LIMIT_DEFAULT)

        typedArray.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 根据屏幕变化修改方向，自适应
        if (isAutoFitOrientation) {
            mOrientation = if (w > h) ORIENTATION_HORIZONTAL else ORIENTATION_VERTICAL
            requestLayout()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        // 不需要来自的XML的view
        removeAllViewsInLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {

    }

    /**
     * 页面适配器
     */
    class LayerAdapter {


    }
}