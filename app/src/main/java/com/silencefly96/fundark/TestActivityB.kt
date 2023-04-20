package com.silencefly96.fundark

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.silencefly96.fundark.databinding.ActivityTestBinding
import com.silencefly96.module_base.base.BaseActivity

class TestActivityB : BaseActivity() {
    private lateinit var binding: ActivityTestBinding

    override fun bindView(): View {
        binding = ActivityTestBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.button.setOnClickListener {
            startActivity(TestActivityC::class.java)
        }
        binding.button.setOnLongClickListener {
            this@TestActivityB.startActivity(
                Intent(this@TestActivityB,TestActivityB::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            true
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("TAG", "onNewIntent: TestActivityB")
    }
}