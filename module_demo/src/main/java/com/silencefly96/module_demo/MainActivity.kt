package com.silencefly96.module_demo

import android.os.Bundle
import android.view.View
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_demo.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }
}