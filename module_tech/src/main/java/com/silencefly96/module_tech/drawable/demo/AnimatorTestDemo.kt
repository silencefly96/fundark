@file:Suppress("unused")

package com.silencefly96.module_tech.drawable.demo

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.animation.Keyframe
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.TimeInterpolator
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.CycleInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.animation.addListener
import androidx.core.app.ActivityOptionsCompat
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_tech.R
import com.silencefly96.module_tech.databinding.FragmentAnimatorTestBinding
import com.silencefly96.module_tech.drawable.animator.AnimatorActivity
import kotlinx.coroutines.Runnable
import java.util.Deque
import java.util.LinkedList
import kotlin.math.pow
import kotlin.math.sin

class AnimatorTestDemo: BaseFragment() {

    private var _binding: FragmentAnimatorTestBinding? = null
    private val binding get() = _binding!!

    // 收集动画，在退出页面时集中销毁
    private val animatorCollector: MutableList<Animator> = ArrayList(8)

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentAnimatorTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n", "ObjectAnimatorBinding")
    override fun doBusiness(context: Context?) {
        // 通过XML实现
        val animatorSet = AnimatorInflater.loadAnimator(requireContext(), R.animator.animator_set_test)
        animatorSet.setTarget(binding.animatorSetTest)
        animatorSet.start()
        binding.animatorSetTest.setOnClickListener {
            // 和view动画不同，点击事件生效的位置和属性动画有关
            showToast("new position click work!")
        }
        animatorCollector.add(animatorSet)

        // 通过代码实现
        val animatorSet2 = AnimatorSet()
        val target = binding.animatorSetTest2
        animatorSet2.playSequentially(
            ObjectAnimator.ofFloat(target, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(target, "rotationX", 0f, 360f),
            ObjectAnimator.ofFloat(target, "rotationY", 0f, 360f),
            ObjectAnimator.ofFloat(target, "rotation", 0f, 360f),
            ObjectAnimator.ofFloat(target, "scaleX", 1f, 1.5f),
            ObjectAnimator.ofFloat(target, "scaleY", 1f, 1.5f),
            ObjectAnimator.ofFloat(target, "translationX", 0f, 90f),
            ObjectAnimator.ofFloat(target, "translationY", 0f, 90f),
            ObjectAnimator.ofFloat(target, "scaleX", 1.5f, 1f),
            ObjectAnimator.ofFloat(target, "scaleY", 1.5f, 1f),
            ObjectAnimator.ofFloat(target, "translationX", 90f, 0f),
            ObjectAnimator.ofFloat(target, "translationY", 90f, 0f),
            ObjectAnimator.ofFloat(target, "translationZ", 0f, 90f),
            ObjectAnimator.ofFloat(target, "translationZ", 90f, 0f).setDuration(3000),
        )
        // 注意translationY和y属性的区别
        // 移动出边界记得使用clipChildren、clipToPadding(父容器、祖父级容器)
        animatorSet2.setDuration(1000).start()
        target.setOnClickListener {
            if (animatorSet2.isRunning) {
                animatorSet2.end()
                showToast("end animatorSetTest2, start new turn!")
            }
            animatorSet2.start()
        }
        animatorCollector.add(animatorSet2)


        // ViewPropertyAnimator
        binding.viewPropertyAnimator.animate()
            .translationX(100f)
            .rotationX(360f)
            .setDuration(2000)
            .withEndAction(Runnable {
                // showToast("ViewPropertyAnimator EndAction act!")
            })
            .start()
        binding.viewPropertyAnimator.setOnClickListener {
            // By可以多次触发，不带By只触发一次
            binding.viewPropertyAnimator.animate()
                .translationXBy(20f)
                .rotation(180f)
                .setDuration(1000)
                .start()
        }

        // ValueAnimator使用
        val valueAnimator = ValueAnimator.ofFloat(0f, 500f)
        valueAnimator.repeatCount = 1
        valueAnimator.repeatMode = ValueAnimator.REVERSE
        valueAnimator.duration = 10000
        // 更新
        valueAnimator.addUpdateListener {
            val value = it.animatedValue as Float
            binding.valueAnimator.translationX = value
        }
        // 添加监听器，提供了可选监听器
        valueAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator?) {}
            override fun onAnimationEnd(animation: Animator?) {}
            override fun onAnimationCancel(animation: Animator?) {}
            override fun onAnimationRepeat(animation: Animator?) {
                showToast("ValueAnimator repeat")
            }
        })
        valueAnimator.addListener(onEnd = {
            showToast("ValueAnimator end")
        })
        binding.valueAnimator.setOnClickListener {
            valueAnimator.start()
        }
        animatorCollector.add(valueAnimator)

        // 时间插值器，写个抛物线吧: y = -4 * (x - 0.5)^2 + 1，过三个点(0,0)(0.5,1)(1,0)
        // 根据时间的百分比计算属性的百分比
        val timeInterpolator =
            TimeInterpolator { input -> -4f * (input - 0.5).pow(2.0).toFloat() + 1 }
        val animator = ObjectAnimator.ofFloat(binding.timeInterpolator,
            "translationX", 0f, 300f)
        animator.interpolator = timeInterpolator
        animator.setDuration(3000).start()
        binding.timeInterpolator.setOnClickListener {
            animator.start()
        }
        animatorCollector.add(animator)

        // 类型估值器，写个正弦函数: x = x, y = height * sin 2 * PI * x
        // 根据属性的百分比确定属性的值
        val height = 100f
        val typeEvaluator = TypeEvaluator<Pair<Float, Float>> { fraction, startValue, endValue ->
            // x轴上线性移动
            val fx = (endValue.first - startValue.first) * fraction

            // y轴在线性移动上上，叠加正弦波动
            val dy = height * sin(2 * Math.PI * fraction).toFloat()
            val fy = (endValue.second - startValue.second) * fraction + dy
            return@TypeEvaluator Pair<Float, Float>(fx, fy)
        }
        val animator2 = ValueAnimator.ofObject(typeEvaluator, Pair(0f, 0f), Pair(300f, 0f))
        // 根据pair的值更新位置坐标
        animator2.addUpdateListener {
            val value = it.animatedValue as Pair<*, *>
            binding.typeEvaluator.translationX = value.first as Float
            binding.typeEvaluator.translationY = value.second as Float
        }
        animator2.setDuration(5000).start()
        binding.typeEvaluator.setOnClickListener {
            animator2.start()
        }
        animatorCollector.add(animator2)

        // 关键帧
        val frame1 = Keyframe.ofFloat(0f, 0f)
        // 结束时回弹一下
        frame1.interpolator = OvershootInterpolator()
        val frame2 = Keyframe.ofFloat(0.25f, 300f)
        // 开始回拉一下
        frame2.interpolator = AnticipateInterpolator()
        val frame3 = Keyframe.ofFloat(0.5f, 0f)
        // 结束时Q弹一下
        frame3.interpolator = BounceInterpolator()
        val frame4 = Keyframe.ofFloat(0.75f, 300f)
        // 来回循环，实际就是正弦，传入参数是执行次数(影响的是前面的动画，在这是0.5f-0.75f的效果)
        frame4.interpolator = CycleInterpolator(2f)
        val frame5 = Keyframe.ofFloat(1f, 0f)
        val holder = PropertyValuesHolder.ofKeyframe("translationX", frame1, frame2, frame3, frame4, frame5)
        // 这里还能转换属性类型
        // holder.setConverter()
        val animator3 = ObjectAnimator.ofPropertyValuesHolder(binding.keyframe, holder)
        animator3.setDuration(4 * 5000).start()
        animator3.addUpdateListener {
            // 根据时段添加说明
            val addedStr = when(it.animatedFraction) {
                in 0f..0.25f -> "OvershootInterpolator"
                in 0.25f..0.5f -> "AnticipateInterpolator"
                in 0.5f..0.75f -> "BounceInterpolator"
                in 0.75f..1.0f -> "CycleInterpolator"
                else -> ""
            }
            binding.keyframe.text = "Keyframe(click): $addedStr"
        }
        binding.keyframe.setOnClickListener {
            animator3.start()
        }
        animatorCollector.add(animator3)

        // PropertyValuesHolder，就是一个没有目标的动画吧
        val holder1 = PropertyValuesHolder.ofFloat("alpha", 0f, 1f)
        val holder2 = PropertyValuesHolder.ofFloat("rotation", 0f, 360f)
        val holder3 = PropertyValuesHolder.ofFloat("translationX", 0f, 100f)
        val animator4 = ObjectAnimator.ofPropertyValuesHolder(binding.propertyValuesHolder, holder1,holder2, holder3)
        animator4.repeatMode = ObjectAnimator.REVERSE
        animator4.setDuration(3000).start()
        binding.propertyValuesHolder.setOnClickListener {
            animator4.start()
        }
        animatorCollector.add(animator4)

        // LayoutTransition
        val layoutTransition = LayoutTransition()
        val animatorAdd = ObjectAnimator.ofFloat(null, "scaleX", 0f, 1f)
        val animatorRmv = ObjectAnimator.ofFloat(null, "scaleX", 1f, 0f)
        // CHANGE_APPEARING和CHANGE_DISAPPEARING需要使用PropertyValuesHolder设置动画
        // 参考文章: https://www.cnblogs.com/yongdaimi/p/7993226.html
        val pvhLeft = PropertyValuesHolder.ofInt("left", 0, 0)
        val pvhTop = PropertyValuesHolder.ofInt("top", 0, 0)
        val pvhScaleAddY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.5f, 1f)
        val pvhScaleRmvY = PropertyValuesHolder.ofFloat("scaleY", 1f, 0.5f, 1f)
        // pvhLeft和pvhTop一定需要，而且开始属性值和结尾属性值要相同
        val animAddOther = ObjectAnimator.ofPropertyValuesHolder(
            binding.layoutTransition, pvhLeft, pvhTop, pvhScaleAddY)
        val animRmvOther = ObjectAnimator.ofPropertyValuesHolder(
            binding.layoutTransition, pvhLeft, pvhTop, pvhScaleRmvY)
        // 元素在容器中出现时所定义的动画。
        layoutTransition.setAnimator(LayoutTransition.APPEARING, animatorAdd)
        // 元素在容器中消失时所定义的动画。
        layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, animatorRmv)
        // 由于容器中要显现一个新的元素，其它需要变化的元素所应用的动画
        layoutTransition.setAnimator(LayoutTransition.CHANGE_APPEARING, animAddOther)
        // 当容器中某个元素消失，其它需要变化的元素所应用的动画
        layoutTransition.setAnimator(LayoutTransition.CHANGE_DISAPPEARING, animRmvOther)
        // 设置layoutTransition
        binding.layoutTransition.layoutTransition = layoutTransition
        // 点击刷新动画
        val removedViews: Deque<View> = LinkedList()
        binding.layoutTransition.setOnClickListener {
            removedViews.poll()?.let {
                binding.layoutTransition.addView(it)
            }
        }
        // 长按删除第一个
        binding.layoutTransition.setOnLongClickListener {
            if (binding.layoutTransition.childCount > 1) {
                val first = binding.layoutTransition.getChildAt(0)
                removedViews.offer(first)
                binding.layoutTransition.removeView(first)
                return@setOnLongClickListener true
            }
            return@setOnLongClickListener false
        }

        // 共享元素过渡动画
        binding.sceneTransitionAnimation.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 21) {
                // iv是当前点击的图片  share字符串是第二个activity布局中设置的**transitionName**属性
                val optionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(),
                    binding.sceneTransitionAnimation,
                    "sceneTransition")
                val intent = Intent(requireContext(), AnimatorActivity::class.java)
                startActivity(intent, optionsCompat.toBundle())
            }else {
                showToast("当前系统版本不支持: ${Build.VERSION.SDK_INT}")
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 退出时关闭动画，否则会闪退
        animatorCollector.forEach {
            it.end()
        }
        _binding = null
    }
}