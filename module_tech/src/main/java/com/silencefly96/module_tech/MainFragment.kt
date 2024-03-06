package com.silencefly96.module_tech

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.BaseRecyclerAdapter
import com.silencefly96.module_base.base.ViewHolder
import com.silencefly96.module_tech.databinding.FragmentMainBinding

class MainFragment: BaseFragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    // 数据
    private val mData = arrayListOf<Item>().apply {
        add(Item(1, "practice",
            "一些技术实践，没有特定方向"))

        add(Item(2, "drawable",
            "测试各种drawable、animation、animator、vector、过渡动画效果"))

        add(Item(3, "activity",
            "对activity的一些实践，比如生命周期、Intent Flag调用等"))

        add(Item(4, "service",
            "对后台服务、通知、JobScheduler、WorkManager的一些实践"))
    }

    // 适配器
    private val adapter = object: BaseRecyclerAdapter<Item>(R.layout.item_main, mData) {
        override fun convertView(viewHolder: ViewHolder?, item: Item, position: Int) {
            viewHolder?.setText(R.id.title, item.title)
            viewHolder?.setText(R.id.desc, item.desc)
        }
    }

    // 绑定布局
    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    // 业务
    override fun doBusiness(context: Context?) {
        // 设置列表
        binding.recycler.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        binding.recycler.adapter = adapter
        adapter.setOnItemClickListener(object : BaseRecyclerAdapter.ItemClickListener<Item> {
            override fun onItemClick(view: View?, itemObj: Item, position: Int) {
                // navigation跳转
                view?.findNavController()?.navigate(when(itemObj.index) {
                    1 -> R.id.action_main_to_practice
                    2 -> R.id.action_main_to_drawable
                    3 -> R.id.action_main_to_activity
                    4 -> R.id.action_main_to_service
                    else -> 0
                })
                // showToast("title: ${itemObj.title}")
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class Item(val index: Int, val title: String, val desc: String)
}