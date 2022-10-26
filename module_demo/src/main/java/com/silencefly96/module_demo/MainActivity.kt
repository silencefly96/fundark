package com.silencefly96.module_demo

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.View
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_common.view.DesktopLayerLayout
import com.silencefly96.module_demo.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        //禁用沉浸状态栏
        isSteepStatusBar = false
        return binding.root
    }

    override fun doBusiness(context: Context) {
        //startActivity(Intent(this, PlanActivity::class.java))
    }

    // 需要在manifest中注册捕捉事件类型
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.e(TAG, "onConfigurationChanged: ")
        //binding.hhView.onConfigurationChanged(newConfig)
    }

}