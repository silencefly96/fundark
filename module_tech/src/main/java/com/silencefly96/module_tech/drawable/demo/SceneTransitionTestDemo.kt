@file:Suppress("unused")

package com.silencefly96.module_tech.drawable.demo

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.transition.AutoTransition
import android.transition.Scene
import android.transition.Slide
import android.transition.TransitionInflater
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.widget.LinearLayout.LayoutParams
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.contains
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_tech.R
import com.silencefly96.module_tech.databinding.FragmentSceneTransitionTestBinding
import com.silencefly96.module_tech.drawable.animation.AnimationActivity
import com.silencefly96.module_tech.drawable.animator.AnimatorActivity
import com.silencefly96.module_tech.drawable.scene.SceneTransitionActivity
import com.silencefly96.module_tech.drawable.transition.CustomTransition
import com.silencefly96.module_tech.drawable.transition.CustomVisibility
import java.util.Deque
import java.util.LinkedList


class SceneTransitionTestDemo: BaseFragment() {

    private var _binding: FragmentSceneTransitionTestBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentSceneTransitionTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n", "ObjectAnimatorBinding")
    override fun doBusiness(context: Context?) {
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

        // Activity过渡动画(Android5之前使用)
        // overridePendingTransition在Android14被废弃，推荐使用overrideActivityTransition，需要改compileSdkVersion吧
        binding.activityAnimation.setOnClickListener {
            startActivity(AnimationActivity::class.java)
            requireActivity().overridePendingTransition(R.anim.anim_enter_activity, R.anim.anim_exit_activity)
        }

        // Activity过渡动画(Android5之后使用)
        binding.sceneTransition.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val intent = Intent(requireContext(), SceneTransitionActivity::class.java)

                // 传入过渡动画类型
                intent.putExtra("type", when(binding.sceneType.checkedRadioButtonId) {
                    R.id.explode -> "explode"
                    R.id.slide -> "slide"
                    R.id.fade -> "fade"
                    else -> "slide"
                })

                // Create an ActivityOptions to transition between Activities using cross-Activity
                // scene animations.
                val optionsCompat =
                    ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity())

                startActivity(intent, optionsCompat.toBundle())
            }else {
                showToast("当前系统版本不支持: ${Build.VERSION.SDK_INT}")
            }
        }

        // 共享元素过渡动画
        binding.sceneTransitionAnimation.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val intent = Intent(requireContext(), AnimatorActivity::class.java)

                // iv是当前点击的图片  share字符串是第二个activity布局中设置的**transitionName**属性
                val optionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(),
                    binding.sceneTransitionAnimation,
                    "sceneTransition")

                val bundle = optionsCompat.toBundle()
                // 多个过渡元素情况，记得import androidx.core.util.Pair
//                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
//                    requireActivity(),
//                    Pair(binding.sceneTransitionAnimation, "sceneTransition")
//                ).toBundle()

                startActivity(intent, bundle)
            }else {
                showToast("当前系统版本不支持: ${Build.VERSION.SDK_INT}")
            }
        }

        // 布局变化动画
        val firstScene = Scene.getSceneForLayout(binding.contentPanel,
            R.layout.layout_scene_first, requireContext())

        val secondScene = Scene.getSceneForLayout(binding.contentPanel,
            R.layout.layout_scene_second, requireContext())

        // ViewBinding无法拿到第二个layout中的id
        // binding.includeLayout.sceneFirst.setOnClickListener {
        var isFirst = true
        binding.contentPanel.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                TransitionManager.go(if(isFirst) secondScene else firstScene, Slide(Gravity.START))
                isFirst = !isFirst
            }
        }


        // 布局变化过渡动画(子view属性)
        var isFirst2 = true
        binding.sceneTransitionSystem.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 延迟启动root的变化
                TransitionManager.beginDelayedTransition(binding.root, AutoTransition())
                // 修改root内子View，设置变化
                binding.sceneTransitionSystem.layoutParams.apply {
                    width = if (isFirst2) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT
                }.also {
                    binding.sceneTransitionSystem.layoutParams = it
                }
                isFirst2 = !isFirst2
            }
        }


        // 过渡动画: TransitionSet
        // 继续用上面的布局变化动画，从XML中读取transition
        val firstScene2 = Scene.getSceneForLayout(binding.contentPanelSet,
            R.layout.layout_scene_first, requireContext())
        val secondScene2 = Scene.getSceneForLayout(binding.contentPanelSet,
            R.layout.layout_scene_second, requireContext())

        val transitionSet =
            TransitionInflater.from(requireContext()).inflateTransition(R.transition.transition_set)
        // 使用代码创建
//        TransitionSet().apply {
//            // 为目标视图滑动添加动画效果
//            addTransition(changeScroll())
//            // 为目标视图布局边界的变化添加动画效果
//            addTransition(ChangeBounds())
//            // 为目标视图裁剪边界的变化添加动画效果
//            addTransition(changeClipBounds())
//            // 为目标视图缩放和旋转方面的变化添加动画效果
//            addTransition(changeTransform())
//            // 为目标图片尺寸和缩放方面的变化添加动画效果
//            addTransition(ChangeImageTransform())
//
//            // 继承Visibility类，
//            addTransition(Slide())
//            addTransition(Explode())
//            addTransition(Fade(Fade.MODE_IN))
//        }

        var isFirst3 = true
        binding.contentPanelSet.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                TransitionManager.go(if(isFirst3) secondScene2 else firstScene2, transitionSet)
                isFirst3 = !isFirst3
            }
        }


        // 自定义transition
        var isFirst4 = true
        binding.customTransition.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 延迟启动root的变化，使用自定义transition
                TransitionManager.beginDelayedTransition(binding.root, CustomTransition())
                // 修改root内子View，设置变化
                binding.customTransition.layoutParams.apply {
                    width = if (isFirst4) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT
                }.also {
                    binding.customTransition.layoutParams = it
                }
                isFirst4 = !isFirst4
            }
        }

        // 过渡动画Activity: CustomVisibility
        binding.customVisibility.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val intent = Intent(requireContext(), SceneTransitionActivity::class.java)

                // 传入过渡动画类型
                intent.putExtra("type", "CustomVisibility")
                val optionsCompat =
                    ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity())

                startActivity(intent, optionsCompat.toBundle())
            }else {
                showToast("当前系统版本不支持: ${Build.VERSION.SDK_INT}")
            }
        }


        // 过渡动画Layout: CustomVisibility
        val textView = binding.customVisibilityText
        binding.customVisibilityType.setOnCheckedChangeListener { _, checkedId ->
            // 设置过渡动画
            TransitionManager.beginDelayedTransition(binding.root, CustomVisibility())
            // 触发
            textView.visibility = when(checkedId) {
                R.id.visiable -> View.VISIBLE
                R.id.invisible -> View.INVISIBLE
                R.id.gone -> View.GONE
                else -> View.VISIBLE
            }
        }
        binding.customVisibilityAdd.setOnCheckedChangeListener { _, checkedId ->
            // 设置过渡动画
            TransitionManager.beginDelayedTransition(binding.root, CustomVisibility())
            // 触发
            when(checkedId) {
                R.id.add -> {
                    if (!binding.visibilityContainer.contains(textView)) {
                        // 添加并不会触发Visibility的onAppear
                        textView.visibility = View.VISIBLE
                        binding.visibilityContainer.addView(textView)
                    }
                }
                R.id.remove -> {
                    if (binding.visibilityContainer.contains(textView)) {
                        // 可以触发onDisappear，但是无法出现过渡效果
                        binding.visibilityContainer.removeView(textView)
                    }
                }
                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}