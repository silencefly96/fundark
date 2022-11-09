package com.silencefly96.module_demo

import android.content.Context
import android.util.Log
import android.view.View
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_common.view.HexagonRankView
import com.silencefly96.module_common.view.PatternLockView
import com.silencefly96.module_common.view.RedDomView
import com.silencefly96.module_demo.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        //禁用沉浸状态栏
        isSteepStatusBar = false
        return binding.root
    }

    override fun doBusiness(context: Context) {
        //startActivity(Intent(this, PlanActivity::class.java))
        for (i in intArrayOf(0, 1 ,2, 4, 6, 7, 8)) {
            binding.hhView.preData.add(i)
        }
        binding.hhView.listener = object : PatternLockView.OnMoveUpListener {
            override fun onMoveUp(success: Boolean) {
                showToast(if (success) "验证成功！" else "验证失败！")
            }
        }
    }

}