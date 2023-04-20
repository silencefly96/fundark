package com.silencefly96.fundark

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.silencefly96.fundark.databinding.ActivityMainBinding
import com.silencefly96.module_base.base.BaseActivity

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, TestActivityA::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}