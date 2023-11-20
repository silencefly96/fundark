@file:Suppress("unused")

package com.silencefly96.module_tech.tech.demo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.RotateDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_tech.databinding.FragmentDrawableTestBinding
import com.silencefly96.module_tech.tech.drawable.CustomStarDrawable

class DrawableTestDemo: BaseFragment() {

    private var _binding: FragmentDrawableTestBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentDrawableTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    override fun doBusiness(context: Context?) {
        // 切换level
        binding.levelListDrawable.setOnClickListener {
            var level = binding.levelListDrawable.background.level
            level = ++level % 5
            binding.levelListDrawable.background.level = level
            binding.levelListDrawable.text = "LevelListDrawable: current level = $level"
        }

        // 启动transition
        binding.transitionDrawable.setOnClickListener {
            (binding.transitionDrawable.background as TransitionDrawable).startTransition(1500)
        }

        // 设置缩放
        binding.scaleDrawable.setOnClickListener {
            var level = binding.scaleDrawable.background.level
            level = (level + 1000) % 10000
            binding.scaleDrawable.background.level = level
            binding.scaleDrawable.text = "ScaleDrawable: current level = $level"
        }

        // 设置裁切
        binding.clipDrawable.setOnClickListener {
            var level = binding.clipDrawable.background.level
            level = (level + 1000) % 10000
            binding.clipDrawable.background.level = level
            binding.clipDrawable.text = "ClipDrawable: current level = $level"
        }

        // 设置自定义drawable
        binding.customDrawable.background = CustomStarDrawable()
        binding.customDrawable.setOnClickListener {
            var level = binding.customDrawable.background.level
            level = (level + 2000) % 10000
            binding.customDrawable.background.level = level
            binding.customDrawable.text = "CustomDrawable: current level = $level"
        }

        // 设置旋转drawable
        binding.rotateDrawable.setOnClickListener {
            var level = binding.rotateDrawable.background.level
            level = (level + 2000) % 10000
            binding.rotateDrawable.background.level = level
            binding.rotateDrawable.text = "RotateDrawable: current level = $level"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}