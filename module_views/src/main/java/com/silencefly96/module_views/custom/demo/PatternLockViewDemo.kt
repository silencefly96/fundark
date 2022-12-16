@file:Suppress("unused")

package com.silencefly96.module_views.custom.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_views.custom.PatternLockView
import com.silencefly96.module_views.databinding.FragmentPatternLockBinding
import com.silencefly96.module_base.base.BaseFragment

class PatternLockViewDemo: BaseFragment() {

    private var _binding: FragmentPatternLockBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentPatternLockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        for (i in intArrayOf(0, 1 ,2, 4, 6, 7, 8)) {
            binding.hhView.preData.add(i)
        }
        binding.hhView.listener = object : PatternLockView.OnMoveUpListener {
            override fun onMoveUp(success: Boolean) {
                showToast(if (success) "验证成功！" else "验证失败！")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}