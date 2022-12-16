@file:Suppress("unused")

package com.silencefly96.module_views.custom.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_views.databinding.FragmentPieChartBinding
import com.silencefly96.module_base.base.BaseFragment

class PieChartViewDemo: BaseFragment() {

    private var _binding: FragmentPieChartBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentPieChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        binding.hhView.data = arrayListOf(1, 2, 3, 4 ,5)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}