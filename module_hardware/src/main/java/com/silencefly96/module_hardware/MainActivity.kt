package com.silencefly96.module_hardware

import android.content.Context
import android.view.View
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_hardware.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun doBusiness(context: Context) {

    }

}