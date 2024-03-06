package com.silencefly96.module_tech.drawable.animation;

import android.content.Context
import android.view.View
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_tech.R
import com.silencefly96.module_tech.databinding.ActivityAnimationBinding

class AnimationActivity : BaseActivity() {

    private lateinit var binding: ActivityAnimationBinding

    override fun bindView(): View {
        binding = ActivityAnimationBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun doBusiness(context: Context) {
        binding.button.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.anim_enter_activity, R.anim.anim_exit_activity)
        }
    }
}