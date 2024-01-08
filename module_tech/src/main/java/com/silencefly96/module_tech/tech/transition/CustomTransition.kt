@file:Suppress("unused")

package com.silencefly96.module_tech.tech.transition

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.transition.Transition
import android.transition.TransitionValues
import android.view.ViewGroup


class CustomTransition : Transition() {
    private val rightValue = "rightValue"

    override fun captureStartValues(transitionValues: TransitionValues) {
        // 获得view，可以拿到起始属性值，保存到transitionValues.values去
        val view = transitionValues.view
        transitionValues.values[rightValue] = view.right
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        // 和上面一样，保持结束值
        val view = transitionValues.view
        transitionValues.values[rightValue] = view.right
    }

    override fun createAnimator(
        sceneRoot: ViewGroup,
        startValues: TransitionValues,
        endValues: TransitionValues
    ): Animator {

        val view = endValues.view
        val start = startValues.values[rightValue] as Int
        val end = endValues.values[rightValue] as Int

        // 将自定义动画应用于目标视图
        val animatorSet = AnimatorSet()
        val animatorSetOther = AnimatorSet()
        animatorSetOther.playSequentially(
            ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.5f),
            ObjectAnimator.ofFloat(view, "scaleY", 1.5f, 1f)
        )
        animatorSet.playTogether(
            ObjectAnimator.ofInt(view, "right", start, end),
            animatorSetOther
        )
        return animatorSet
    }

}