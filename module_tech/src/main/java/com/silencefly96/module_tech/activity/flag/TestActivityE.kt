package com.silencefly96.module_tech.activity.flag

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_tech.databinding.ActivityTestBinding
import com.silencefly96.module_tech.activity.demo.ActivityFlagDemo

class TestActivityE : BaseActivity() {
    private lateinit var binding: ActivityTestBinding

    override fun bindView(): View {
        binding = ActivityTestBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityFlagDemo.addActivity(this)
        binding.button.setOnClickListener {
            startActivity(Intent(this, TestActivityE::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
        binding.button.setOnLongClickListener {
            this@TestActivityE.startActivity(
                Intent(this@TestActivityE, TestActivityB::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            true
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()

        val info = ActivityFlagDemo.getActivitiesInfo()
        // 输出信息
        binding.content.text =
            "current: TestActivityE\n" +
            "next: TestActivityE\n" +
            "taskAffinity: com.test.TestA\n" +
            "launchMode: \n" +
            "flags: \n" +
            "\t\tFLAG_ACTIVITY_NEW_TASK\n" +
            "\t\tFLAG_ACTIVITY_CLEAR_TOP\n" +
            "\t\tFLAG_ACTIVITY_SINGLE_TOP\n\n" +

            "long click next: TestActivityB\n" +
            "taskAffinity: com.test.TestA\n" +
            "launchMode: singleTop\n" +
            "flags:\n" +
            "\t\tFLAG_ACTIVITY_NEW_TASK\n" +
            "\t\tFLAG_ACTIVITY_CLEAR_TOP\n" +

            "current activity info:\n\n" +
            info
    }

    override fun onDestroy() {
        super.onDestroy()
        ActivityFlagDemo.removeActivity(this)
    }

    @SuppressLint("SetTextI18n")
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("TAG", "onNewIntent: TestActivityE")
        binding.content.postDelayed(Runnable {
            binding.content.text = "${binding.content.text}\n\nonNewIntent: TestActivityE"
        }, 500)
    }
}
