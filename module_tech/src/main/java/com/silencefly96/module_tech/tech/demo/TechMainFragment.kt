package com.silencefly96.module_tech.tech.demo

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
import com.silencefly96.module_tech.R

class TechMainFragment: BaseFragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    // 数据
    private val mData = arrayListOf<Item>().apply {
        add(Item(1, "Dns优化: DnsInterceptor",
            "对DNS的优化，ipv4优先、httpdns优化"))

        add(Item(2, "Activity flag测试: TestActivityA/B/C/D/E",
            "对Android启动模式、flag、taskAffinity进行测试"))

        add(Item(3, "drawable测试: DrawableTest",
            "查看各种drawable的展示效果"))
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
                    1 -> R.id.action_main_to_dns_test
                    2 -> R.id.action_main_to_activity_flag
                    3 -> R.id.action_main_to_drawable_test
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