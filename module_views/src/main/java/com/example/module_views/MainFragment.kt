package com.example.module_views

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.module_views.databinding.FragmentMainBinding
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.BaseRecyclerAdapter
import com.silencefly96.module_base.base.ViewHolder

class MainFragment: BaseFragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    // 数据
    private val mData = arrayListOf<Item>().apply {
        add(Item(1, "左划删除列表: SlideDeleteRecyclerView",
            "继承RecyclerView实现左滑列表点击删除功能, 学习事件拦截机制、滑动、Scroller以及TouchSlop、VelocityTracker和GestureDetector等工具"))

        add(Item(2, "左划删除控件: LeftDeleteItemLayout",
            "继承View实现对控件左滑达到删除功能，学习自定义View、动态生成控件、kotlin构造函数、postDelayed函数"))

        add(Item(3, "带header和footer的Layout: HeaderFooterView",
            "继承ViewGroup实现滚动自动隐藏header和footer的控件，学习自定义ViewGroup、onMeasure、MeasureSpec、onLayout的使用"))

        add(Item(4, "滚动选择的控件: ScrollSelectView",
            "继承View实现滚动选择文字控件，学习onDraw的使用，并且了解下在XML自定义控件参数"))

        add(Item(5, "安卓侧滑栏: TwoLayerSlideLayout",
            "继承ViewGroup实现安卓侧滑栏，学习自定义LayoutParams、带padding和margin的measure和layout、利用requestLayout实现动画效果"))

        add(Item(6, "滚动折叠控件: ScrollingCollapseTopLayout",
            "继承ViewGroup实现折叠header效果，学习滑动事件冲突问题、更改view节点以及CoordinatorLayout事件传递"))

        add(Item(7, "滑动切换控件: DesktopLayerLayout",
            "继承ViewGroup实现模仿桌面切换的控件，综合使用下自定义view的知识"))

        add(Item(8, "六边形评分控件: HexagonRankView",
            "继承View实现六边形评分控件，学习一下onDraw的使用，罗列一下Paint的api"))

        add(Item(9, "多点触控扇形图: PieChartView",
            "继承View实现扇形图，支持单指旋转、二指放大、三指移动，四指以上同时按下进行复位，学习多点触控的使用"))

        add(Item(10, "贝塞尔曲线绘制小红点: RedDomView",
            "继承View实现拖拽小红点功能，学习一下贝塞尔曲线在onDraw中的使用"))

        add(Item(11, "滑动解锁九宫格控件: PatternLockView",
            "继承View实现九宫格解锁功能"))

        add(Item(12, "粒子线条控件: ParticleLinesBgView",
            "继承View实现粒子线条效果"))
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
                    1 -> R.id.action_main_to_slide_delete
                    2 -> R.id.action_main_to_left_delete
                    3 -> R.id.action_main_to_header_footer
                    4 -> R.id.action_main_to_scroll_select
                    5 -> R.id.action_main_to_layer_slide

                    6 -> R.id.action_main_to_scrolling_collapse
                    7 -> R.id.action_main_to_desktop_layer
                    8 -> R.id.action_main_to_hexagon_rank
                    9 -> R.id.action_main_to_pie_chart
                    10 -> R.id.action_main_to_red_dom

                    11 -> R.id.action_main_to_pattern_lock
                    12 -> R.id.action_main_to_particle_lines
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