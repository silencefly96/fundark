package com.silencefly96.module_tech.tech.scene

import android.content.Context
import android.os.Build
import android.transition.Explode
import android.transition.Fade
import android.transition.Slide
import android.view.View
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_tech.R
import com.silencefly96.module_tech.databinding.ActivityAnimationBinding
import com.silencefly96.module_tech.tech.transition.CustomVisibility


class SceneTransitionActivity : BaseActivity() {

    private lateinit var binding: ActivityAnimationBinding

    override fun bindView(): View {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // AppCompatActivity中window会被style影响，需要在style中设置下面两个属性
            // 启用窗口过渡属性;
            // window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)

            // 设置是否需动画覆盖，转场动画中动画之间会有覆盖的情况
            // 可以设置false来让动画有序的进入退出
            // window.allowEnterTransitionOverlap = false

            // 根据传入信息配置过渡动画
            when(intent.getStringExtra("type")) {
                "explode" -> Explode()
                "slide" -> Slide()
                "fade" -> Fade()
                else -> CustomVisibility()
            }.apply {
                duration = 500

                // 排除状态栏和导航栏(这两个还真有用！！！navigationBarBackground效果不明显)
                // 关于排除的id，可以自定义一个Visibility，在onAppear方法中用Log查看
                excludeTarget(android.R.id.statusBarBackground, true)
                excludeTarget(android.R.id.navigationBarBackground, true)
                // 排查标题栏
                excludeTarget(R.id.action_bar_container, true)
            }.also { transition ->
                // 退出当前界面的过渡动画
                window.exitTransition = transition
                // 进入当前界面的过渡动画
                window.enterTransition = transition
                // 重新进入界面的过渡动画
                window.reenterTransition = transition
            }
        }

        binding = ActivityAnimationBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun doBusiness(context: Context) {
        binding.button.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 带动画返回
                finishAfterTransition()
            }else {
                finish()
            }
        }
    }
}