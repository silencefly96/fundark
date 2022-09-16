package com.silencefly96.module_demo.plan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.silencefly96.module_demo.R
import com.silencefly96.module_demo.databinding.FragmentPlanListBinding

class PlanListFragment(viewModel: PlanViewModel): Fragment() {

    private var _binding: FragmentPlanListBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlanListBinding.inflate(inflater, container, false)

        binding.text.text = getString(R.string.app_name)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(viewModel: PlanViewModel) = PlanListFragment(viewModel)
    }
}