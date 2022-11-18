@file:Suppress("unused")

package com.example.module_views.custom.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.module_views.custom.HeaderFooterView
import com.example.module_views.databinding.FragmentHeaderFooterBinding
import com.silencefly96.module_base.base.BaseFragment

class HeaderFooterViewDemo: BaseFragment() {

    private var _binding: FragmentHeaderFooterBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentHeaderFooterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        binding.hhView.onReachHeadListener = object : HeaderFooterView.OnReachHeadListener {
            override fun onReachHead() {
                showToast("到顶了...")
            }
        }
        binding.hhView.onReachFootListener = object : HeaderFooterView.OnReachFootListener {
            override fun onReachFoot() {
                showToast("我也是有底线的...")
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}