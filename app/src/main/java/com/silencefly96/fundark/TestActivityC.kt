package com.silencefly96.fundark

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.silencefly96.fundark.databinding.ActivityTestBinding
import com.silencefly96.module_base.base.BaseActivity

class TestActivityC : BaseActivity() {
    private lateinit var binding: ActivityTestBinding

    override fun bindView(): View {
        binding = ActivityTestBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.button.setOnClickListener {
            startActivity(Intent(this, TestActivityD::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("TAG", "onNewIntent: TestActivityC")
    }
}