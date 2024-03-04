@file:Suppress("unused")

package com.silencefly96.module_views.game.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
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
        // 设置辅助线开关
        binding.tipSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.gameView.isShowTip = isChecked
        }

        // 设置难度
        binding.configSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                binding.gameView.apply {
                    setGameLevel(position - 1)
                    reload(width, height)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        binding.gameView.recycle()
        super.onDestroyView()
        _binding = null
    }
}