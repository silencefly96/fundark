package com.silencefly96.module_mvvm.plan

import android.content.Context
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.silencefly96.module_base.base.BaseActivity
import com.silencefly96.module_mvvm.databinding.ActivityPlanBinding
import com.silencefly96.module_mvvm.plan.model.Injection
import com.silencefly96.module_common.ext.replaceFragment

class PlanActivity : BaseActivity() {

    private lateinit var binding: ActivityPlanBinding

    private lateinit var planViewModel: PlanViewModel

    override fun bindView(): View {
        binding = ActivityPlanBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun doBusiness(context: Context) {
        //viewmodel和activity生命周期不一致，通过ViewModelProvider提供
        planViewModel = ViewModelProvider(this).get(PlanViewModel::class.java)
        //通过注入提供Repository，便于测试
        planViewModel.planRepository = Injection.providePlanRepository(this)

        //fragment才是真正的view层
        replaceFragment(PlanListFragment.newInstance(planViewModel), binding.detailFrame.id)
        //PlanDetailFragment通过侧边栏提供
        replaceFragment(PlanDetailFragment.newInstance(planViewModel), binding.listFrame.id)
        //TestFragment可以控制所有数据
        replaceFragment(PlanTestFragment.newInstance(planViewModel), binding.testFrame.id)
    }
}