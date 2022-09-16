package com.silencefly96.module_demo.plan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.silencefly96.module_demo.plan.model.Plan
import com.silencefly96.module_demo.plan.model.PlanRepository

class PlanViewModel: ViewModel() {

    val type: Int = 1

    lateinit var planRepository: PlanRepository

    private val _output = MutableLiveData<String>()
    val output: LiveData<String>
        get() = _output

    fun getAll() = planRepository.queryAll(type)

    fun add(id: Long, title: String, content: String) =
        planRepository.add(type, Plan(id = id, title = title, content = content))

    fun delete(id: Long) = planRepository.delete(type, id)

    fun query(id: Long) = planRepository.query(type, id)

    fun update(id: Long, title: String, content: String) =
        planRepository.update(type, Plan(id = id, title = title, content = content))

}