@file:Suppress("unused")

package com.example.module_views.custom.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.module_views.custom.HexagonRankView
import com.example.module_views.databinding.FragmentHexagonRankBinding
import com.silencefly96.module_base.base.BaseFragment

class HexagonRankViewDemo: BaseFragment() {

    private var _binding: FragmentHexagonRankBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentHexagonRankBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        for (i in 0..5) {
            val point = HexagonRankView.PointInfo("name$i", (Math.random() * 100).toInt())
            binding.hhView.data.add(point)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}