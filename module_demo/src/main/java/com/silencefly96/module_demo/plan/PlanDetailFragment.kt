package com.silencefly96.module_demo.plan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_demo.databinding.FragmentPlanDetailBinding

class PlanDetailFragment(val viewModel: PlanViewModel): BaseFragment() {

    private var _binding: FragmentPlanDetailBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlanDetailBinding.inflate(inflater, container, false)
        //监测选中plan的变化
        viewModel.plan.observe(viewLifecycleOwner) { result ->
            result.getOrNull()?.let {
                viewModel.title = it.title
                viewModel.content = it.content
                //TODO 实现绑定
//                binding.title.text = it.title
//                binding.content.text = it.content
            }
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(viewModel: PlanViewModel) = PlanDetailFragment(viewModel)
    }


    interface OnOperateListener {
        fun all()
        fun add()
        fun delete()
        fun query()
        fun update()
    }
}