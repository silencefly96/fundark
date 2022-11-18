@file:Suppress("unused")

package com.example.module_views.custom.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.module_views.R
import com.example.module_views.databinding.FragmentSlideDeleteBinding
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.BaseRecyclerAdapter
import com.silencefly96.module_base.base.ViewHolder

class SlideDeleteRecyclerViewDemo: BaseFragment() {

    private var _binding: FragmentSlideDeleteBinding? = null
    private val binding get() = _binding!!

    private val mData = arrayListOf(0, 1, 2, 3, 4, 5 ,6, 7, 8, 9)

    // 适配器
    private val mAdapter = object: BaseRecyclerAdapter<Int>(R.layout.item_slide_delete, mData) {
        override fun convertView(viewHolder: ViewHolder?, item: Int, position: Int) {
            viewHolder?.setText(R.id.order, position.toString())
            // viewHolder?.setText(R.id.title, item.toString())
            viewHolder?.setOnClickListener(R.id.delete) {
                // 删除数据并更新
                mData.remove(item)
                notifyItemChanged(position)
            }
        }
    }

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentSlideDeleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        // 设置列表
        binding.recycler.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        binding.recycler.adapter = mAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}