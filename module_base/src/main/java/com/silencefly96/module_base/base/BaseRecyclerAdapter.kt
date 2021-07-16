package com.silencefly96.module_base.base

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import java.util.*

/**
 * RecyclerView 列表适配器简化
 * @author fdk
 * @date 2021/07/14
 */
@Suppress("unused")
abstract class BaseRecyclerAdapter<T>(
    //布局
    @param:LayoutRes
    private val mItemLayoutRes: Int,
    //数据
    items: List<T>?
) : RecyclerView.Adapter<ViewHolder>() {

    //数据
    private var mItems: List<T> = items ?: ArrayList()

    //点击事件回调接口
    private var mItemClickListener: ItemClickListener<T>? = null

    private var mItemLongClickListener: ItemLongClickListener<T>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            mItemLayoutRes, parent, false
        )

        //根据view创建多功能view holder
        val viewHolder = ViewHolder(view)

        // 初始化Item事件监听
        initOnItemListener(viewHolder)
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        //获取数据
        val item = mItems[position]
        //绑定数据到布局
        convertView(holder, item, position)
    }

    override fun getItemCount(): Int {
        return mItems.size
    }

    //关键--外部将数据绑定到布局去
    abstract fun convertView(viewHolder: ViewHolder?, item: T, position: Int)

    //设置点击事件
    private fun initOnItemListener(holder: ViewHolder) {
        if (mItemClickListener != null) {
            //根布局点击事件
            holder.itemView.setOnClickListener { v: View? ->
                val o = mItems[holder.layoutPosition]
                mItemClickListener!!.onItemClick(v, o, holder.layoutPosition)
            }
        }
        if (mItemLongClickListener != null) {
            //根布局长按事件
            holder.itemView.setOnLongClickListener { v: View? ->
                val o = mItems[holder.layoutPosition]
                mItemLongClickListener!!.onItemLongClick(v, o, holder.layoutPosition)
                true
            }
        }
    }

    fun setOnItemClickListener(listener: ItemClickListener<T>?) {
        mItemClickListener = listener
    }

    fun setOnItemLongClickListener(listener: ItemLongClickListener<T>?) {
        mItemLongClickListener = listener
    }

    interface ItemClickListener<T> {
        fun onItemClick(view: View?, itemObj: T, position: Int)
    }

    interface ItemLongClickListener<T> {
        fun onItemLongClick(view: View?, itemObj: T, position: Int)
    }

}