package com.silencefly96.module_plan.plan.model.local

import androidx.annotation.VisibleForTesting
import com.silencefly96.module_plan.plan.model.Plan
import com.silencefly96.module_plan.plan.model.PlanDataSource

class PlanLocalDataSource(
    private val planDao: PlanDao
): PlanDataSource {

    override suspend fun queryAll(): List<Plan> {
        return planDao.queryAll()
    }

    override suspend fun query(id: Long): Plan? {
       return planDao.query(id)
    }

    override suspend fun add(plan: Plan): Int {
        planDao.insert(plan)
        return 0
    }

    override suspend fun delete(id: Long): Int {
       planDao.delete(id)
       return 0
    }

    override suspend fun update(plan: Plan): Int {
        planDao.update(plan)
        return 0
    }

    companion object {
        
        private var INSTANCE: PlanLocalDataSource? = null

        @JvmStatic
        fun getInstance(planDao: PlanDao): PlanLocalDataSource {
            if (INSTANCE == null) {
                synchronized(PlanLocalDataSource::javaClass) {
                    INSTANCE = PlanLocalDataSource(planDao)
                }
            }
            return INSTANCE!!
        }

        @VisibleForTesting
        fun clearInstance() {
            INSTANCE = null
        }
    }
}