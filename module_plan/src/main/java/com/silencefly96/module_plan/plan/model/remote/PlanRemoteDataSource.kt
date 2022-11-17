package com.silencefly96.module_plan.plan.model.remote

import androidx.annotation.VisibleForTesting
import com.silencefly96.module_common.net.ServiceCreator
import com.silencefly96.module_plan.plan.model.Plan
import com.silencefly96.module_plan.plan.model.PlanDataSource

class PlanRemoteDataSource(
        private val planService: PlanService
): PlanDataSource {

    override suspend fun queryAll(): List<Plan> {
        return planService.queryAll()
    }

    override suspend fun query(id: Long): Plan? {
        return planService.query(id)
    }

    override suspend fun add(plan: Plan): Int {
        return planService.add(plan).result
    }

    override suspend fun delete(id: Long): Int {
        return planService.delete(id).result
    }

    override suspend fun update(plan: Plan): Int {
        return planService.update(plan.id, plan).result
    }

    companion object {

        private var INSTANCE: PlanRemoteDataSource? = null

        @JvmStatic
        fun getInstance(): PlanRemoteDataSource {
            if (INSTANCE == null) {
                synchronized(PlanRemoteDataSource::javaClass) {
                    INSTANCE = PlanRemoteDataSource(ServiceCreator.create(PlanService::class.java))
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