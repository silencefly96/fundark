package com.silencefly96.module_tech.drawable.demo

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

class DrawableMainFragment: BaseFragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    // 数据
    private val mData = arrayListOf<Item>().apply {
        add(Item(1, "drawable测试: DrawableTest",
            "查看各种drawable的展示效果"))

        add(Item(2, "animation测试: AnimationTest",
            "查看View动画(帧动画)的展示效果"))

        add(Item(3, "animator测试: AnimatorTest",
            "查看属性动画的展示效果"))

        add(Item(4, "VectorAnim测试: VectorAnimTest",
            "查看矢量的展示效果"))

        add(Item(5, "过渡动画测试: SceneTransitionTest",
            "查看各种过渡动画效果"))
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
                    1 -> R.id.action_main_to_drawable_test
                    2 -> R.id.action_main_to_animation_test
                    3 -> R.id.action_main_to_animator_test
                    4 -> R.id.action_main_to_vector_test
                    5 -> R.id.action_main_to_scene_transition_test
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