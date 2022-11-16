package com.silencefly96.module_demo

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_common.view.PatternLockView
import com.silencefly96.module_demo.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        //禁用沉浸状态栏
        isSteepStatusBar = false
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.d(TAG, "onSaveInstanceState()")
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        Log.d(TAG, "onRestoreInstanceState()")
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun doBusiness(context: Context) {
        //startActivity(Intent(this, PlanActivity::class.java))
        Log.e("TAG", "doBusiness: ")

    }

}