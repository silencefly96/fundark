package com.silencefly96.module_mvvm.plan

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_mvvm.databinding.FragmentPlanDetailBinding

class PlanDetailFragment(val viewModel: PlanViewModel): BaseFragment() {

    private var _binding: FragmentPlanDetailBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentPlanDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        //监测选中plan的变化
        viewModel.plan.observe(viewLifecycleOwner) { result ->
            result.getOrNull()?.let {
                binding.title.text = it.title
                binding.content.text = it.content
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(viewModel: PlanViewModel) = PlanDetailFragment(viewModel)
    }
}