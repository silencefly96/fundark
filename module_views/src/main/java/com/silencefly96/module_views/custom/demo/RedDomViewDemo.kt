@file:Suppress("unused")

package com.silencefly96.module_views.custom.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_views.custom.RedDomView
import com.silencefly96.module_views.databinding.FragmentRedDomBinding
import com.silencefly96.module_base.base.BaseFragment

class RedDomViewDemo: BaseFragment() {

    private var _binding: FragmentRedDomBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentRedDomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        binding.hhView.domPercent = 0.05f
        binding.hhView.disappearPercent = 0.25f
        binding.hhView.listener = object : RedDomView.OnDisappearListener {
            override fun onDisappear() {
                showToast("小红点消失了")
            }
        }
        binding.hhView.setOnClickListener { binding.hhView.reset() }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}