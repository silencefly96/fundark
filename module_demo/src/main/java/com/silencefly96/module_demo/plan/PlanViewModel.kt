package com.silencefly96.module_demo.plan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.silencefly96.module_demo.plan.model.Plan
import com.silencefly96.module_demo.plan.model.PlanRepository
import com.silencefly96.module_demo.plan.model.PlanRepository.Companion.TYPE_REMOTE

class PlanViewModel: ViewModel() {

    //仓库类型
    private val repoType: Int = TYPE_REMOTE
    //总仓库
    lateinit var planRepository: PlanRepository

    //全部数据
    val planList = ArrayList<Plan>()
    //当前查看的id
    var idSelect = "0"
    var id = MutableLiveData<String>()
    //当前选中plan
    var title = ""
    var content = ""
    val plan = id.switchMap { id ->
        query(id.toLong())
    }

    //输出日志
    private val _output = MutableLiveData<String>()
    val output: LiveData<String>
        get() = _output

    fun getAll() = planRepository.queryAll(repoType)

    fun add(id: Long, title: String, content: String) =
        planRepository.add(repoType, Plan(id = id, title = title, content = content))

    fun delete(id: Long) = planRepository.delete(repoType, id)

    fun query(id: Long) = planRepository.query(repoType, id)

    fun update(id: Long, title: String, content: String) =
        planRepository.update(repoType, Plan(id = id, title = title, content = content))

}