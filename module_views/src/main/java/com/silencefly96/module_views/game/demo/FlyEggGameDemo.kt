@file:Suppress("unused")

package com.silencefly96.module_views.game.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_views.databinding.FragmentGameFlyEggBinding

class FlyEggGameDemo: BaseFragment() {

    private var _binding: FragmentGameFlyEggBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentGameFlyEggBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {

    }

    override fun onDestroyView() {
        binding.gamaView.recycle()
        super.onDestroyView()
        _binding = null
    }
}