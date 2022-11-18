package com.silencefly96.module_plan

import android.content.Context
import android.view.View
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_plan.databinding.ActivityMainBinding
import com.silencefly96.module_plan.plan.PlanActivity

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun doBusiness(context: Context) {
        binding.hhView.postDelayed({
            startActivity(PlanActivity::class.java)
        }, 1000)
    }

}