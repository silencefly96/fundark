package com.silencefly96.module_tech.tech.animator;

import android.content.Context
import android.view.View
import androidx.core.app.ActivityCompat
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_tech.databinding.ActivityAnimatorBinding

class AnimatorActivity : BaseActivity() {

    private lateinit var binding: ActivityAnimatorBinding

    override fun bindView(): View {
        binding = ActivityAnimatorBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun doBusiness(context: Context) {
        binding.sceneTransitionAnimation.setOnClickListener {
            ActivityCompat.finishAfterTransition(this)
        }
    }
}