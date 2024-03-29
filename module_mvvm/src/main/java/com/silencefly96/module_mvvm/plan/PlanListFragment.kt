package com.silencefly96.module_mvvm.plan

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.BaseRecyclerAdapter
import com.silencefly96.module_base.base.ViewHolder
import com.silencefly96.module_mvvm.R
import com.silencefly96.module_mvvm.databinding.FragmentPlanListBinding
import com.silencefly96.module_mvvm.plan.model.Plan

class PlanListFragment(val viewModel: PlanViewModel): BaseFragment() {

    private var _binding: FragmentPlanListBinding? = null
    //_binding可以位null，binding不为null，消除kotlin对null的判别
    private val binding get() = _binding!!

    //列表适配器
    private val adapter
        = object : BaseRecyclerAdapter<Plan>(R.layout.item_plan, viewModel.planList) {
            override fun convertView(viewHolder: ViewHolder?, item: Plan, position: Int) {
                viewHolder?.let {
                    it.setText(R.id.order, position.toString())
                    it.setText(R.id.title, item.title)
                }
        }
    }

    // 绑定布局
    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding =  DataBindingUtil.inflate(inflater,R.layout.fragment_plan_list, container, false)
        // _binding = FragmentPlanListBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun doBusiness(context: Context?) {
        // 设置好数据
        binding.viewmodel = viewModel
        // 21年的项目，22年才发现不更新是因为没有加lifecycleOwner
        binding.lifecycleOwner = viewLifecycleOwner

        //设置垂直列表
        binding.list.layoutManager = LinearLayoutManager(context)
            .apply { orientation = RecyclerView.VERTICAL }
        //设置列表适配器
        binding.list.adapter = adapter
        //点击列表项，修改选中id，触发请求数据
        adapter.setOnItemClickListener(object : BaseRecyclerAdapter.ItemClickListener<Plan> {
            override fun onItemClick(view: View?, itemObj: Plan, position: Int) {
                viewModel.id.value = position.toString()
            }
        })

        //第一次进来加载所有数据
        viewModel.getAll().observe(viewLifecycleOwner) { result ->
            //取得所有数据
            val plans = result.getOrNull()
            plans?.let {
                viewModel.planList.clear()
                viewModel.planList.addAll(it)
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //fragment的存在时间比其视图长，后续生命周期不需要视图了
        _binding = null
    }

    companion object {
        fun newInstance(viewModel: PlanViewModel) = PlanListFragment(viewModel)
    }
}