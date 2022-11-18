@file:Suppress("unused")

package com.example.module_views.custom.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.module_views.databinding.FragmentScrollingCollapseBinding
import com.silencefly96.module_base.base.BaseFragment

class ScrollingCollapseTopDemo: BaseFragment() {

    private var _binding: FragmentScrollingCollapseBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentScrollingCollapseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}