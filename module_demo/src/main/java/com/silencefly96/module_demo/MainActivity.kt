package com.silencefly96.module_demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_demo.databinding.ActivityMainBinding
import com.silencefly96.module_demo.plan.PlanActivity

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        //禁用沉浸状态栏
        isSteepStatusBar = false
        return binding.root
    }

    override fun doBusiness(context: Context) {
        startActivity(Intent(this, PlanActivity::class.java))
    }
}