package com.silencefly96.fundark

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.silencefly96.fundark.databinding.ActivityTestBinding
import com.silencefly96.module_base.base.BaseActivity

class TestActivityD : BaseActivity() {
    private lateinit var binding: ActivityTestBinding

    override fun bindView(): View {
        binding = ActivityTestBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.button.setOnClickListener {
            startActivity(TestActivityB::class.java)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("TAG", "onNewIntent: TestActivityD")
    }
}