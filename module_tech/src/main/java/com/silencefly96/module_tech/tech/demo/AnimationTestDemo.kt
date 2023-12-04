@file:Suppress("unused")

package com.silencefly96.module_tech.tech.demo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_tech.R
import com.silencefly96.module_tech.databinding.FragmentAnimationTestBinding
import com.silencefly96.module_tech.tech.animation.AnimationActivity
import com.silencefly96.module_tech.tech.animation.Custom3dIkunAnimation

class AnimationTestDemo: BaseFragment() {

    private var _binding: FragmentAnimationTestBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentAnimationTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun doBusiness(context: Context?) {
        // View动画
        val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.anim_animation_set)
        binding.viewAnimation.startAnimation(animation)
        binding.viewAnimation.setOnClickListener {
            showToast("perform click!")
        }

        // View动画
        val ikunAnimation = Custom3dIkunAnimation(requireContext())
        ikunAnimation.duration = 10000
        ikunAnimation.repeatCount = 1
        ikunAnimation.repeatMode = Animation.REVERSE
        var isPlaying = false
        ikunAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                isPlaying = true
            }

            override fun onAnimationEnd(animation: Animation?) {
                isPlaying = false
            }

            override fun onAnimationRepeat(animation: Animation?) { }
        })
        binding.custom3dIkunAnimation.startAnimation(ikunAnimation)
        binding.custom3dIkunAnimation.setOnClickListener {
            if (!isPlaying) {
                binding.custom3dIkunAnimation.startAnimation(ikunAnimation)
            }
        }

        // 帧动画
        val frameAnimation = binding.frameAnimation.background as AnimationDrawable
        frameAnimation.isOneShot = true
        binding.frameAnimation.setOnClickListener {
            frameAnimation.start()
        }

        // LayoutAnimation
        val layoutAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.anim_layout_item)
        val control = LayoutAnimationController(layoutAnimation)
        control.delay = 0.15f
        control.order = LayoutAnimationController.ORDER_NORMAL
        binding.layoutAnimation.layoutAnimation = control
        // 点击刷新动画
        binding.layoutAnimation.setOnClickListener {
            // 注意每次播放要加上标记，配合invalidate才有动画
            binding.layoutAnimation.scheduleLayoutAnimation()
            binding.layoutAnimation.postInvalidate()
        }
        // 长按删除第一个
        binding.layoutAnimation.setOnLongClickListener {
            if (binding.layoutAnimation.childCount > 0) {
                binding.layoutAnimation.scheduleLayoutAnimation()
                binding.layoutAnimation.removeView(binding.layoutAnimation.getChildAt(0))
                return@setOnLongClickListener true
            }
            return@setOnLongClickListener false
        }

        // Activity动画
        binding.activityAnimation.setOnClickListener {
            startActivity(AnimationActivity::class.java)
            requireActivity().overridePendingTransition(R.anim.anim_enter_activity, R.anim.anim_exit_activity)
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}