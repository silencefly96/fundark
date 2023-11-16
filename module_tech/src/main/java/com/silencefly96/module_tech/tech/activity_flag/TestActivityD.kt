package com.silencefly96.module_tech.tech.activity_flag

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_tech.databinding.ActivityTestBinding
import com.silencefly96.module_tech.tech.demo.ActivityFlagDemo

class TestActivityD : BaseActivity() {
    private lateinit var binding: ActivityTestBinding

    override fun bindView(): View {
        binding = ActivityTestBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityFlagDemo.addActivity(this)
        binding.button.setOnClickListener {
            startActivity(TestActivityE::class.java)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()

        val info = ActivityFlagDemo.getActivitiesInfo()
        // 输出信息
        binding.content.text =
            "current: TestActivityD\n" +
            "next: TestActivityE\n" +
            "taskAffinity: com.test.TestB\n" +
            "launchMode: \n" +
            "flags: \n" +
            "current activity info:\n\n" +
            info
    }

    override fun onDestroy() {
        super.onDestroy()
        ActivityFlagDemo.removeActivity(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("TAG", "onNewIntent: TestActivityD")
    }
}
