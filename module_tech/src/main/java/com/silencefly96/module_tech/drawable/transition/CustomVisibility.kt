@file:Suppress("unused")

package com.silencefly96.module_tech.drawable.transition

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.transition.TransitionValues
import android.transition.Visibility
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener

class CustomVisibility: Visibility() {

    // 用于控制view出现时的外观变化，类似transition功能
    override fun onAppear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues,
        endValues: TransitionValues
    ): Animator {
        return super.onAppear(sceneRoot, view, startValues, endValues)
    }

    // 提供了更直接的方式来访问视图的可见性状态，未实现的话，会继续调用上面onAppear方法
    override fun onAppear(
        sceneRoot: ViewGroup,
        startValues: TransitionValues?,
        startVisibility: Int,
        endValues: TransitionValues,
        endVisibility: Int
    ): Animator {
        // 获取要操作的View，注意startValues可能为null
        val view = endValues.view

        // 加日志可以看到受影响的view，可根据ID排除
         Log.d("TAG", "onAppear: view = $view")
        // view = android.view.View{... #1020030 android:id/navigationBarBackground}
        // 比如: excludeTarget(android.R.id.navigationBarBackground, true)

        // 将自定义动画应用于目标视图
        return AnimatorSet().apply {
            duration = 2000
            when(startVisibility) {
                // 从GONE变为显示
                View.GONE -> playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(view, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(view, "scaleY", 0f, 1f)
                )
                // 从INVISIBLE变为显示
                View.INVISIBLE -> playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                    AnimatorSet().apply {
                        playSequentially(
                            ObjectAnimator.ofFloat(view, "rotation", 0f, 180f),
                            ObjectAnimator.ofFloat(view, "rotation", 180f, 0f),
                        )
                    }
                )
                // 当view被添时，startVisibility=-1，不加旋转
                else -> {
                    playTogether(
                        ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                        ObjectAnimator.ofFloat(view, "scaleX", 0f, 1f),
                        ObjectAnimator.ofFloat(view, "scaleY", 0f, 1f),
                    )
                }
            }
        }
    }

    override fun onDisappear(
        sceneRoot: ViewGroup,
        startValues: TransitionValues,
        startVisibility: Int,
        endValues: TransitionValues?,
        endVisibility: Int
    ): Animator {
        // 获取要操作的View，注意endValues可能为null
        val view = endValues?.view ?: startValues.view

        // 这里要阻止View直接变成endVisibility，在动画结束后再设置
        // 通过日志可以看出这里设置visibility不会再触发onAppear、onDisappear
        Log.d("TAG", "onDisappear: view = $view")
        view.visibility = startVisibility

        return AnimatorSet().apply {
            duration = 2000
            addListener(onEnd = {
                view.visibility = endVisibility
            })
            when(endVisibility) {
                // 最终变为GONE
                View.GONE -> playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(view, "scaleX", 1f, 0f),
                    ObjectAnimator.ofFloat(view, "scaleY", 1f, 0f),
                )
                // 最终变为INVISIBLE
                View.INVISIBLE -> playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
                )
                // 当view被remove的时候，endVisibility=-1，不加旋转
                else -> {
                    playTogether(
                        ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
                        ObjectAnimator.ofFloat(view, "scaleX", 1f, 0f),
                        ObjectAnimator.ofFloat(view, "scaleY", 1f, 0f)
                    )
                }
            }
        }
    }
}