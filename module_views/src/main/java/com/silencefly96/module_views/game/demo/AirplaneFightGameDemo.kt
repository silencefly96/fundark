@file:Suppress("unused")

package com.silencefly96.module_views.game.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_views.databinding.FragmentAirplaneGameBinding

class AirplaneFightGameDemo: BaseFragment() {

    private var _binding: FragmentAirplaneGameBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentAirplaneGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {

    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 资源回收
        binding.gameView.recycle()
        _binding = null
    }
}