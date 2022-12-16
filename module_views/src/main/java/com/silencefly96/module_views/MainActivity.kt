package com.silencefly96.module_views

import android.content.Context
import android.view.View
import com.silencefly96.module_views.databinding.ActivityMainBinding
import com.silencefly96.module_base.base.BaseActivity

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun doBusiness(context: Context) {

    }

}