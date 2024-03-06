@file:Suppress("unused")

package com.silencefly96.module_tech.drawable.demo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_tech.databinding.FragmentVectorTestBinding


class VectorAnimTestDemo: BaseFragment() {

    private var _binding: FragmentVectorTestBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentVectorTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun doBusiness(context: Context?) {
        // SVG轨迹动画，取巧用一下TextView的drawableBottomCompat:
        val drawable = binding.animatedVectorPath.compoundDrawables[3] as Animatable
        drawable.start()
        binding.animatedVectorPath.setOnClickListener {
            if (!drawable.isRunning) drawable.start()
        }

        // SVG路径动画，从一种形状变成另一种形状:
        val drawable1 = binding.animatedVectorMorphing.compoundDrawables[3] as Animatable
        drawable1.start()
        binding.animatedVectorMorphing.setOnClickListener {
            if (!drawable1.isRunning) drawable1.start()
        }

        // SVG多种动画，从一种形状变成另一种形状:
        val drawable2 = binding.animatedVectorCombine.compoundDrawables[3] as Animatable
        // 一开始有问题
        binding.animatedVectorCombine.post {
            drawable2.start()
        }
        binding.animatedVectorCombine.setOnClickListener {
            if (!drawable2.isRunning) drawable2.start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}