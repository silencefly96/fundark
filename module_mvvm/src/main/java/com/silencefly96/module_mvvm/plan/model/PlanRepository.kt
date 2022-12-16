package com.silencefly96.module_mvvm.plan.model

import kotlinx.coroutines.Dispatchers

class PlanRepository(
    var mPlansRemoteDataSource: PlanDataSource,
    var mPlansLocalDataSource: PlanDataSource
): BaseRepository {

    private fun getDataSource(type: Int): PlanDataSource {
        return when(type){
            TYPE_REMOTE -> mPlansRemoteDataSource
            TYPE_LOCAL -> mPlansLocalDataSource
            else -> mPlansLocalDataSource
        }
    }

    fun queryAll(type: Int) = runAsLiveData(Dispatchers.IO) {
        val data = getDataSource(type).queryAll()
        Result.success(data)
    }

    fun queryAllLocal() = queryAll(TYPE_LOCAL)

    fun queryAllRemote() = queryAll(TYPE_REMOTE)

    fun query(type: Int, id: Long)= runAsLiveData(Dispatchers.IO) {
        val data = getDataSource(type).query(id)
        if (data != null) {
            Result.success(data)
        }else {
            Result.failure(IllegalArgumentException("未查询到数据"))
        }
    }

    fun queryLocal(id: Long) = query(TYPE_LOCAL, id)

    fun queryRemote(id: Long) = query(TYPE_REMOTE, id)

    fun add(type: Int, plan: Plan)= runAsLiveData(Dispatchers.IO) {
        val index = getDataSource(type).add(plan)
        Result.success(index)
    }

    fun addLocal(plan: Plan) = add(TYPE_LOCAL, plan)

    fun addRemote(plan: Plan) = add(TYPE_REMOTE, plan)

    fun delete(type: Int, id: Long)= runAsLiveData(Dispatchers.IO) {
        val index = getDataSource(type).delete(id)
        Result.success(index)
    }

    fun deleteLocal(id: Long) = delete(TYPE_LOCAL, id)

    fun deleteRemote(id: Long) = delete(TYPE_REMOTE, id)

    fun update(type: Int, plan: Plan)= runAsLiveData(Dispatchers.IO) {
        val index = getDataSource(type).update(plan)
        Result.success(index)
    }

    fun updateLocal(plan: Plan) = update(TYPE_LOCAL, plan)

    fun updateRemote(plan: Plan) = update(TYPE_REMOTE, plan)

    companion object {

        //仓库类型
        const val TYPE_LOCAL: Int = 0
        const val TYPE_REMOTE: Int = 1

        private var INSTANCE: PlanRepository? = null

        @JvmStatic
        fun getInstance(
            planRemoteDataSource: PlanDataSource,
            planLocalDataSource: PlanDataSource
        ): PlanRepository {
            return INSTANCE ?: PlanRepository(planRemoteDataSource, planLocalDataSource)
                    .apply { INSTANCE = this }
        }

        @JvmStatic
        fun destroyInstance() {
            INSTANCE = null
        }
    }
}