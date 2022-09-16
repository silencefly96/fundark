package com.silencefly96.module_demo.plan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.silencefly96.module_demo.databinding.ActivityPlanBinding
import com.silencefly96.module_demo.plan.model.Injection
import com.silencefly96.module_common.ext.replaceFragment

class PlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlanBinding

    lateinit var planViewModel: PlanViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        planViewModel = ViewModelProvider(this).get(PlanViewModel::class.java)

        planViewModel.planRepository = Injection.providePlanRepository(this)


        replaceFragment(PlanListFragment.newInstance(planViewModel), binding.listFrame.id)

        replaceFragment(PlanDetailFragment.newInstance(planViewModel), binding.detailFrame.id)

    }
}