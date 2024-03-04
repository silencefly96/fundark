@file:Suppress("unused")

package com.silencefly96.module_views.game.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_views.databinding.FragmentAirplaneGameBinding
import com.silencefly96.module_views.databinding.FragmentBombBallGameBinding
import com.silencefly96.module_views.databinding.FragmentSnarkGameBinding

class BombBallGameDemo: BaseFragment() {

    private var _binding: FragmentBombBallGameBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentBombBallGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {

    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.gameView.recycle()
        _binding = null
    }
}