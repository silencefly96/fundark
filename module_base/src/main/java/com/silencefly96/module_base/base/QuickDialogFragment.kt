package com.silencefly96.module_base.base

import android.content.Context
import androidx.annotation.LayoutRes
import android.os.Bundle
import androidx.annotation.FloatRange
import androidx.annotation.StyleRes
import android.content.DialogInterface
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

/**
 * 超简单、快速的对话框使用
 * @author fdk
 * @date 2021/07/14
 */
@Suppress("unused")
class QuickDialogFragment<T, R>(

    /**
     * 布局ID
     */
    @field:LayoutRes @param:LayoutRes private val mLayoutResId: Int,

    /**
     * 传入数据
     */
    private val param: T

) : DialogFragment() {

    /**
     * 传出数据
     */
    private var result: R? = null

    /**
     * 是否背景透明
     */
    private var isTransparent = false

    /**
     * 是否底部显示
     */
    private var isAlignBottom = false

    /**
     * 点击返回键取消
     */
    private var isOutCancelable: Boolean = true

    /**
     * 背景昏暗度
     */
    private var mDimAmount = 0.5f

    /**
     * 左右边距
     */
    private var mMargin = 0

    /**
     * 进入退出动画
     */
    private var mAnimStyle = 0

    /**
     * 对话框的主题
     */
    private var mThemeStyle = 0

    /**
     * 对话框宽度
     */
    private var mWidth = 0

    /**
     * 对话框高度
     */
    private var mHeight = 0

    /**
     * 核心接口，绑定对话框布局里的数据、点击效果等
     */
    private var onConvertListener: OnConvertViewListener<T, R>? = null

    /**
     * 对话框消失时回调，可以获取结果
     */
    private var onDismissListener: OnDismissListener<R?>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(mLayoutResId, container, false)
        onConvertListener!!.convertView(ViewHolder.create(view), this, param)
        return view
    }

    override fun onStart() {
        super.onStart()
        initParams()
    }

    /**
     * 初始化参数
     */
    private fun initParams() {
        val window = dialog!!.window
        if (window != null) {
            val params = window.attributes
            params.dimAmount = mDimAmount

            //设置dialog显示位置
            if (isAlignBottom) {
                params.gravity = Gravity.BOTTOM
            }

            //设置dialog的主题
            if (mThemeStyle != 0) {
                setStyle(STYLE_NO_TITLE, mThemeStyle)
            }
            if (null != context) {

                //设置dialog宽度
                if (mWidth == 0) {
                    params.width = getScreenWidth(context) - 2 * dp2px(
                        context, mMargin.toFloat()
                    )
                } else {
                    params.width = dp2px(context, mWidth.toFloat())
                }

                //设置dialog高度
                if (mHeight == 0) {
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                } else {
                    params.height = dp2px(context, mHeight.toFloat())
                }
            }

            //设置dialog动画
            if (mAnimStyle != 0) {
                window.setWindowAnimations(mAnimStyle)
            }

            //设置背景透明
            if (isTransparent) {
                window.setBackgroundDrawableResource(android.R.color.transparent)
            }
            window.attributes = params
        }

        //设置是否可以取消
        isCancelable = isOutCancelable
    }

    /**
     * 设置背景昏暗度
     *
     * @param dimAmount 设置背景昏暗度
     * @return dialog
     */
    fun setDimAmount(@FloatRange(from = 0.0, to = 1.0) dimAmount: Float): QuickDialogFragment<T, R> {
        mDimAmount = dimAmount
        return this
    }

    /**
     * 是否显示底部
     *
     * @param alignBottom 是否显示底部
     * @return dialog
     */
    fun setAlignBottom(alignBottom: Boolean): QuickDialogFragment<T, R> {
        isAlignBottom = alignBottom
        return this
    }

    /**
     * 是否透明
     *
     * @param transparent 是否透明
     * @return dialog
     */
    fun setTransparent(transparent: Boolean): QuickDialogFragment<T, R> {
        isTransparent = transparent
        return this
    }

    /**
     * 设置宽高
     *
     * @param width 长度
     * @param height 高度
     * @return dialog
     */
    fun setSize(width: Int, height: Int): QuickDialogFragment<T, R> {
        mWidth = width
        mHeight = height
        return this
    }

    /**
     * 设置左右margin
     *
     * @param margin margin
     * @return dialog
     */
    fun setMargin(margin: Int): QuickDialogFragment<T, R> {
        mMargin = margin
        return this
    }

    /**
     * 设置进入退出动画
     *
     * @param animStyle anim
     * @return dialog
     */
    fun setAnimStyle(@StyleRes animStyle: Int): QuickDialogFragment<T, R> {
        mAnimStyle = animStyle
        return this
    }

    /**
     * 设置主题
     *
     * @param mThemeStyle style
     * @return dialog
     */
    fun setThemeStyle(@StyleRes mThemeStyle: Int): QuickDialogFragment<T, R> {
        this.mThemeStyle = mThemeStyle
        return this
    }

    /**
     * 设置是否点击外部取消
     *
     * @param outCancel 是否点击外部取消
     * @return dialog
     */
    fun setOutCancel(outCancel: Boolean): QuickDialogFragment<T, R> {
        isOutCancelable = outCancel
        return this
    }

    /**
     * 设置传出数据
     *
     * @param result 导出数据
     */
    fun setResult(result: R) {
        this.result = result
    }

    /**
     * 显示dialog
     *
     * @param manager manager
     */
    fun show(manager: FragmentManager?) {
        super.show(manager!!, System.currentTimeMillis().toString())
    }

    /**
     * 设置转换接口
     * @param onConvertListener 转换接口
     * @return dialog
     */
    fun setOnConvertListener(
        onConvertListener: OnConvertViewListener<T, R>
    ): QuickDialogFragment<T, R> {
        this.onConvertListener = onConvertListener
        return this
    }

    /**
     * 设置对话框消失监听接口
     * @param listener 对话框消失监听接口
     * @return dialog
     */
    fun setOnDismissListener(listener: OnDismissListener<R?>?): QuickDialogFragment<T, R> {
        onDismissListener = listener
        return this
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (onDismissListener != null) {
            onDismissListener!!.onDialogDismiss(result)
        }
    }

    /**
     * 转换接口
     */
    interface OnConvertViewListener<T, R> {
        /**
         * 绑定数据到布局，设置布局点击事件等
         *
         * @param holder 通用的 ViewHolder
         * @param dialog 对话框
         * @param param 传入参数
         */
        fun convertView(holder: ViewHolder?, dialog: QuickDialogFragment<T, R>?, param: T)
    }

    /**
     * 消失接口
     */
    interface OnDismissListener<R> {
        /**
         * 对话框消失
         *
         * @param result 对话框中传递的结果数据
         */
        fun onDialogDismiss(result: R)
    }

    companion object {

        /**
         * 新建对话框方式，可继续调用相关设置参数函数，实现链式调用
         *
         * @param mLayoutResId 布局id
         * @param param 输入数据
         * @param convertViewListener 转换接口
         * @return 返回 dialog
         */
        fun <T, R> newInstance(
            @LayoutRes mLayoutResId: Int,
            param: T,
            convertViewListener: OnConvertViewListener<T, R>?
        ): QuickDialogFragment<T, R> {
            val dialog = QuickDialogFragment<T, R>(mLayoutResId, param)
            return dialog.setOnConvertListener(convertViewListener!!)
        }

        /**
         * 获取屏幕宽度
         *
         * @param context context
         * @return width
         */
        fun getScreenWidth(context: Context?): Int {
            val displayMetrics = context!!.resources.displayMetrics
            return displayMetrics.widthPixels
        }

        /**
         * 将 dp 值转成 px 值
         *
         * @param context context
         * @param dipValue dp
         * @return px
         */
        fun dp2px(context: Context?, dipValue: Float): Int {
            val scale = context!!.resources.displayMetrics.density
            return (dipValue * scale + 0.5f).toInt()
        }
    }
}