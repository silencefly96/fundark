package com.silencefly96.module_base.base

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import java.util.*

/**
 * 多类型，RecyclerView 列表适配器简化
 * @author silence
 * @date 2019-08-27
 */
@Suppress("unused")
abstract class BaseMultiRecyclerAdapter(items: List<Any>?) : RecyclerView.Adapter<ViewHolder>() {

    //需要存储不同类型的对象
    private val mItems: List<Any> = items ?: ArrayList()

    //点击事件回调接口
    private var mItemClickListener: ItemClickListener? = null
    private var mItemLongClickListener: ItemLongClickListener? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        //获取资源文件id
        val mItemLayoutRes = convertType(viewType)

        //根据给到的不同资源文件id，创建不同的view
        val view = LayoutInflater.from(parent.context).inflate(mItemLayoutRes, parent, false)

        //根据view创建多功能view holder
        val viewHolder = ViewHolder(view)

        // 初始化Item事件监听
        initOnItemListener(viewHolder)
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        //所有对象都继承自object
        val o = mItems[position]
        //绑定数据到布局
        convertView(holder, o, getItemViewType(position))
    }

    override fun getItemCount(): Int {
        return mItems.size
    }

    //将重写getItemViewType获得的类型转换成布局
    @LayoutRes
    abstract fun convertType(viewType: Int): Int

    //关键--外部将数据绑定到布局去
    abstract fun convertView(viewHolder: ViewHolder, itemObj: Any, viewType: Int)

    //设置点击事件
    private fun initOnItemListener(holder: RecyclerView.ViewHolder) {
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

    fun setOnItemClickListener(listener: ItemClickListener?) {
        mItemClickListener = listener
    }

    fun setOnItemLongClickListener(listener: ItemLongClickListener?) {
        mItemLongClickListener = listener
    }

    interface ItemClickListener {
        fun onItemClick(view: View?, itemObj: Any?, position: Int)
    }

    interface ItemLongClickListener {
        fun onItemLongClick(view: View?, itemObj: Any?, position: Int)
    }
}