@file:Suppress("unused")

package com.silencefly96.module_views.custom.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_views.databinding.FragmentParticleLinesBinding
import com.silencefly96.module_base.base.BaseFragment

class ParticleLinesBgViewDemo: BaseFragment() {

    private var _binding: FragmentParticleLinesBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentParticleLinesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}