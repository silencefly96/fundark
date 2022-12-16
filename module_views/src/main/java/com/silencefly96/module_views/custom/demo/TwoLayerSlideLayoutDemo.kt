@file:Suppress("unused")

package com.silencefly96.module_views.custom.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_views.databinding.FragmentLayerSlideBinding
import com.silencefly96.module_base.base.BaseFragment

class TwoLayerSlideLayoutDemo: BaseFragment() {

    private var _binding: FragmentLayerSlideBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentLayerSlideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}